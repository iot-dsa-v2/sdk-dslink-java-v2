package com.acuity.iot.dsa.dslink.protocol.protocol_v1.responder;

import com.acuity.iot.dsa.dslink.DSProtocolException;
import com.acuity.iot.dsa.dslink.DSResponderSession;
import com.acuity.iot.dsa.dslink.protocol.message.CloseMessage;
import com.acuity.iot.dsa.dslink.protocol.message.ErrorResponse;
import com.acuity.iot.dsa.dslink.protocol.message.OutboundMessage;
import com.acuity.iot.dsa.dslink.protocol.protocol_v1.DS1Session;
import com.acuity.iot.dsa.dslink.protocol.protocol_v1.DS1Stream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.iot.dsa.DSRuntime;
import org.iot.dsa.dslink.DSIResponder;
import org.iot.dsa.dslink.DSLinkConnection;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.node.DSNode;

/**
 * Implements DSA 1.1.2
 *
 * @author Aaron Hansen
 */
public class DS1Responder extends DSNode implements DSResponderSession {

    ///////////////////////////////////////////////////////////////////////////
    // Constants
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////

    private ConcurrentHashMap<Integer, DS1Stream> inboundRequests =
            new ConcurrentHashMap<Integer, DS1Stream>();
    private Logger logger;
    private DS1Session session;
    private DSIResponder responder;
    private DS1InboundSubscriptions subscriptions =
            new DS1InboundSubscriptions(this);

    /////////////////////////////////////////////////////////////////
    // Methods - Constructors
    /////////////////////////////////////////////////////////////////

    public DS1Responder(DS1Session session) {
        this.session = session;
    }

    /////////////////////////////////////////////////////////////////
    // Methods - In alphabetical order by method name.
    /////////////////////////////////////////////////////////////////

    @Override
    public DSLinkConnection getConnection() {
        return session.getConnection();
    }

    @Override
    public Logger getLogger() {
        if (logger == null) {
            logger = Logger.getLogger(
                    getConnection().getLink().getLinkName() + ".responderSession");
        }
        return logger;
    }

    /**
     * Will throw an exception if the request doesn't have the path.
     */
    private String getPath(DSMap req) {
        String path = req.get("path", null);
        if (path == null) {
            throw new DSProtocolException("Request missing path");
        }
        return path;
    }

    public DS1InboundSubscriptions getSubscriptions() {
        return subscriptions;
    }

    public void onConnect() {
    }

    public void onConnectFail() {
    }

    public void onDisconnect() {
        finer(finer() ? "Close" : null);
        subscriptions.close();
        for (Map.Entry<Integer, DS1Stream> entry : inboundRequests.entrySet()) {
            try {
                entry.getValue().onClose(entry.getKey());
            } catch (Exception x) {
                finer(finer() ? "Close" : null, x);
            }
        }
        inboundRequests.clear();
    }

    /**
     * Handles an invoke request.
     */
    private void processInvoke(Integer rid, DSMap req) {
        DS1InboundInvoke invokeImpl = new DS1InboundInvoke(req);
        invokeImpl.setPath(getPath(req))
                  .setSession(session)
                  .setRequestId(rid)
                  .setResponderImpl(responder)
                  .setResponder(this);
        inboundRequests.put(rid, invokeImpl);
        DSRuntime.run(invokeImpl);
    }

    /**
     * Handles a list request.
     */
    private void processList(Integer rid, DSMap req) {
        DS1InboundList listImpl = new DS1InboundList();
        listImpl.setPath(getPath(req))
                .setSession(session)
                .setRequest(req)
                .setRequestId(rid)
                .setResponderImpl(responder)
                .setResponder(this);
        inboundRequests.put(listImpl.getRequestId(), listImpl);
        DSRuntime.run(listImpl);
    }

    /**
     * Process an individual request.
     */
    public void processRequest(final Integer rid, final DSMap map) {
        if (responder == null) {
            responder = getConnection().getLink();
        }
        if (responder == null) {
            throw new DSProtocolException("Not a responder");
        }
        String method = map.get("method", null);
        try {
            if ((method == null) || method.isEmpty()) {
                throwInvalidMethod(method, map);
            }
            switch (method.charAt(0)) {
                case 'c':  //close
                    if (!method.equals("close")) {
                        throwInvalidMethod(method, map);
                    }
                    final DS1Stream req = inboundRequests.remove(rid);
                    if (req != null) {
                        DSRuntime.run(new Runnable() {
                            public void run() {
                                try {
                                    req.onClose(rid);
                                } catch (Exception x) {
                                    getConnection().getLink().getLogger().log(
                                            Level.FINE, getConnection().getConnectionId(), x);
                                }
                            }
                        });
                    }
                    break;
                case 'i':  //invoke
                    if (!method.equals("invoke")) {
                        throwInvalidMethod(method, map);
                    }
                    processInvoke(rid, map);
                    break;
                case 'l':  //list
                    if (!method.equals("list")) {
                        throwInvalidMethod(method, map);
                    }
                    processList(rid, map);
                    break;
                case 'r':  //remove
                    if (method.equals("remove")) {
                        //Does this even make sense in a link?
                        severe("Remove method called");
                        sendResponse(
                                new CloseMessage(rid).setMethod(null).setStream("closed"));
                    } else {
                        throwInvalidMethod(method, map);
                    }
                    break;
                case 's':  //set, subscribe
                    if (method.equals("set")) {
                        processSet(rid, map);
                    } else if (method.equals("subscribe")) {
                        DSRuntime.run(new Runnable() {
                            @Override
                            public void run() {
                                processSubscribe(rid, map);
                            }
                        });
                        sendResponse(new CloseMessage(rid).setMethod(null));
                    } else {
                        throwInvalidMethod(method, map);
                    }
                    break;
                case 'u':
                    if (!method.equals("unsubscribe")) {
                        throwInvalidMethod(method, map);
                    }
                    DSRuntime.run(new Runnable() {
                        @Override
                        public void run() {
                            processUnsubscribe(rid, map);
                        }
                    });
                    sendResponse(new CloseMessage(rid).setMethod(null));
                    break;
                default:
                    throwInvalidMethod(method, map);
            }
        } catch (DSProtocolException x) {
            sendResponse(new ErrorResponse(x).parseRequest(map));
        } catch (Throwable x) {
            DSProtocolException px = new DSProtocolException(x.getMessage());
            px.setDetail(x);
            px.setType("serverError");
            sendResponse(new ErrorResponse(px).parseRequest(map));
        }
    }

    /**
     * Handles a set request.
     */
    private void processSet(Integer rid, DSMap req) {
        DS1InboundSet setImpl = new DS1InboundSet(req);
        setImpl.setPath(getPath(req))
               .setSession(session)
               .setRequestId(rid)
               .setResponderImpl(responder)
               .setResponder(this);
        DSRuntime.run(setImpl);
    }

    /**
     * Handles a subscribe request.
     */
    private void processSubscribe(int rid, DSMap req) {
        DSList list = req.getList("paths");
        if (list == null) {
            return;
        }
        String path;
        Integer sid;
        Integer qos;
        DSMap subscribe;
        for (int i = 0, len = list.size(); i < len; i++) {
            subscribe = list.getMap(i);
            path = subscribe.getString("path");
            sid = subscribe.getInt("sid");
            qos = subscribe.get("qos", 0);
            try {
                subscriptions.subscribe(responder, sid, path, qos);
            } catch (Exception x) {
                //invalid paths are very common
                fine(path, x);
            }
        }
    }

    /**
     * Handles an unsubscribe request.
     */
    private void processUnsubscribe(int rid, DSMap req) {
        DSList list = req.getList("sids");
        Integer sid = null;
        if (list != null) {
            for (int i = 0, len = list.size(); i < len; i++) {
                try {
                    sid = list.getInt(i);
                    subscriptions.unsubscribe(sid);
                } catch (Exception x) {
                    fine(fine() ? "Unsubscribe: " + sid : null, x);
                }
            }
        }
    }

    void removeInboundRequest(Integer requestId) {
        inboundRequests.remove(requestId);
    }

    @Override
    public boolean shouldEndMessage() {
        return session.shouldEndMessage();
    }

    @Override
    public void sendResponse(OutboundMessage res) {
        session.enqueueOutgoingResponse(res);
    }

    /**
     * Used throughout processRequest.
     */
    private void throwInvalidMethod(String methodName, DSMap request) {
        String msg = "Invalid method name " + methodName;
        finest(finest() ? (msg + ": " + request.toString()) : null);
        throw new DSProtocolException(msg).setType("invalidMethod");
    }

}