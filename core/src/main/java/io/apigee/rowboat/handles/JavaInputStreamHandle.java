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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

/**
 * This class implements the generic "handle" pattern with a Java input or output stream. Different Node
 * versions wire it up to a specific handle type depending on the specific JavaScript contract required.
 * This class basically does the async I/O on the handle.
 */

public class JavaInputStreamHandle
    extends AbstractHandle
{
    private static final int READ_BUFFER_SIZE = 16392;

    private final InputStream in;

    private Future<?> readTask;
    private volatile boolean reading;

    public JavaInputStreamHandle(InputStream in, NodeRuntime runtime)
    {
        super(runtime);
        this.in = in;
        this.runtime = runtime;
    }

    @Override
    public void startReading(BiConsumer<Object, ByteBuffer> cb)
    {
        if (reading) {
            return;
        }

        // Pin explicitly before starting to read -- these handles don't necessarily keep the server open,
        // but when we are reading many problems are averted when we do. Note that we don't do this for
        // network handles, but instead "pin" when the socket is first created.
        reading = true;
        runtime.pin();
        readTask = runtime.getUnboundedPool().submit(() -> readLoop(cb));
    }

    protected void readLoop(BiConsumer<Object, ByteBuffer> cb)
    {
        byte[] readBuf = new byte[READ_BUFFER_SIZE];
        try {
            int count = 0;
            while (reading && (count >= 0)) {
                count = in.read(readBuf);
                if (count > 0) {
                    ByteBuffer buf = ByteBuffer.allocate(count);
                    buf.put(readBuf, 0, count);
                    buf.flip();
                    submitReadCallback(null, buf, cb);
                }
            }
            if (count < 0) {
                submitReadCallback(Constants.EOF, null, cb);
            }

        } catch (InterruptedIOException iee) {
            // Nothing special to do, since we were asked to stop reading
        } catch (EOFException eofe) {
            submitReadCallback(Constants.EOF, null, cb);
        } catch (IOException ioe) {
            String err =
                ("Stream Closed".equalsIgnoreCase(ioe.getMessage()) ? Constants.EOF : Constants.EIO);
            submitReadCallback(err, null, cb);
        }
    }

    @Override
    public void stopReading()
    {
        if (reading) {
            runtime.unPin();
            reading = false;
        }
        if (readTask != null) {
            readTask.cancel(true);
        }
    }

    @Override
    public void close()
    {
        stopReading();
        try {
            in.close();
        } catch (IOException ignore) {
        }
    }
}
