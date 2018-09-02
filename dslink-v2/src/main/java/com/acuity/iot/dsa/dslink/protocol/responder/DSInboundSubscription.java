package com.acuity.iot.dsa.dslink.protocol.responder;

import com.acuity.iot.dsa.dslink.protocol.DSSession;
import com.acuity.iot.dsa.dslink.protocol.message.MessageWriter;
import com.acuity.iot.dsa.dslink.protocol.message.RequestPath;
import org.iot.dsa.dslink.DSIResponder;
import org.iot.dsa.dslink.responder.InboundSubscribeRequest;
import org.iot.dsa.dslink.responder.SubscriptionCloseHandler;
import org.iot.dsa.io.DSIWriter;
import org.iot.dsa.node.DSIObject;
import org.iot.dsa.node.DSIStatus;
import org.iot.dsa.node.DSIValue;
import org.iot.dsa.node.DSInfo;
import org.iot.dsa.node.DSNode;
import org.iot.dsa.node.DSStatus;
import org.iot.dsa.node.event.DSIEvent;
import org.iot.dsa.node.event.DSISubscriber;
import org.iot.dsa.node.event.DSInfoTopic;
import org.iot.dsa.node.event.DSTopic;
import org.iot.dsa.node.event.DSValueTopic;
import org.iot.dsa.time.DSTime;

/**
 * Subscribe implementation for the responder.
 *
 * @author Aaron Hansen
 */
public class DSInboundSubscription extends DSInboundRequest
        implements DSISubscriber, InboundSubscribeRequest {

    ///////////////////////////////////////////////////////////////////////////
    // Instance Fields
    ///////////////////////////////////////////////////////////////////////////

    private int ackRequired = 0;
    private DSInfo child;
    private boolean closeAfterUpdate = false;
    private SubscriptionCloseHandler closeHandler;
    private boolean enqueued = false;
    private DSInboundSubscriptions manager;
    private DSNode node;
    private boolean open = true;
    private int qos = 0;
    private Integer sid;
    private Update updateHead;
    private Update updateTail;

    ///////////////////////////////////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////////////////////////////////

    protected DSInboundSubscription(DSInboundSubscriptions manager,
                                    Integer sid,
                                    String path,
                                    int qos) {
        this.manager = manager;
        this.sid = sid;
        setResponder(manager.getResponder());
        setSession(manager.getResponder().getSession());
        setPath(path);
        this.qos = qos;
        setLink(manager.getLink());
        init();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////

    public boolean canWrite(DSSession session) {
        if (ackRequired > 0) {
            int last = session.getAckRcvd();
            if (last >= ackRequired) {
                return true;
            }
            //is the last ack is so far away that we have a rollover.
            return ((ackRequired - 10000000) > last);
        }
        return true;
    }

    @Override
    public void close() {
        manager.unsubscribe(sid);
    }

    /**
     * Unique subscription id for this path.
     */
    @Override
    public Integer getSubscriptionId() {
        return sid;
    }

    public int getQos() {
        return qos;
    }

    /**
     * For v2 only.
     */
    public boolean isCloseAfterUpdate() {
        return closeAfterUpdate;
    }

    @Override
    public void onEvent(DSNode node, DSInfo child, DSIEvent event) {
        DSIValue value;
        if (child != null) {
            value = child.getValue();
        } else {
            value = (DSIValue) node;
        }
        DSStatus status = DSStatus.ok;
        if (value instanceof DSIStatus) {
            status = ((DSIStatus) value).getStatus();
        }
        update(System.currentTimeMillis(), value, status);
    }

    @Override
    public void onUnsubscribed(DSTopic topic, DSNode node, DSInfo info) {
        close();
    }

    /**
     * For v2 only.
     */
    public DSInboundSubscription setCloseAfterUpdate(boolean closeAfterUpdate) {
        this.closeAfterUpdate = closeAfterUpdate;
        return this;
    }

    public void setSubscriptionId(Integer id) {
        sid = id;
    }

    public void setQos(Integer val) {
        synchronized (this) {
            if (val == 0) {
                updateHead = updateTail;
            }
            qos = val;
        }
    }

    @Override
    public String toString() {
        return "Subscription (" + getSubscriptionId() + ") " + getPath();
    }

    /**
     * The responder should call this whenever the value or status changes.
     */
    @Override
    public void update(long timestamp, DSIValue value, DSStatus status) {
        if (!open) {
            return;
        }
        trace(trace() ? "Update " + getPath() + " to " + value : null);
        if (qos == 0) {
            synchronized (this) {
                if (updateHead == null) {
                    updateHead = new Update();
                }
                updateHead.set(timestamp, value, status);
                if (enqueued) {
                    return;
                }
                enqueued = true;
            }
        } else {
            Update update = new Update().set(timestamp, value, status);
            synchronized (this) {
                if (updateHead == null) {
                    updateHead = updateTail = update;
                } else {
                    updateTail.next = update;
                    updateTail = update;
                }
                if (enqueued) {
                    return;
                }
                enqueued = true;
            }
        }
        if (sid != 0) {
            manager.enqueue(this);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Protected Methods
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Remove an update from the queue.
     */
    protected synchronized Update dequeue() {
        if (updateHead == null) {
            return null;
        }
        Update ret = updateHead;
        if (updateHead == updateTail) {
            updateHead = updateTail = null;
        } else {
            updateHead = updateHead.next;
        }
        ret.next = null;
        return ret;
    }

    protected void init() {
        RequestPath path = new RequestPath(getPath(), getLink());
        if (path.isResponder()) {
            DSIResponder responder = (DSIResponder) path.getTarget();
            setPath(path.getPath());
            closeHandler = responder.onSubscribe(this);
        } else {
            DSIObject obj = path.getTarget();
            if (obj instanceof DSNode) {
                node = (DSNode) obj;
                node.subscribe(DSNode.VALUE_TOPIC, null, this);
                onEvent(node, null, DSNode.VALUE_TOPIC);
            } else {
                DSInfo info = path.getInfo();
                node = path.getParent();
                child = info;
                node.subscribe(DSNode.VALUE_TOPIC, info, this);
                onEvent(node, info, DSNode.VALUE_TOPIC);
            }
        }
    }

    protected DSInboundSubscription setQos(int qos) {
        this.qos = qos;
        return this;
    }

    /**
     * Encodes one or more updates.
     *
     * @param writer Where to encode.
     * @param buf    For encoding timestamps.
     */
    protected void write(DSSession session, MessageWriter writer, StringBuilder buf) {
        if (qos > 0) {
            ackRequired = session.getMessageId();
        }
        Update update = dequeue();
        int count = 500;
        while (update != null) {
            write(update, writer, buf);
            if ((qos == 0) || session.shouldEndMessage()) {
                break;
            }
            if (--count >= 0) {
                update = dequeue();
            } else {
                update = null;
            }
        }
        synchronized (this) {
            if (updateHead == null) {
                if (qos == 0) {
                    //reuse instance
                    updateHead = update;
                }
                enqueued = false;
                return;
            }
        }
        manager.enqueue(this);
    }

    /**
     * Encode a single update.  This is implemented for v1 and will need to be overridden for
     * v2.
     *
     * @param update The udpate to write.
     * @param writer Where to write.
     * @param buf    For encoding timestamps.
     */
    protected void write(Update update, MessageWriter writer, StringBuilder buf) {
        DSIWriter out = writer.getWriter();
        out.beginMap();
        out.key("sid").value(getSubscriptionId());
        buf.setLength(0);
        DSTime.encode(update.timestamp, true, buf);
        out.key("ts").value(buf.toString());
        out.key("value").value(update.value.toElement());
        if ((update.status != null) && !update.status.isOk()) {
            out.key("status").value(update.status.toString());
        }
        out.endMap();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Package / Private Methods
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Called no matter how closed.
     */
    void onClose() {
        synchronized (this) {
            if (!open) {
                return;
            }
            open = false;
        }
        try {
            if (closeHandler != null) {
                closeHandler.onClose(getSubscriptionId());
            }
        } catch (Exception x) {
            manager.debug(manager.getPath(), x);
        }
        try {
            if (node != null) {
                node.unsubscribe(DSNode.VALUE_TOPIC, child, this);
            }
        } catch (Exception x) {
            manager.debug(manager.getPath(), x);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Inner Classes
    ///////////////////////////////////////////////////////////////////////////

    protected class Update {

        Update next;
        public DSStatus status;
        public long timestamp;
        public DSIValue value;

        Update set(long timestamp, DSIValue value, DSStatus status) {
            this.timestamp = timestamp;
            this.value = value;
            this.status = status;
            return this;
        }
    }

}
