package org.iot.dsa.io.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.zip.ZipInputStream;

/**
 * Allows unreading of a byte for DSReader.
 *
 * @author Aaron Hansen
 */
class JsonInput implements JsonReader.Input {

    private static final int BUF_SIZE = 8192;
    private char[] buf = new char[BUF_SIZE];
    private Reader in;
    private int len;
    private int next;

    public JsonInput() {
    }

    public JsonInput(InputStream in)
            throws IOException {
        setInput(in, "UTF-8");
    }

    public JsonInput(InputStream in, String charset)
            throws IOException {
        setInput(in, charset);
    }

    public JsonInput(Reader in) {
        setInput(in);
    }

    @Override
    public void close() {
        try {
            in.close();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    @Override
    public int read() {
        if (next >= len) {
            try {
                fill();
            } catch (IOException x) {
                throw new RuntimeException(x);
            }
            if (next >= len) {
                return -1;
            }
        }
        return buf[next++];
    }

    public int read(char[] buf, int off, int len) {
        throw new IllegalStateException("Will never be called");
    }

    public void setInput(InputStream in) throws IOException {
        setInput(in, "UTF-8");
    }

    public void setInput(InputStream in, String charset) throws IOException {
        //check if it is zip
        if (in.markSupported()) {
            byte[] zip = new byte[4];
            in.mark(4);
            in.read(zip);
            in.reset();
            if ((zip[0] == 0x50) &&
                    (zip[1] == 0x4b) &&
                    (zip[2] == 0x03) &&
                    (zip[3] == 0x04)) {
                ZipInputStream unzip = new ZipInputStream(in);
                unzip.getNextEntry();
                in = unzip;
            }
        }
        if (charset == null) {
            this.in = new InputStreamReader(in);
        } else {
            this.in = new InputStreamReader(in, charset);
        }
        this.len = 0;
        this.next = 0;
    }

    public void setInput(Reader in) {
        this.in = in;
        this.len = 0;
        this.next = 0;
    }

    @Override
    public void unread() {
        if (next > 0) {
            --next;
        }
    }

    private void fill() throws IOException {
        if (in == null) {
            return;
        }
        len = in.read(buf, 0, buf.length);
        next = 0;
    }


}
