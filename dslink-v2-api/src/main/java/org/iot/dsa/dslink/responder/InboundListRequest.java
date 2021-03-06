package org.iot.dsa.dslink.responder;

import org.iot.dsa.dslink.Action;
import org.iot.dsa.dslink.DSIResponder;
import org.iot.dsa.dslink.Node;
import org.iot.dsa.dslink.Value;
import org.iot.dsa.node.DSElement;
import org.iot.dsa.node.DSElementType;

/**
 * How to respond to a list request.  Implementations must first send the target of the
 * request.  Then any children, listComplete and finally changes as they happen.  If the target is
 * resent, that will clear all prior state so children should be also resent.
 *
 * @author Aaron Hansen
 * @see DSIResponder#onList(InboundListRequest)
 */
public interface InboundListRequest extends InboundRequest {

    /**
     * Allows the responder to forcefully close the list stream.
     */
    void close();

    /**
     * Allows the responder to forcefully close the list stream with an error.
     */
    void close(Exception reason);

    /**
     * Whether or not the list stream is still open.
     */
    boolean isOpen();

    /**
     * Call after the initial state of the target and it's children has been sent.
     */
    void listComplete();

    /**
     * Add or change any metadata on the target of the request after beginUpdates.  Can also be
     * used as a pass-thru mechanism.
     */
    void send(String name, DSElement value);

    /**
     * Add or update a child action to the list.
     *
     * @param name        Will be encoded, it's not usually necessary to have a display name.
     * @param displayName Can be null.
     * @param admin       Whether or not the action requires admin level permission.
     * @param readonly    Whether or not the action requires write permission.
     */
    void sendChildAction(String name, String displayName, boolean admin, boolean readonly);

    /**
     * Add or update a child node to the list.
     *
     * @param name        Will be encoded, it's not usually necessary to have a display name.
     * @param displayName Can be null.
     * @param admin       Whether or not admin level required to see node.
     */
    void sendChildNode(String name, String displayName, boolean admin);

    /**
     * Add or update a child value to the list.
     *
     * @param name        Will be encoded, it's not usually necessary to have a display name.
     * @param displayName Can be null.
     * @param type        Required.
     * @param admin       Whether or not admin level required to see node.
     * @param readonly    Whether or not the value is writable.
     */
    void sendChildValue(String name,
                        String displayName,
                        DSElementType type,
                        boolean admin,
                        boolean readonly);

    /**
     * The responder should call this whenever a child or metadata is removed.
     */
    void sendRemove(String name);

    /**
     * This should be called first to provide details about the target and should be an
     * action, node or value.  Subsequent calls will reset the state of the list such that
     * children will need to be resent as well as sendBeginUpdates.
     *
     * @param object Cannot be the generic ApiObject interface, must be one of the subtypes; action,
     *               node or value.
     * @see Action
     * @see Node
     * @see Value
     */
    void sendTarget(Node object);

}
