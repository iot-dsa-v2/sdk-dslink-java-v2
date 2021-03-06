package com.acuity.iot.dsa.dslink.protocol.requester;

import com.acuity.iot.dsa.dslink.protocol.DSSession;
import com.acuity.iot.dsa.dslink.protocol.message.MessageWriter;
import org.iot.dsa.dslink.requester.OutboundRequestHandler;
import org.iot.dsa.io.DSIWriter;
import org.iot.dsa.node.DSMap;

/**
 * Manages the lifecycle of a remove request and is also the outbound stream passed to the
 * requester.
 *
 * @author Daniel Shapiro, Aaron Hansen
 */
public class DSOutboundRemoveStub extends DSOutboundStub {

    private OutboundRequestHandler request;

    protected DSOutboundRemoveStub(DSRequester requester,
                                   Integer requestId,
                                   String path,
                                   OutboundRequestHandler request) {
        super(requester, requestId, path);
        this.request = request;
    }

    @Override
    public OutboundRequestHandler getHandler() {
        return request;
    }

    /**
     * Does nothing.
     */
    @Override
    public void handleResponse(DSMap response) {
    }

    /**
     * Writes the v1 version.
     */
    @Override
    public boolean write(DSSession session, MessageWriter writer) {
        DSIWriter out = writer.getWriter();
        out.beginMap();
        out.key("rid").value(getRequestId());
        out.key("method").value("remove");
        out.key("path").value(getPath());
        out.endMap();
        return true;
    }

}
