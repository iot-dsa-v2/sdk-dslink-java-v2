package org.iot.dsa.node;

/**
 * This is used for decoding raw json.  Try to use strong typed null instances instead (e.g.
 * DBool.NULL).
 *
 * @author Aaron Hansen
 */
public class DSNull extends DSElement {

    // Constants
    // ---------

    public static DSNull NULL = new DSNull();

    // Fields
    // ------

    // Constructors
    // ------------

    private DSNull() {
    }

    // Public Methods
    // --------------

    /**
     * Returns this.
     */
    @Override
    public DSNull copy() {
        return this;
    }

    /**
     * Returns this.
     */
    @Override
    public DSNull valueOf(DSElement arg) {
        return this;
    }

    /**
     * Returns this.
     */
    @Override
    public DSNull toElement() {
        return this;
    }

    /**
     * True of the arg == this.
     */
    public boolean equals(Object arg) {
        return arg == this;
    }

    @Override
    public DSValueType getValueType() {
        return DSValueType.DYNAMIC;
    }

    /**
     * True
     */
    public boolean isNull() {
        return true;
    }

    @Override
    public DSElementType getElementType() {
        return DSElementType.NULL;
    }

    public String toString() {
        return "null";
    }

    // Initialization
    // --------------

    static {
        DSRegistry.registerDecoder(DSNull.class, NULL);
    }

}
