package com.acuity.iot.dsa.dslink.protocol.protocol_v1.responder;

import com.acuity.iot.dsa.dslink.protocol.message.ErrorResponse;
import com.acuity.iot.dsa.dslink.protocol.message.OutboundMessage;
import com.acuity.iot.dsa.dslink.protocol.protocol_v1.DS1Stream;
import java.util.Iterator;
import org.iot.dsa.DSRuntime;
import org.iot.dsa.dslink.responder.InboundInvokeRequest;
import org.iot.dsa.io.DSIWriter;
import org.iot.dsa.node.DSIValue;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.node.action.ActionResult;
import org.iot.dsa.node.action.ActionSpec;
import org.iot.dsa.node.action.ActionTable;
import org.iot.dsa.node.action.ActionValues;
import org.iot.dsa.security.DSPermission;

/**
 * Invoke implementation for a responder.
 *
 * @author Aaron Hansen
 */
class DS1InboundInvoke extends DS1InboundRequest
        implements DS1Stream, InboundInvokeRequest, OutboundMessage, Runnable {

    ///////////////////////////////////////////////////////////////////////////
    // Constants
    ///////////////////////////////////////////////////////////////////////////

    private static final int STATE_INIT = 0;
    private static final int STATE_ROWS = 1;
    private static final int STATE_UPDATES = 2;
    private static final int STATE_CLOSE_PENDING = 3;
    private static final int STATE_CLOSED = 4;

    ///////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////

    private Exception closeReason;
    private boolean enqueued = false;
    private DSMap parameters;
    private DSPermission permission;
    private ActionResult result;
    private Iterator<DSList> rows;
    private int state = STATE_INIT;
    private Update updateHead;
    private Update updateTail;

    ///////////////////////////////////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////////////////////////////////

    DS1InboundInvoke(DSMap request) {
        setRequest(request);
        String permit = request.get("permit", "config");
        permission = DSPermission.forString(permit);
        parameters = request.getMap("params");
    }

    ///////////////////////////////////////////////////////////////////////////
    // Methods in alphabetical order
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void clearAllRows() {
        enqueueUpdate(new Update(null, UpdateType.REFRESH));
    }

    @Override
    public void close() {
        if (!isOpen()) {
            return;
        }
        state = STATE_CLOSE_PENDING;
        enqueueResponse();
        fine(fine() ? getPath() + " list closed locally" : null);
    }

    @Override
    public void close(Exception reason) {
        if (!isOpen()) {
            return;
        }
        closeReason = reason;
        state = STATE_CLOSE_PENDING;
        enqueueResponse();
        fine(fine() ? getPath() + " list closed locally" : null);
    }

    private synchronized Update dequeueUpdate() {
        if (updateHead == null) {
            return null;
        }
        Update ret = updateHead;
        if (updateHead == updateTail) {
            updateHead = null;
            updateTail = null;
        } else {
            updateHead = updateHead.next;
        }
        ret.next = null;
        return ret;
    }

    /**
     * Handles the transition to close.
     */
    private void doClose() {
        state = STATE_CLOSED;
        getResponder().removeInboundRequest(getRequestId());
        if (result == null) {
            return;
        }
        DSRuntime.run(new Runnable() {
            @Override
            public void run() {
                try {
                    result.onClose();
                } catch (Exception x) {
                    severe(getPath(), x);
                }
            }
        });
    }

    private void enqueueUpdate(Update update) {
        if (!isOpen()) {
            return;
        }
        synchronized (this) {
            if (updateHead == null) {
                updateHead = update;
                updateTail = update;
            } else {
                updateTail.next = update;
                updateTail = update;
            }
            if (enqueued) {
                return;
            }
        }
        getResponder().sendResponse(this);
    }

    /**
     * Enqueues in the session.
     */
    private void enqueueResponse() {
        synchronized (this) {
            if (enqueued) {
                return;
            }
            enqueued = true;
        }
        getResponder().sendResponse(this);
    }

    /**
     * Invokes the action and will then enqueueUpdate the outgoing response.
     */
    public void run() {
        try {
            result = getResponderImpl().onInvoke(this);
            if (result == null) {
                close();
            }
        } catch (Exception x) {
            severe(getPath(), x);
            close(x);
        }
        enqueueResponse();
    }

    /**
     * Any parameters supplied by the requester for the invocation, or null.
     */
    @Override
    public DSMap getParameters() {
        return parameters;
    }

    @Override
    public DSPermission getPermission() {
        return permission;
    }

    @Override
    public void insert(int index, DSList[] rows) {
        enqueueUpdate(new Update(rows, index, -1, UpdateType.INSERT));
    }

    public boolean isClosed() {
        return state == STATE_CLOSED;
    }

    public boolean isClosePending() {
        return state == STATE_CLOSE_PENDING;
    }

    @Override
    public boolean isOpen() {
        return (state != STATE_CLOSED) && (state != STATE_CLOSE_PENDING);
    }

    @Override
    public void onClose(Integer requestId) {
        if (isClosed()) {
            return;
        }
        state = STATE_CLOSED;
        fine(finer() ? getPath() + " invoke closed" : null);
        synchronized (this) {
            updateHead = updateTail = null;
        }
        doClose();
    }


    @Override
    public void replace(int index, int len, DSList... rows) {
        if (len < 1) {
            throw new IllegalArgumentException("Invalid length: " + len);
        }
        enqueueUpdate(new Update(rows, index, len, UpdateType.REPLACE));
    }

    @Override
    public void send(DSList row) {
        enqueueUpdate(new Update(row));
    }

    @Override
    public void write(DSIWriter out) {
        enqueued = false;
        if (isClosed()) {
            return;
        }
        if (isClosePending() && (updateHead == null) && (closeReason != null)) {
            ErrorResponse res = new ErrorResponse(closeReason);
            res.parseRequest(getRequest());
            res.write(out);
            doClose();
            return;
        }
        out.beginMap();
        out.key("rid").value(getRequestId());
        switch (state) {
            case STATE_INIT:
                writeColumns(out);
                writeInitialResults(out);
                break;
            case STATE_ROWS:
                writeInitialResults(out);
                break;
            case STATE_CLOSE_PENDING:
            case STATE_UPDATES:
                writeUpdates(out);
                break;
            default:
                ;
        }
        if (isClosePending() && (updateHead == null)) {
            if (closeReason != null) {
                ErrorResponse res = new ErrorResponse(closeReason);
                res.parseRequest(getRequest());
                getResponder().sendResponse(res);
            } else {
                out.key("stream").value("closed");
            }
            doClose();
        }
        out.endMap();
    }

    private void writeColumns(DSIWriter out) {
        if (result instanceof ActionValues) {
            out.key("columns").beginList();
            Iterator<DSMap> it = result.getAction().getValueResults();
            if (it != null) {
                while (it.hasNext()) {
                    out.value(it.next());
                }
            }
            out.endList();
        } else if (result instanceof ActionTable) {
            ActionSpec action = result.getAction();
            out.key("meta")
               .beginMap()
               .key("mode").value(action.getResultType().isStream() ? "stream" : "append")
               .key("meta").beginMap().endMap()
               .endMap();
            out.key("columns").beginList();
            Iterator<DSMap> iterator = ((ActionTable) result).getColumns();
            if (iterator != null) {
                while (iterator.hasNext()) {
                    out.value(iterator.next());
                }
            }
            out.endList();
        } else {
            out.key("columns").beginList().endList();
        }
    }

    private void writeInitialResults(DSIWriter out) {
        state = STATE_ROWS;
        out.key("updates").beginList();
        if (result instanceof ActionValues) {
            out.beginList();
            Iterator<DSIValue> values = ((ActionValues) result).getValues();
            if (values != null) {
                while (values.hasNext()) {
                    DSIValue val = values.next();
                    out.value(val.toElement());
                }
            }
            out.endList();
        } else if (result instanceof ActionTable) {
            if (rows == null) {
                rows = ((ActionTable) result).getRows();
            }
            DS1Responder session = getResponder();
            while (rows.hasNext()) {
                out.value(rows.next());
                if (session.shouldEndMessage()) {
                    out.endList();
                    enqueueResponse();
                    return;
                }
            }
        }
        out.endList();
        if ((result == null) || !result.getAction().getResultType().isOpen()) {
            out.key("stream").value("closed");
            state = STATE_CLOSED;
            doClose();
        } else {
            out.key("stream").value("open");
            state = STATE_UPDATES;
        }
    }

    private void writeUpdates(DSIWriter out) {
        Update update = updateHead; //peak ahead
        if (update == null) {
            return;
        }
        if (update.type != null) {
            out.key("meta")
               .beginMap()
               .key(update.typeKey()).value(update.typeValue())
               .key("meta").beginMap().endMap()
               .endMap();
        }
        out.key("updates").beginList();
        DS1Responder session = getResponder();
        while (true) {
            update = dequeueUpdate();
            if (update.rows != null) {
                for (DSList row : update.rows) {
                    out.value(row);
                }
            } else if (update.row != null) {
                out.value(update.row);
            }
            if ((updateHead == null) || (updateHead.type != null)) {
                break;
            }
            if (session.shouldEndMessage()) {
                enqueueResponse();
                break;
            }
        }
        out.endList();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Inner Classes
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Used to described more complex updates.
     */
    private enum UpdateType {
        INSERT("insert"),
        REFRESH("refresh"),
        REPLACE("replace");

        private String display;

        UpdateType(String display) {
            this.display = display;
        }

        public String toString() {
            return display;
        }
    }

    /**
     * Describes an update to be sent to the requester.
     */
    private static class Update {

        int beginIndex = -1;
        int endIndex = -1;
        UpdateType type;
        Update next;
        DSList row;
        DSList[] rows;

        Update(DSList row) {
            this.row = row;
        }

        Update(DSList row, UpdateType type) {
            this.row = row;
            this.type = type;
        }

        Update(DSList[] rows, int index, int len, UpdateType type) {
            this.rows = rows;
            this.beginIndex = index;
            if (len > 0) {
                this.endIndex = index + len - 1; //inclusive end
            }
            this.type = type;
        }

        String typeKey() {
            if ((type == UpdateType.INSERT) || (type == UpdateType.REPLACE)) {
                return "modify";
            } else {
                return "mode";
            }
        }

        String typeValue() {
            if (type == UpdateType.REFRESH) {
                return type.toString();
            }
            if (type == UpdateType.INSERT) {
                return type.toString() + " " + beginIndex;
            }
            return type.toString() + " " + beginIndex + "-" + endIndex;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Initialization
    ///////////////////////////////////////////////////////////////////////////

}
