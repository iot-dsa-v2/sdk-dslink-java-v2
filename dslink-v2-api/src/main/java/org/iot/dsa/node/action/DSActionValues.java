package org.iot.dsa.node.action;

import java.util.ArrayList;
import org.iot.dsa.node.DSIValue;
import org.iot.dsa.node.DSMap;

/**
 * This is a convenience implementation of ActionValues.  It is for actions that return a single
 * row of values.
 *
 * @author Aaron Hansen
 */
public class DSActionValues implements ActionValues {

    ///////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////

    private DSAction action;
    private ArrayList<DSIValue> values = new ArrayList<DSIValue>();

    ///////////////////////////////////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////////////////////////////////

    public DSActionValues(DSAction action) {
        this.action = action;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Methods in alphabetical order
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Values must be added in the same order they were defined in the action.
     */
    public DSActionValues addResult(DSIValue value) {
        values.add(value);
        return this;
    }

    public DSAction getAction() {
        return action;
    }

    @Override
    public int getColumnCount() {
        return values.size();
    }

    @Override
    public void getMetadata(int idx, DSMap map) {
        action.getColumnMetadata(idx, map);
    }

    @Override
    public DSIValue getValue(int idx) {
        return values.get(idx);
    }

    /**
     * Does nothing.
     */
    public void onClose() {
    }

}