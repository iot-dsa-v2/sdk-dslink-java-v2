package com.acuity.iot.dsa.dslink.protocol.v1.responder;

import com.acuity.iot.dsa.dslink.protocol.DSProtocolException;
import com.acuity.iot.dsa.dslink.protocol.DSSession;
import com.acuity.iot.dsa.dslink.protocol.DSStream;
import com.acuity.iot.dsa.dslink.protocol.responder.DSInboundInvoke;
import com.acuity.iot.dsa.dslink.protocol.responder.DSInboundList;
import com.acuity.iot.dsa.dslink.protocol.responder.DSInboundRequest;
import com.acuity.iot.dsa.dslink.protocol.responder.DSInboundSet;
import com.acuity.iot.dsa.dslink.protocol.responder.DSInboundSubscriptions;
import com.acuity.iot.dsa.dslink.protocol.responder.DSResponder;
import com.acuity.iot.dsa.dslink.protocol.v1.CloseMessage;
import org.iot.dsa.DSRuntime;
import org.iot.dsa.node.DSElement;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.security.DSPermission;

/**
 * Implementation DSA v1.
 *
 * @author Aaron Hansen
 */
public class DS1Responder extends DSResponder {

    ///////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////

    private DSInboundSubscriptions subscriptions = new DSInboundSubscriptions(this);

    /////////////////////////////////////////////////////////////////
    // Methods - Constructors
    /////////////////////////////////////////////////////////////////

    public DS1Responder() {
    }

    public DS1Responder(DSSession session) {
        super(session);
    }

    /////////////////////////////////////////////////////////////////
    // Methods - In alphabetical order by method name.
    /////////////////////////////////////////////////////////////////

    /**
     * Process an individual request.
     */
    public void handleRequest(final Integer rid, final DSMap map) {
        String method = map.get("method", null);
        try {
            if ((method == null) || method.isEmpty()) {
                sendInvalidMethod(rid, method);
            } else {
                switch (method.charAt(0)) {
                    case 'c':  //close
                        if (!method.equals("close")) {
                            sendInvalidMethod(rid, method);
                        }
                        DSRuntime.run(() -> {
                            try {
                                DSStream req = removeRequest(rid);
                                if (req != null) {
                                    req.onClose(rid);
                                }
                            } catch (Exception x) {
                                debug(getPath(), x);
                            }
                        });
                        break;
                    case 'i':  //invoke
                        if (!method.equals("invoke")) {
                            if (method.equals("init")) {
                                break;
                            }
                            sendInvalidMethod(rid, method);
                        }
                        processInvoke(rid, map);
                        break;
                    case 'l':  //list
                        if (!method.equals("list")) {
                            sendInvalidMethod(rid, method);
                        }
                        processList(rid, map);
                        break;
                    case 'r':  //remove
                        if (method.equals("remove")) {
                            //Does this even make sense in a link?
                            error("Remove method called");
                            sendClose(rid);
                        } else {
                            sendInvalidMethod(rid, method);
                        }
                        break;
                    case 's':  //set, subscribe
                        if (method.equals("set")) {
                            processSet(rid, map);
                        } else if (method.equals("subscribe")) {
                            processSubscribe(rid, map);
                            sendClose(rid);
                        } else {
                            sendInvalidMethod(rid, method);
                        }
                        break;
                    case 'u':
                        if (!method.equals("unsubscribe")) {
                            sendInvalidMethod(rid, method);
                        }
                        DSRuntime.run(() -> processUnsubscribe(rid, map));
                        sendClose(rid);
                        break;
                    default:
                        sendInvalidMethod(rid, method);
                }
            }
        } catch (DSProtocolException x) {
            sendError(rid, x);
        } catch (Throwable x) {
            error(getPath(), x);
            sendError(rid, x);
        }
    }

    @Override
    public void sendClose(int rid) {
        sendResponse(new CloseMessage(rid));
    }

    @Override
    public void sendError(DSInboundRequest req, Throwable reason, boolean close) {
        sendResponse(new ErrorMessage(req.getRequestId(), reason).setClose(close));
    }

    public void sendError(int rid, Throwable reason) {
        sendResponse(new ErrorMessage(rid, reason));
    }

    @Override
    protected DSInboundSubscriptions getSubscriptions() {
        return subscriptions;
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

    /**
     * Handles an invoke request.
     */
    private void processInvoke(Integer rid, DSMap req) {
        String permit = req.get("permit", "config");
        DSPermission permission = DSPermission.forString(permit);
        DSInboundInvoke invokeImpl = new DSInboundInvoke(req.getMap("params"), permission);
        invokeImpl.setPath(getPath(req))
                  .setSession(getSession())
                  .setRequestId(rid)
                  .setLink(getLink())
                  .setResponder(this);
        putRequest(rid, invokeImpl);
        DSRuntime.run(invokeImpl);
    }

    /**
     * Handles a list request.
     */
    private void processList(Integer rid, DSMap req) {
        DSInboundList listImpl = new DSInboundList();
        listImpl.setPath(getPath(req))
                .setSession(getSession())
                .setRequestId(rid)
                .setLink(getLink())
                .setResponder(this);
        putRequest(listImpl.getRequestId(), listImpl);
        DSRuntime.run(listImpl);
    }

    /**
     * Handles a set request.
     */
    private void processSet(Integer rid, DSMap req) {
        String permit = req.get("permit", "config");
        DSPermission permission = DSPermission.forString(permit);
        DSElement value = req.get("value");
        DSInboundSet setImpl = new DSInboundSet(value, permission);
        setImpl.setPath(getPath(req))
               .setSession(getSession())
               .setRequestId(rid)
               .setLink(getLink())
               .setResponder(this);
        setImpl.run();
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
                subscriptions.subscribe(sid, path, qos);
            } catch (Exception x) {
                //invalid paths are very common
                debug(path, x);
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
                    error(debug() ? "Unsubscribe: " + sid : null, x);
                }
            }
        }
    }

    /**
     * Used throughout processRequest.
     */
    private void sendInvalidMethod(int rid, String methodName) {
        String msg = "Invalid method: " + methodName;
        error(msg);
        sendResponse(new ErrorMessage(rid, msg).setType("invalidMethod"));
    }

}
