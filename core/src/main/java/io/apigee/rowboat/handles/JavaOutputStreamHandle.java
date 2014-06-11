/*
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
package io.apigee.rowboat.handles;

import io.apigee.rowboat.NodeRuntime;
import io.apigee.rowboat.internal.Constants;
import jdk.nashorn.api.scripting.JSObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * This class implements the generic "handle" pattern with a Java input or output stream. Different Node
 * versions wire it up to a specific handle type depending on the specific JavaScript contract required.
 * This class basically does the async I/O on the handle.
 */

public class JavaOutputStreamHandle
    extends AbstractHandle
{
    private final OutputStream out;

    public JavaOutputStreamHandle(OutputStream out, NodeRuntime runtime)
    {
        super(runtime);
        this.out = out;
    }

    @Override
    public int write(ByteBuffer buf, Object context, WriteCompleteCallback cb)
    {
        try {
            int len = buf.remaining();
            if (buf.hasArray()) {
                out.write(buf.array(), buf.arrayOffset() + buf.position(), len);
                buf.position(buf.position() + len);
            } else {
                byte[] tmp = new byte[len];
                buf.get(tmp);
                out.write(tmp);
            }
            cb.complete(context, null, true);
            return len;

        } catch (IOException ioe) {
            cb.complete(context, Constants.EIO, true);
            return 0;
        }
    }

    @Override
    public void close()
    {
        try {
            out.close();
        } catch (IOException ignore) {
        }
    }
}
