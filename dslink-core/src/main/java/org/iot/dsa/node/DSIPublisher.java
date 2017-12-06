package org.iot.dsa.node;

/**
 * DSEvent producer.
 *
 * <p>
 *
 * Nodes do not notify parent containers of any events.  However, if a node subtype implements this
 * interface, parent containers will treat all events emitted as a child changed event.
 *
 * <p>
 *
 * Mutable DSIValues such as DSFlexEnum should implement this interface. However, DSList and DSMap
 * do not.
 *
 * <p>
 *
 * Containers will not publish events if they are stopped.
 *
 * @author Aaron Hansen
 * @see DSISubscriber
 */
public interface DSIPublisher {

    /**
     * Subscribes the argument, has no effect if the argument is already subscribed.
     *
     * @param subscriber Who is subscribing.
     */
    public void subscribe(DSISubscriber subscriber);

    /**
     * Unsubscribes the argument, has no effect if the argument is not currently subscribed.
     */
    public void unsubscribe(DSISubscriber subscriber);

    /**
     * Passed to DSISubscriber onEvent to describe the event.
     */
    public enum Event {

        /**
         * The event will have a source (parent) and an info.
         */
        CHILD_ADDED,

        /**
         * A value child has changed.  The event will have a source (parent) and a child
         * info.
         */
        CHILD_CHANGED,

        /**
         * The meta-data about a child has changed.  The event will have a source (parent)
         * and an info.
         */
        CHILD_INFO_CHANGED,

        /**
         * The event will have a source (parent) and an info.
         */
        CHILD_REMOVED,

        /**
         * The publisher represents a value and has changed.  The event will have a
         * source (parent) but * no child.
         */
        PUBLISHER_CHANGED,

        /**
         * The event will have a source (parent) and possibly a child.
         */
        SUBSCRIPTION_ENDED,

    }

}
