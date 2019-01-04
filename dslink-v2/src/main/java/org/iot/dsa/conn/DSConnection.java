package org.iot.dsa.conn;

import org.iot.dsa.node.DSInfo;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.node.DSNode;
import org.iot.dsa.node.DSStatus;
import org.iot.dsa.node.DSString;
import org.iot.dsa.node.event.DSIEvent;
import org.iot.dsa.node.event.DSITopic;
import org.iot.dsa.node.event.DSTopic;
import org.iot.dsa.time.DSDateTime;
import org.iot.dsa.util.DSException;

/**
 * Represents connection with a lifecycle (connecting, connected, disconnecting, disconnected).
 * These connections do not have to have a long lived pipes such as a sockets, they could simply
 * represent http sessions, or other abstract constructs.
 * <p>
 * Subclasses must:<br>
 * <ul>
 * <li> Implement doConnect, doDisconnect and doPing.
 * <li> Maybe add a property to make the ping interval configurable.
 * <li> Call connOk() after a successful communication.
 * <li> Call connDown(String) after a connection failure.  This does not need to be called for
 * higher level errors such as malformed sql statements being submitted over a connection.  In
 * those kinds of scenarios, just call connOk and let the ping loop determine if the connection
 * has been lost.
 * <li> Override checkConfig() and throw an exception if misconfigured.
 * <li> Have a thread call the run() method.
 * </ul>
 *
 * @author Aaron Hansen
 */
public abstract class DSConnection extends DSBaseConnection implements Runnable {

    ///////////////////////////////////////////////////////////////////////////
    // Class Fields
    ///////////////////////////////////////////////////////////////////////////

    protected static final String STATE = "State";
    protected static final String STATE_TIME = "State Time";

    /**
     * The topic to subscribe to for connected events.
     */
    public static final DSConnectionTopic CONNECTED = new DSConnectionTopic("CONNECTED");

    /**
     * The topic to subscribe to for disconnected events.
     */
    public static final DSConnectionTopic DISCONNECTED = new DSConnectionTopic("DISCONNECTED");

    ///////////////////////////////////////////////////////////////////////////
    // Instance Fields
    ///////////////////////////////////////////////////////////////////////////

    protected DSInfo state = getInfo(STATE);
    protected DSInfo stateTime = getInfo(STATE_TIME);

    ///////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////

    /**
     * If the connection state is connected, this will call disconnect.  Otherwise, if not
     * already disconnected this transitions the connection to disconnected.
     *
     * @param reason Optional
     */
    @Override
    public void connDown(String reason) {
        debug(debug() ? "connDown: " + reason : null);
        if (getConnectionState().isConnected()) {
            put(lastFail, DSDateTime.currentTime());
            if (reason != null) {
                if (!getStatusText().equals(reason)) {
                    put(statusText, DSString.valueOf(reason));
                }
            }
            disconnect();
        } else if (!getConnectionState().isDisconnected()) {
            put(state, DSConnectionState.DISCONNECTED);
            put(stateTime, DSDateTime.currentTime());
            notifyConnectedDescendants(this, this);
            super.connDown(reason);
            try {
                onDisconnected();
            } catch (Exception x) {
                error(error() ? getPath() : null, x);
            }
            fire(DISCONNECTED, null);
        }
    }


    /**
     * Update the last ok timestamp, will remove the down status if present and notifies the
     * subtree if the connection actually transitions to connected.
     */
    @Override
    public void connOk() {
        if (getConnectionState().isDisengaged()) {
            debug(debug() ? "Not connecting or connected, ignoring connOk()" : null);
            return;
        }
        long now = System.currentTimeMillis();
        if ((now - lastOkMillis) > 1000) { //prevents subscriptions to last ok from going crazy
            put(lastOk, DSDateTime.valueOf(now));
        }
        lastOkMillis = now;
        if (!isConnected()) {
            put(state, DSConnectionState.CONNECTED);
            put(stateTime, DSDateTime.valueOf(now));
            notifyConnectedDescendants(this, this);
            super.connOk();
            try {
                onConnected();
            } catch (Exception x) {
                error(error() ? getPath() : null, x);
            }
            fire(CONNECTED, null);
        }
    }

    /**
     * Verifies configuration and if operational, initiates the connection logic.
     */
    public void connect() {
        if (!getConnectionState().isDisconnected()) {
            debug(debug() ? "Not disconnected, ignoring connect()" : null);
            return;
        }
        debug(debug() ? "Connect" : null);
        put(state, DSConnectionState.CONNECTING);
        put(stateTime, DSDateTime.currentTime());
        notifyConnectedDescendants(this, this);
        if (canConnect()) {
            try {
                doConnect();
            } catch (Throwable e) {
                error(error() ? getPath() : null, e);
                connDown(DSException.makeMessage(e));
            }
        } else {
            put(state, DSConnectionState.DISCONNECTED);
            put(stateTime, DSDateTime.currentTime());
            notifyConnectedDescendants(this, this);
        }
    }

    /**
     * Transitions the connection to disconnecting then disconnected and makes all the
     * respective callbacks.
     */
    public void disconnect() {
        debug(debug() ? "Disconnect" : null);
        put(state, DSConnectionState.DISCONNECTING);
        put(stateTime, DSDateTime.currentTime());
        notifyConnectedDescendants(this, this);
        try {
            doDisconnect();
        } catch (Throwable x) {
            error(getPath(), x);
        }
        put(state, DSConnectionState.DISCONNECTED);
        put(stateTime, DSDateTime.currentTime());
        notifyConnectedDescendants(this, this);
        myStatus = myStatus.add(DSStatus.DOWN);
        updateStatus(myStatus, null);
        try {
            onDisconnected();
        } catch (Exception x) {
            error(error() ? getPath() : null, x);
        }
        fire(DISCONNECTED, null);
    }

    public DSConnectionState getConnectionState() {
        return (DSConnectionState) state.get();
    }

    public boolean isConnected() {
        return getConnectionState().isConnected();
    }

    /**
     * Call this to automatically manage the connection lifecycle, it will not return
     * until the node is stopped.
     */
    public void run() {
        long INTERVAL = 2000;
        long retryMs = 0;
        long lastPing = 0;
        while (isRunning()) {
            if (isConnected()) {
                retryMs = INTERVAL;
                if (!isEnabled()) {
                    disconnect();
                } else {
                    try {
                        long ivl = getPingInterval();
                        long last = Math.max(getLastOk(), lastPing);
                        long now = System.currentTimeMillis();
                        long duration = now - last;
                        if (duration >= ivl) {
                            lastPing = now;
                            try {
                                doPing();
                            } catch (Throwable t) {
                                error(getPath(), t);
                                connDown(DSException.makeMessage(t));
                            }
                        }
                    } catch (Exception x) {
                        debug(debug() ? getPath() : null, x);
                    }
                }
            } else {
                if (isEnabled() && getConnectionState().isDisconnected()) {
                    if (getTimeInState() >= retryMs) {
                        retryMs = Math.min(60000, retryMs + INTERVAL);
                        connect();
                    }
                }
            }
            synchronized (this) {
                try {
                    wait(INTERVAL);
                } catch (Exception x) {
                    debug(debug() ? getPath() : null, x);
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Protected Methods
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void declareDefaults() {
        super.declareDefaults();
        declareDefault(STATE, DSConnectionState.DISCONNECTED).setReadOnly(true).setTransient(true);
        declareDefault(STATE_TIME, DSDateTime.currentTime()).setReadOnly(true).setTransient(true);
    }

    /**
     * Subclasses must establish the connection. You must call connOk or connDown, but that can
     * be async after this method has returned. You can throw an exception from this method instead
     * of calling connDown. This will only be called if configuration is ok.
     */
    protected abstract void doConnect();

    /**
     * Close and clean up resources, when this returns, the connection will be put into
     * the disconnected state, onDisconnected will be called, and descendants will be notified.
     */
    protected abstract void doDisconnect();

    /**
     * Override point, called by the run method.  Implementations should verify the connection
     * is still valid and call connOk or connDown, but those can be async and after this method
     * returns.  Throwing an exception will be treated as a connDown.  By default, this does
     * nothing.
     */
    protected void doPing() {
    }

    /**
     * Ping interval in milliseconds (default is 60000).  If the time since the last call
     * to connOk exceeds this, the ping method will be called.  Implementations should
     * call connOk whenever there have been successful communications to minimize pinging.
     *
     * @return 60000
     */
    protected long getPingInterval() {
        return 60000;
    }

    protected long getTimeInState() {
        DSDateTime time = (DSDateTime) stateTime.get();
        return System.currentTimeMillis() - time.timeInMillis();
    }

    /**
     * Called by connOk() when transitioning to connected.  By default, this does nothing.
     */
    protected void onConnected() {
    }

    /**
     * Called by connDown() when transitioning to disconnected.  By default, this does nothing.
     */
    protected void onDisconnected() {
    }

    ///////////////////////////////////////////////////////////////////////////
    // Package / Private Methods
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Calls DSIConnected.onChange on implementations in the subtree.  Stops at instances of
     * DSIConnected and DSConnection.
     */
    private void notifyConnectedDescendants(DSNode node, DSConnection conn) {
        DSInfo info = node.getFirstInfo();
        while (info != null) {
            if (info.is(DSIConnected.class)) {
                try {
                    ((DSIConnected) info.get()).onConnectionChange(conn);
                } catch (Throwable t) {
                    error(error() ? info.getPath(null) : null, t);
                }
            } else if (info.isNode() && !info.is(DSConnection.class)) {
                notifyConnectedDescendants(info.getNode(), conn);
            }
            info = info.next();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Inner Classes
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Used to notify subscribers of connected and disconnected events.
     */
    public static class DSConnectionTopic extends DSTopic implements DSIEvent {

        String name;

        // Prevent instantiation
        private DSConnectionTopic(String name) {
            this.name = name;
        }

        /**
         * Returns null.
         */
        @Override
        public DSMap getData() {
            return null;
        }

        @Override
        public DSITopic getTopic() {
            return this;
        }

        @Override
        public String toString() {
            return name;
        }

    }

}
