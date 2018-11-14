package org.iot.dsa.node.event;

import org.iot.dsa.node.DSIValue;

/**
 * Basic implementation of DSIEvent.
 *
 * @author Aaron Hansen
 */
public class DSEvent implements DSIEvent {

    ///////////////////////////////////////////////////////////////////////////
    // Class Fields
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // Instance Fields
    ///////////////////////////////////////////////////////////////////////////

    private DSITopic topic;
    private DSIValue value;

    ///////////////////////////////////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////////////////////////////////

    public DSEvent(DSITopic topic, DSIValue value) {
        this.topic = topic;
        this.value = value;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public DSIValue getData() {
        return value;
    }

    @Override
    public DSITopic getTopic() {
        return topic;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Protected Methods
    ///////////////////////////////////////////////////////////////////////////

}