package com.acuity.iot.dsa.dslink.protocol.responder;

import com.acuity.iot.dsa.dslink.protocol.message.RequestPath;
import org.iot.dsa.dslink.DSIResponder;
import org.iot.dsa.dslink.DSRequestException;
import org.iot.dsa.dslink.responder.InboundSetRequest;
import org.iot.dsa.node.DSElement;
import org.iot.dsa.node.DSIValue;
import org.iot.dsa.node.DSInfo;
import org.iot.dsa.node.DSNode;
import org.iot.dsa.security.DSPermission;

public class DSInboundSet extends DSInboundRequest implements InboundSetRequest, Runnable {

    private DSElement value;
    private DSPermission permission;

    public DSInboundSet(DSElement value, DSPermission permission) {
        this.permission = permission;
        this.value = value;
    }

    @Override
    public DSPermission getPermission() {
        return permission;
    }

    @Override
    public DSElement getValue() {
        return value;
    }

    @Override
    public void run() {
        try {
            RequestPath path = new RequestPath(getPath(), getLink());
            if (path.isResponder()) {
                DSIResponder responder = (DSIResponder) path.getTarget();
                setPath(path.getPath());
                responder.onSet(this);
            } else {
                DSNode parent = path.getParent();
                DSInfo info = path.getInfo();
                if (info.isReadOnly()) {
                    throw new DSRequestException("Not writable: " + getPath());
                }
                //TODO verify incoming permission
                if (info.isNode()) {
                    info.getNode().onSet(value);
                } else {
                    DSIValue current = info.getValue();
                    if (current == null) {
                        if (info.getDefaultObject() instanceof DSIValue) {
                            current = (DSIValue) info.getDefaultObject();
                        }
                    }
                    if (current != null) {
                        current = current.valueOf(value);
                    } else {
                        current = value;
                    }
                    parent.onSet(info, current);
                }
            }
            sendClose();
        } catch (Exception x) {
            error(getPath(), x);
            getResponder().sendError(this, x);
        }
    }

    /**
     * Override point for V2.
     */
    protected void sendClose() {
        getResponder().sendClose(getRequestId());
    }

}