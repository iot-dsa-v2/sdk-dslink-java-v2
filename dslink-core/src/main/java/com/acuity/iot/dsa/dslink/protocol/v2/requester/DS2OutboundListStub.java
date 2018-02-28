package com.acuity.iot.dsa.dslink.protocol.v2.requester;

import com.acuity.iot.dsa.dslink.io.msgpack.MsgpackReader;
import com.acuity.iot.dsa.dslink.protocol.message.MessageWriter;
import com.acuity.iot.dsa.dslink.protocol.requester.DSOutboundListStub;
import com.acuity.iot.dsa.dslink.protocol.requester.DSRequester;
import com.acuity.iot.dsa.dslink.protocol.v2.DS2MessageReader;
import com.acuity.iot.dsa.dslink.protocol.v2.DS2MessageWriter;
import com.acuity.iot.dsa.dslink.protocol.v2.MessageConstants;
import com.acuity.iot.dsa.dslink.transport.DSBinaryTransport;
import java.io.IOException;
import java.io.InputStream;
import org.iot.dsa.dslink.requester.OutboundListHandler;
import org.iot.dsa.node.DSBytes;
import org.iot.dsa.node.DSElement;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.node.DSPath;
import org.iot.dsa.util.DSException;

public class DS2OutboundListStub extends DSOutboundListStub
        implements DS2OutboundStub, MessageConstants {

    private byte state = STS_INITIALIZING;

    protected DS2OutboundListStub(DSRequester requester,
                                  Integer requestId,
                                  String path,
                                  OutboundListHandler handler) {
        super(requester, requestId, path, handler);
    }

    public void handleResponse(DS2MessageReader response) {
        OutboundListHandler handler = getHandler();
        if (state == STS_INITIALIZING) {
            Byte status = (Byte) response.getHeader(MessageConstants.HDR_STATUS);
            if (status.byteValue() == STS_OK) {
                state = STS_OK;
                getHandler().onInitialized();
            }
        }
        try {
            MsgpackReader reader = response.getBodyReader();
            InputStream in = response.getBody();
            int bodyLen = response.getBodyLength();
            String name;
            DSElement value;
            while (bodyLen > 0) {
                int len = DSBytes.readShort(in, false);
                name = reader.readUTF(len);
                len = DSBytes.readShort(in, false);
                if (len == 0) {
                    handler.onRemove(name);
                } else {
                    bodyLen -= len;
                    reader.reset();
                    value = reader.getElement();
                    bodyLen -= len;
                    handler.onUpdate(DSPath.decodeName(name), value);
                }
            }
        } catch (IOException x) {
            DSException.throwRuntime(x);
        }
    }

    @Override
    public void write(MessageWriter writer) {
        //if has multipart remaining send that
        DS2MessageWriter out = (DS2MessageWriter) writer;
        out.init(getRequestId(), MSG_LIST_REQ);
        out.addHeader((byte)HDR_TARGET_PATH, getPath());
        out.write((DSBinaryTransport) getRequester().getTransport());
        //if multipart
    }

}
