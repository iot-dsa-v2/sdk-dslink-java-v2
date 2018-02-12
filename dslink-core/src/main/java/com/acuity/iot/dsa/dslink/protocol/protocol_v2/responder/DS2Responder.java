package com.acuity.iot.dsa.dslink.protocol.protocol_v2.responder;

import com.acuity.iot.dsa.dslink.io.DSByteBuffer;
import com.acuity.iot.dsa.dslink.protocol.DSStream;
import com.acuity.iot.dsa.dslink.protocol.protocol_v2.CloseMessage;
import com.acuity.iot.dsa.dslink.protocol.protocol_v2.DS2MessageReader;
import com.acuity.iot.dsa.dslink.protocol.protocol_v2.DS2Session;
import com.acuity.iot.dsa.dslink.protocol.protocol_v2.MessageConstants;
import com.acuity.iot.dsa.dslink.protocol.responder.DSInboundRequest;
import com.acuity.iot.dsa.dslink.protocol.responder.DSInboundSet;
import com.acuity.iot.dsa.dslink.protocol.responder.DSResponder;
import com.acuity.iot.dsa.dslink.transport.DSBinaryTransport;
import java.util.Map;
import org.iot.dsa.DSRuntime;
import org.iot.dsa.node.DSBytes;
import org.iot.dsa.node.DSElement;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.security.DSPermission;

/**
 * Implementation for DSA v2.
 *
 * @author Aaron Hansen
 */
public class DS2Responder extends DSResponder implements MessageConstants {

    ///////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////

    private DS2InboundSubscriptions subscriptions = new DS2InboundSubscriptions(this);

    /////////////////////////////////////////////////////////////////
    // Methods - Constructors
    /////////////////////////////////////////////////////////////////

    public DS2Responder(DS2Session session) {
        super(session);
    }

    /////////////////////////////////////////////////////////////////
    // Methods - In alphabetical order by method name.
    /////////////////////////////////////////////////////////////////

    public DSBinaryTransport getTransport() {
        return (DSBinaryTransport) getConnection().getTransport();
    }

    /**
     * Process an individual request.
     */
    public void handleRequest(DS2MessageReader reader) {
        switch (reader.getMethod()) {
            case MSG_CLOSE:
                processClose(reader);
                break;
            case MSG_INVOKE_REQ:
                processInvoke(reader);
                break;
            case MSG_LIST_REQ:
                processList(reader);
                break;
            case MSG_OBSERVE_REQ:
                break;
            case MSG_SET_REQ:
                processSet(reader);
                break;
            case MSG_SUBSCRIBE_REQ:
                processSubscribe(reader);
                break;
            default:
                throw new IllegalArgumentException("Unexpected method: " + reader.getMethod());
        }
    }

    public void onConnect() {
    }

    public void onConnectFail() {
    }

    public void onDisconnect() {
        subscriptions.close();
        for (Map.Entry<Integer, DSStream> entry : getRequests().entrySet()) {
            try {
                entry.getValue().onClose(entry.getKey());
            } catch (Exception x) {
                severe(getPath(), x);
            }
        }
        getRequests().clear();
    }

    /**
     * Handles an invoke request.
     */
    private void processClose(DS2MessageReader msg) {
        int rid = msg.getRequestId();
        DSStream stream = getRequests().get(rid);
        if (stream != null) {
            stream.onClose(rid);
        } else {
            subscriptions.unsubscribe(rid);
        }
    }

    /**
     * Handles an invoke request.
     */
    private void processInvoke(DS2MessageReader msg) {
        int rid = msg.getRequestId();
        DSMap params = msg.getBodyReader().getMap();
        DSPermission perm = DSPermission.READ;
        Object obj = msg.getHeader(MessageConstants.HDR_MAX_PERMISSION);
        if (obj != null) {
            perm = DSPermission.valueOf(obj.hashCode());
        }
        DS2InboundInvoke invokeImpl = new DS2InboundInvoke(params, perm);
        invokeImpl.setPath((String) msg.getHeader(HDR_TARGET_PATH))
                  .setSession(getSession())
                  .setRequestId(rid)
                  .setResponder(this);
        putRequest(rid, invokeImpl);
        DSRuntime.run(invokeImpl);
    }

    /**
     * Handles a list request.
     */
    private void processList(DS2MessageReader msg) {
        int rid = msg.getRequestId();
        String path = (String) msg.getHeader(HDR_TARGET_PATH);
        DS2InboundList listImpl = new DS2InboundList();
        listImpl.setPath(path)
                .setSession(getSession())
                .setRequestId(rid)
                .setResponder(this);
        putRequest(listImpl.getRequestId(), listImpl);
        DSRuntime.run(listImpl);
    }

    /**
     * Handles a set request.
     */
    private void processSet(DS2MessageReader msg) {
        int rid = msg.getRequestId();
        DSPermission perm = DSPermission.READ;
        Object obj = msg.getHeader(MessageConstants.HDR_MAX_PERMISSION);
        if (obj != null) {
            perm = DSPermission.valueOf(obj.hashCode());
        }
        int metaLen = DSBytes.readShort(msg.getBody(), false);
        if (metaLen > 0) {
            //what to do with it?
            msg.getBodyReader().getElement();
        }
        DSElement value = msg.getBodyReader().getElement();
        DSInboundSet setImpl = new DSInboundSet(value, perm);
        setImpl.setPath((String) msg.getHeader(HDR_TARGET_PATH))
               .setSession(getSession())
               .setRequestId(rid)
               .setResponder(this);
        DSRuntime.run(setImpl);
    }

    /**
     * Handles a subscribe request.
     */
    private void processSubscribe(DS2MessageReader msg) {
        Integer sid = msg.getRequestId();
        String path = (String) msg.getHeader(HDR_TARGET_PATH);
        Integer qos = (Integer) msg.getHeader(MessageConstants.HDR_QOS);
        if (qos == null) {
            qos = Integer.valueOf(0);
        }
        //Integer queueSize = (Integer) msg.getHeader(MessageConstants.HDR_QUEUE_SIZE);
        subscriptions.subscribe(sid, path, qos);
    }

    @Override
    public void sendClose(int rid) {
        sendResponse(new CloseMessage(this, rid));
    }

    @Override
    public void sendError(DSInboundRequest req, Throwable reason) {
        sendResponse(new ErrorMessage(req, reason));
    }


}
