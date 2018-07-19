package org.iot.dsa.io;

import java.io.Closeable;
import org.iot.dsa.node.DSElement;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSMap;

/**
 * A decoder that can be used to get an entire graph in pieces, or one large group, or somewhere in
 * between. To get an entire graph, call getElement(), getMap() or getList(). Otherwise, use the
 * next() method to iterate the elements of the input.
 * <p>
 * <p>
 * <p>
 * When next() returns:
 *
 * <ul>
 *
 * <li>ROOT - The initial state, not in a list or map, call next() or getElement().
 *
 * <li>BEGIN_LIST - Call getList() to decodeKeys the entire list, or call next again to get the
 * first element of the list (or END_LIST if empty).
 *
 * <li>BEGIN_MAP - Call getMap() to decodeKeys the entire map, or call next again to get the first
 * key of the map (or END_MAP if empty).
 *
 * <li>END_INPUT - Parsing is finished, close the reader.
 *
 * <li>END_LIST - The current list is complete, call next again.
 *
 * <li>END_MAP - The current map is complete, call next again.
 *
 * <li>BOOLEAN,DOUBLE,LONG,NULL,STRING - Call getElement() or the corresponding getter.
 *
 * </ul>
 * <p>
 * <p>
 * <p>
 * Be aware that if the underlying encoding (such as JSON) doesn't provide a mechanism to
 * differentiate between data types (such as numbers), values might not get as the same type they
 * were encoded.
 *
 * @author Aaron Hansen
 */
public interface DSIReader extends Closeable {

    /**
     * Close the input.
     */
    public void close();

    /**
     * Returns the value when last() == BOOLEAN.
     */
    public boolean getBoolean();

    /**
     * Returns the value when last() == BYTES.
     */
    public byte[] getBytes();

    /**
     * Returns the value when last() == DOUBLE.
     */
    public double getDouble();

    /**
     * Returns the DSElement when last() == raw type or ROOT.
     */
    public DSElement getElement();

    /**
     * Returns the value when last() == LONG.
     */
    public long getLong();

    /**
     * This should only be called when last() == BEGIN_LIST and it will decodeKeys the entire
     * list.  Call next rather than this method to get the list in pieces.
     */
    public DSList getList();

    /**
     * This should only be called when last() == BEGIN_MAP and it will decodeKeys the entire map.
     * Call next rather than this method get the map in pieces.
     */
    public DSMap getMap();

    /**
     * Returns the value when last() == STRING.
     */
    public String getString();

    /**
     * The last value returned from next(). At the beginning of a document, before next has been
     * called, this will return ROOT.
     */
    public Token last();

    /**
     * Advances the reader to the next item and returns the token representing it's current state.
     */
    public Token next();

    /**
     * Sets last() == ROOT.
     */
    public DSIReader reset();

    /**
     * Represents the state of the reader, and determines which getter should be called next.
     */
    public enum Token {
        BEGIN_LIST,
        BEGIN_MAP,
        BOOLEAN,
        BYTES,
        END_INPUT,
        END_LIST,
        END_MAP,
        DOUBLE,
        LONG,
        NULL,
        ROOT,
        STRING,
    }


}