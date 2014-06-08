/**
 * Copyright 2014 Apigee Corporation.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.apigee.rowboat.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A few utility functions, mainly for Rhino, that are useful when writing Node modules in Java.
 */
public class Utils
{
    private static final Pattern DOUBLE_QUOTED =
        Pattern.compile("^[\\s]*\"(.*)\"[\\s]*$");
    private static final Pattern SINGLE_QUOTED =
        Pattern.compile("^[\\s]*\'(.*)\'[\\s]*$");

    /**
     * Read an entire input stream into a single string, and interpret it as UTF-8.
     */
    public static String readStream(InputStream in)
        throws IOException
    {
        InputStreamReader rdr = new InputStreamReader(in, Charsets.UTF8);
        StringBuilder str = new StringBuilder();
        char[] buf = new char[4096];
        int r;
        do {
            r = rdr.read(buf);
            if (r > 0) {
                str.append(buf, 0, r);
            }
        } while (r > 0);
        return str.toString();
    }

    /**
     * Read an entire file into a single string, and interpret it as UTF-8.
     */
    public static String readFile(File f)
        throws IOException
    {
        FileInputStream in = new FileInputStream(f);
        try {
            return readStream(in);
        } finally {
            in.close();
        }
    }

    /**
     * Given a class, find the first method named "name". Since this doesn't handle operator
     * overloading, it should be handled with care.
     */
    public static Method findMethod(Class<?> klass, String name)
    {
        for (Method m : klass.getMethods()) {
            if (name.equals(m.getName())) {
                return m;
            }
        }
        return null;
    }

    /**
     * Using a CharsetDecoder, translate the ByteBuffer into a stream, updating the buffer's position as we go.
     */
    public static String bufferToString(ByteBuffer buf, Charset cs)
    {
        CharsetDecoder decoder = Charsets.get().getDecoder(cs);
        int bufLen = (int)(buf.limit() * decoder.averageCharsPerByte());
        CharBuffer cBuf = CharBuffer.allocate(bufLen);
        CoderResult result;
        do {
            result = decoder.decode(buf, cBuf, true);
            if (result.isOverflow()) {
                cBuf = doubleBuffer(cBuf);
            }
        } while (result.isOverflow());
        do {
            result = decoder.flush(cBuf);
            if (result.isOverflow()) {
                cBuf = doubleBuffer(cBuf);
            }
        } while (result.isOverflow());

        cBuf.flip();
        return cBuf.toString();
    }

    /**
     * Like bufferToString, but read multiple buffers.
     */
    public static String bufferToString(ByteBuffer[] bufs, Charset cs)
    {
        CharsetDecoder decoder = Charsets.get().getDecoder(cs);
        int totalBytes = 0;
        for (int i = 0; i < bufs.length; i++) {
            totalBytes += (bufs[i] == null ? 0 : bufs[i].remaining());
        }
        int bufLen = (int)(totalBytes * decoder.averageCharsPerByte());
        CharBuffer cBuf = CharBuffer.allocate(bufLen);
        CoderResult result;
        for (int i = 0; i < bufs.length; i++) {
            do {
                result = decoder.decode(bufs[i], cBuf, (i == (bufs.length - 1)));
                if (result.isOverflow()) {
                    cBuf = doubleBuffer(cBuf);
                }
            } while (result.isOverflow());
        }
        do {
            result = decoder.flush(cBuf);
            if (result.isOverflow()) {
                cBuf = doubleBuffer(cBuf);
            }
        } while (result.isOverflow());

        cBuf.flip();
        return cBuf.toString();
    }

    /**
     * Using a CharsetEncoder, translate a string to a ByteBuffer, allocating a new buffer
     * as necessary.
     */
    public static ByteBuffer stringToBuffer(String str, Charset cs)
    {
        CharsetEncoder enc = Charsets.get().getEncoder(cs);
        CharBuffer chars = CharBuffer.wrap(str);
        int bufLen = (int)(chars.remaining() * enc.averageBytesPerChar());
        ByteBuffer writeBuf =  ByteBuffer.allocate(bufLen);

        CoderResult result;
        do {
            result = enc.encode(chars, writeBuf, true);
            if (result.isOverflow()) {
                writeBuf = doubleBuffer(writeBuf);
            }
        } while (result.isOverflow());
        do {
            result = enc.flush(writeBuf);
            if (result.isOverflow()) {
                writeBuf = doubleBuffer(writeBuf);
            }
        } while (result.isOverflow());

        writeBuf.flip();
        return writeBuf;
    }

    /**
     * Concatenate two byte buffers into one, updating their position. This method is very flexible
     * in that either or both, buffer may be null.
     */
    public static ByteBuffer catBuffers(ByteBuffer b1, ByteBuffer b2)
    {
        if ((b1 != null) && (b2 == null)) {
            return b1;
        }
        if ((b1 == null) && (b2 != null)) {
            return b2;
        }

        int len = (b1 == null ? 0 : b1.remaining()) +
                  (b2 == null ? 0 : b2.remaining());
        if (len == 0) {
            return null;
        }

        ByteBuffer r = ByteBuffer.allocate(len);
        if (b1 != null) {
            r.put(b1);
        }
        if (b2 != null) {
            r.put(b2);
        }
        r.flip();
        return r;
    }

    /**
     * Double the capacity of the specified buffer so that more data may be added.
     */
    public static CharBuffer doubleBuffer(CharBuffer b)
    {
        int newCap = Math.max(b.capacity() * 2, 1);
        CharBuffer d = CharBuffer.allocate(newCap);
        b.flip();
        d.put(b);
        return d;
    }

    /**
     * Double the capacity of the specified buffer so that more data may be added.
     */
    public static ByteBuffer doubleBuffer(ByteBuffer b)
    {
        int newCap = Math.max(b.capacity() * 2, 1);
        ByteBuffer d = ByteBuffer.allocate(newCap);
        b.flip();
        d.put(b);
        return d;
    }

    /**
     * Fill a ByteBuffer with zeros, useful if it has been used to store a password or something.
     */
    public static void zeroBuffer(ByteBuffer b)
    {
        b.clear();
        while (b.hasRemaining()) {
            b.put((byte)0);
        }
        b.clear();
    }

    /**
     * Make a duplicate of a ByteBuffer.
     */
    public static ByteBuffer duplicateBuffer(ByteBuffer b)
    {
        ByteBuffer ret = ByteBuffer.allocate(b.remaining());
        ByteBuffer tmp = b.duplicate();
        ret.put(tmp);
        ret.flip();
        return ret;
    }

    /**
     * Remove leading and trailing strings from a quoted string that has both leading and trailing quotes on it.
     */
    public static String unquote(String s)
    {
        Matcher m = DOUBLE_QUOTED.matcher(s);
        if (m.matches()) {
            return m.group(1);
        }
        Matcher m2 = SINGLE_QUOTED.matcher(s);
        if (m2.matches()) {
            return m2.group(1);
        }
        return s;
    }
}
