package org.iot.dsa.node;

import org.iot.dsa.util.DSException;

/**
 * Wrapper for Java enums.
 *
 * @author Aaron Hansen
 */
public class DSJavaEnum extends DSValue implements DSIEnum, DSIMetadata, DSIStorable {

    // Constants
    // ---------

    public static final DSJavaEnum NULL = new DSJavaEnum();

    // Fields
    // ------

    private Enum value;
    private DSList values;

    // Constructors
    // ------------

    private DSJavaEnum() {
    }

    private DSJavaEnum(Enum value) {
        this.value = value;
    }

    // Public Methods
    // --------------

    /**
     * True if the argument is a DSDynamicEnum and the values are equal or they are both isNull.
     */
    @Override
    public boolean equals(Object arg) {
        if (arg == this) {
            return true;
        }
        if (arg instanceof DSJavaEnum) {
            DSJavaEnum denum = (DSJavaEnum) arg;
            return denum.value.equals(value);
        }
        return false;
    }

    @Override
    public DSList getEnums(DSList bucket) {
        if (bucket == null) {
            bucket = new DSList();
        }
        if (values == null) {
            values = new DSList();
            for (Enum e : value.getClass().getEnumConstants()) {
                values.add(e.name());
            }
        }
        bucket.addAll(values);
        return bucket;
    }

    @Override
    public void getMetadata(DSMap bucket) {
        if (values == null) {
            bucket.put(DSMetadata.ENUM_RANGE, getEnums(new DSList()));
        } else {
            bucket.put(DSMetadata.ENUM_RANGE, values.copy());
        }
    }

    @Override
    public DSValueType getValueType() {
        return DSValueType.ENUM;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean isNull() {
        return this == NULL;
    }

    @Override
    public DSJavaEnum restore(DSElement arg) {
        if ((arg == null) || arg.isNull()) {
            return NULL;
        }
        Enum e = null;
        try {
            String s = arg.toString();
            int idx = s.indexOf(':');
            String value = s.substring(0, idx);
            Class clazz = Class.forName(s.substring(++idx));
            e = Enum.valueOf(clazz, value);
        } catch (Exception x) {
            DSException.throwRuntime(x);
        }
        return valueOf(e);
    }

    @Override
    public DSElement store() {
        return DSString.valueOf(value.name() + ':' + value.getClass().getName());
    }

    @Override
    public DSElement toElement() {
        if (isNull()) {
            return DSString.NULL;
        }
        return DSString.valueOf(value.name());
    }

    /**
     * The Java enum.
     */
    public Enum toEnum() {
        return value;
    }

    @Override
    public String toString() {
        return value.name();
    }

    @Override
    public DSJavaEnum valueOf(DSElement arg) {
        if ((arg == null) || arg.isNull()) {
            return NULL;
        }
        Enum e = null;
        try {
            String s = arg.toString();
            e = Enum.valueOf(value.getClass(), s);
        } catch (Exception x) {
            DSException.throwRuntime(x);
        }
        return valueOf(e);
    }

    /**
     * Creates an enum for the given value (and it's range).
     */
    public static DSJavaEnum valueOf(Enum value) {
        if (value == null) {
            return NULL;
        }
        return new DSJavaEnum(value);
    }

    public DSJavaEnum valueOf(String arg) {
        if ((arg == null) || arg.isEmpty()) {
            return NULL;
        }
        Enum e = null;
        try {
            e = Enum.valueOf(value.getClass(), arg);
        } catch (Exception x) {
            DSException.throwRuntime(x);
        }
        return valueOf(e);
    }

    // Initialization
    // --------------

    static {
        DSRegistry.registerDecoder(DSJavaEnum.class, NULL);
    }

}
