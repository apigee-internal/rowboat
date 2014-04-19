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
import io.apigee.rowboat.ScriptTask;
import io.apigee.rowboat.Utils;
import jdk.nashorn.api.scripting.JSObject;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * This class is the base of all handle types that are used for I/O in Trireme.
 */

public abstract class AbstractHandle
{
    protected NodeRuntime runtime;

    protected AbstractHandle(NodeRuntime runtime)
    {
        this.runtime = runtime;
    }

    public int write(ByteBuffer buf, Object context, JSObject onWriteComplete)
    {
        throw new IllegalStateException("Handle not capable of writing");
    }

    public int write(String s, Charset cs, Object context, JSObject onWriteComplete)
    {
        // Convert the string to a buffer, which may involve some re-allocating and copying if the
        // string has many long multi-byte characters.
        // An alternative would be to use CharsetEncoder directly here and call "write" for every
        // chunk of data that it produces. This would optimize for allocating and copying ByteBuffers
        // but it would result in more "write" calls to the socket.
        ByteBuffer buf = Utils.stringToBuffer(s, cs);
        return write(buf, context, onWriteComplete);
    }

    public int getWritesOutstanding()
    {
        return 0;
    }

    public void startReading(Object context, JSObject onReadComplete)
    {
        throw new IllegalStateException("Handle not capable of reading");
    }

    public void stopReading()
    {
        throw new IllegalStateException("Handle not capable of reading");
    }

    public abstract void close();

    protected void submitReadCallback(final Object context, final String err,
                                    final ByteBuffer buf, final JSObject onReadComplete)
    {
        runtime.enqueueTask(new ScriptTask() {
            @Override
            public void execute()
            {
                Object jsBuf = null;
                if (buf != null) {
                    // TODO!
                    throw new AssertionError("We should create a buffer here!");
                }
                onReadComplete.call(null, context, err, jsBuf, false);
            }
        });
    }
}
