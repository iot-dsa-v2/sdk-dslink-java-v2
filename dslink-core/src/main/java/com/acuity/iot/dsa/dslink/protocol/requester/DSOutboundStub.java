package com.acuity.iot.dsa.dslink.protocol.requester;

import com.acuity.iot.dsa.dslink.protocol.message.OutboundMessage;
import org.iot.dsa.dslink.requester.ErrorType;
import org.iot.dsa.dslink.requester.OutboundRequestHandler;
import org.iot.dsa.dslink.requester.OutboundStream;
import org.iot.dsa.node.DSElement;
import org.iot.dsa.node.DSMap;

/**
 * All stubs manage the lifecycle of a request and are also the outbound stream passed back to the
 * requester.
 *
 * @author Daniel Shapiro, Aaron Hansen
 */
public abstract class DSOutboundStub implements OutboundMessage, OutboundStream {

    ///////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////

    private String path;
    private boolean open = true;
    private DSRequester requester;
    private Integer requestId;

    ///////////////////////////////////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////////////////////////////////

    DSOutboundStub(DSRequester requester, Integer requestId, String path) {
        this.requester = requester;
        this.requestId = requestId;
        this.path = path;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Methods
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void closeStream() {
        if (!open) {
            return;
        }
        getRequester().sendClose(getRequestId());
        handleClose();
    }

    public abstract OutboundRequestHandler getHandler();

    public String getPath() {
        return path;
    }

    public DSRequester getRequester() {
        return requester;
    }

    public Integer getRequestId() {
        return requestId;
    }

    public void handleClose() {
        if (!open) {
            return;
        }
        open = false;
        try {
            getHandler().onClose();
        } catch (Exception x) {
            getRequester().error(getRequester().getPath(), x);
        }
        getRequester().removeRequest(getRequestId());
    }

    public void handleError(DSElement details) {
        if (!open) {
            return;
        }
        try {
            ErrorType type = ErrorType.internalError;
            String msg;
            if (details.isMap()) {
                String detail = null;
                DSMap map = details.toMap();
                String tmp = map.getString("type");
                if (tmp.equals("permissionDenied")) {
                    type = ErrorType.permissionDenied;
                } else if (tmp.equals("invalidRequest")) {
                    type = ErrorType.badRequest;
                } else if (tmp.equals("invalidPath")) {
                    type = ErrorType.badRequest;
                } else if (tmp.equals("notSupported")) {
                    type = ErrorType.notSupported;
                } else {
                    type = ErrorType.internalError;
                }
                msg = map.getString("msg");
                detail = map.getString("detail");
                if (msg == null) {
                    msg = detail;
                }
                if (msg == null) {
                    msg = details.toString();
                }
            } else {
                type = ErrorType.internalError;
                msg = details.toString();
            }
            if (msg == null) {
                msg = "";
            }
            getHandler().onError(type, msg);
        } catch (Exception x) {
            getRequester().error(getRequester().getPath(), x);
        }
    }

    public void handleError(ErrorType type, String message) {
        if (!open) {
            return;
        }
        try {
            getHandler().onError(type, message);
        } catch (Exception x) {
            getRequester().error(getRequester().getPath(), x);
        }
    }

    /**
     * Handle the V1 response map.
     */
    public abstract void handleResponse(DSMap map);

    public boolean isStreamOpen() {
        return open;
    }

}
