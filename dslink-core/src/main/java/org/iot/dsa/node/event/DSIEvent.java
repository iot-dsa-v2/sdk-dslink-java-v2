package org.iot.dsa.node.event;

import org.iot.dsa.node.DSInfo;
import org.iot.dsa.node.DSNode;

/**
 * This is an empty interface, DSTopics are allowed to define events however they wish.  If an
 * event needs more parameters than two predefined ones
 *
 * @author Aaron Hansen
 * @see DSISubscriber#onEvent(DSNode, DSInfo, DSTopic, DSIEvent, Object, Object
 * @see DSTopic
 */
public interface DSIEvent {

}
