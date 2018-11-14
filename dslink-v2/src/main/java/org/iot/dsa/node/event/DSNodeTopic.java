package org.iot.dsa.node.event;

import org.iot.dsa.node.DSIValue;

/**
 * Topics implicitly available on all nodes.
 *
 * @author Aaron Hansen
 */
public enum DSNodeTopic implements DSIEvent, DSITopic {

    ///////////////////////////////////////////////////////////////////////////
    // Class Fields
    ///////////////////////////////////////////////////////////////////////////

    /**
     * A child info will accompany the event and the event data will be null.
     */
    CHILD_ADDED,

    /**
     * A child info will accompany the event and the event data will be null.
     */
    CHILD_REMOVED,

    /**
     * A child info will accompany the event and the event data be the old name as a DSString.
     */
    CHILD_RENAMED,

    /**
     * A child info will accompany the event and the event data will be null.
     */
    METADATA_CHANGED,

    /**
     * A child info may or may not accompany the event and the event data will be null.
     */
    VALUE_CHANGED;

    ///////////////////////////////////////////////////////////////////////////
    // Instance Fields
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Returns this.
     */
    @Override
    public DSNodeTopic copy() {
        return this;
    }

    @Override
    public DSIValue getData() {
        return null;
    }

    @Override
    public DSITopic getTopic() {
        return this;
    }

    /**
     * Only tests instance equality.
     */
    @Override
    public boolean isEqual(Object obj) {
        return obj == this;
    }

    /**
     * False
     */
    @Override
    public boolean isNull() {
        return false;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Protected Methods
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // Package / Private Methods
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // Inner Classes
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // Initialization
    ///////////////////////////////////////////////////////////////////////////

}