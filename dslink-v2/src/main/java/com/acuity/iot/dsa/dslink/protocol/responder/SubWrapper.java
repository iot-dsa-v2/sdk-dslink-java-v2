package com.acuity.iot.dsa.dslink.protocol.responder;

import org.iot.dsa.dslink.responder.InboundSubscribeRequest;
import org.iot.dsa.node.DSIValue;
import org.iot.dsa.node.DSStatus;
import org.iot.dsa.time.DSDateTime;

/**
 * Used by DSInboundSubscription when forwarding a request to a DSIResponder.
 *
 * @author Aaron Hansen
 */
class SubWrapper implements InboundSubscribeRequest {

    ///////////////////////////////////////////////////////////////////////////
    // Class Fields
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // Instance Fields
    ///////////////////////////////////////////////////////////////////////////

    private DSInboundSubscription inner;
    private String path;

    ///////////////////////////////////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////////////////////////////////

    SubWrapper(String path, DSInboundSubscription inner) {
        this.path = path;
        this.inner = inner;
    }
    ///////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void close() {
        inner.close();
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public Integer getRequestId() {
        return inner.getRequestId();
    }

    @Override
    public Integer getSubscriptionId() {
        return inner.getSubscriptionId();
    }

    @Override
    public void update(DSDateTime timestamp, DSIValue value, DSStatus quality) {
        inner.update(timestamp, value, quality);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Protected Methods
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // Package Methods
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // Inner Classes
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // Initialization
    ///////////////////////////////////////////////////////////////////////////

}
