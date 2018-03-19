package com.acuity.iot.dsa.dslink.protocol.v2.responder;

import com.acuity.iot.dsa.dslink.protocol.message.MessageWriter;
import com.acuity.iot.dsa.dslink.protocol.responder.DSInboundInvoke;
import com.acuity.iot.dsa.dslink.protocol.v2.DS2MessageWriter;
import com.acuity.iot.dsa.dslink.protocol.v2.MessageConstants;
import com.acuity.iot.dsa.dslink.protocol.v2.MultipartWriter;
import com.acuity.iot.dsa.dslink.transport.DSBinaryTransport;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.security.DSPermission;

/**
 * List implementation for a responder.
 *
 * @author Aaron Hansen
 */
class DS2InboundInvoke extends DSInboundInvoke implements MessageConstants {

    private int seqId = 0;
    MultipartWriter multipart;

    DS2InboundInvoke(DSMap parameters, DSPermission permission) {
        super(parameters, permission);
    }

    @Override
    public void write(MessageWriter writer) {
        DS2MessageWriter out = (DS2MessageWriter) writer;
        if (multipart != null) {
            if (multipart.update(out, getSession().getNextAck())) {
                getResponder().sendResponse(this);
            }
            return;
        }
        int ack = getSession().getNextAck();
        out.init(getRequestId(), ack);
        out.setMethod(MSG_INVOKE_RES);
        out.addIntHeader(HDR_SEQ_ID, seqId);
        seqId++;
        super.write(writer);
        if (out.requiresMultipart()) {
            multipart = out.makeMultipart();
            multipart.update(out, ack);
            getResponder().sendResponse(this);
        } else {
            out.write((DSBinaryTransport) getResponder().getTransport());
        }
    }

    @Override
    protected void writeBegin(MessageWriter writer) {
        writer.getWriter().beginMap();
    }

    @Override
    protected void writeClose(MessageWriter writer) {
        DS2MessageWriter out = (DS2MessageWriter) writer;
        out.addByteHeader(HDR_STATUS, STS_CLOSED);
    }

    @Override
    protected void writeOpen(MessageWriter writer) {
        DS2MessageWriter out = (DS2MessageWriter) writer;
        out.addByteHeader(HDR_STATUS, STS_OK);
    }


}
