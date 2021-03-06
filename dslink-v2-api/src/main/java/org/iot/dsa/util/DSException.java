package org.iot.dsa.util;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * An runtime exception that forwards most calls to the inner exception. This is to exclude itself
 * from reporting and expose the real issue as soon as possible. <p> The throwRuntime method is a
 * convenience for converting checked exceptions into runtime exceptions.
 *
 * @author Aaron Hansen
 */
public class DSException extends RuntimeException {

    /////////////////////////////////////////////////////////////////
    // Constructors
    /////////////////////////////////////////////////////////////////

    private Throwable inner;

    /////////////////////////////////////////////////////////////////
    // Methods - Public and in alphabetical order by method name.
    /////////////////////////////////////////////////////////////////

    public DSException(Throwable inner) {
        this.inner = inner;
    }

    @Override
    public Throwable getCause() {
        return null;
    }

    @Override
    public String getLocalizedMessage() {
        return inner.getLocalizedMessage();
    }

    @Override
    public String getMessage() {
        return inner.getMessage();
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        return inner.getStackTrace();
    }

    @Override
    public Throwable initCause(Throwable cause) {
        return this;
    }

    /**
     * Attempts come up with the best description of the argument.
     */
    public static String makeMessage(Throwable t) {
        if (t.getCause() != null) {
            return makeMessage(t.getCause());
        }
        String message = t.getMessage();
        if ((message == null) || message.trim().isEmpty()) {
            return t.toString();
        }
        return message;
    }

    /**
     * If the given exception is already a runtime exception, it is cast and returned,
     * otherwise it will be returned wrapped by an instance of this class.
     */
    public static RuntimeException makeRuntime(Throwable x) {
        if (x instanceof RuntimeException) {
            return (RuntimeException) x;
        }
        return new DSException(x);
    }

    @Override
    public void printStackTrace() {
        inner.printStackTrace();
    }

    @Override
    public void printStackTrace(PrintStream out) {
        inner.printStackTrace(out);
    }

    @Override
    public void printStackTrace(PrintWriter out) {
        inner.printStackTrace(out);
    }

    /**
     * If the given exception is already a runtime exception, it is rethrown, otherwise
     * it will be thrown wrapped by an instance of this class.
     */
    public static void throwRuntime(Throwable x) {
        throw makeRuntime(x);
    }

    /////////////////////////////////////////////////////////////////
    // Fields - in alphabetical order by field name.
    /////////////////////////////////////////////////////////////////

    @Override
    public String toString() {
        return inner.toString();
    }

}
