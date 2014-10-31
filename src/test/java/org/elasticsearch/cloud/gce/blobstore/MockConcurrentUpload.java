/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.cloud.gce.blobstore;

import org.elasticsearch.common.io.Streams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MockConcurrentUpload implements ConcurrentUpload<byte[]> {

    private InputStream in = null;
    private ByteArrayOutputStream out = new ByteArrayOutputStream();

    private boolean completed = false;
    private Exception exception;

    private final CountDownLatch done = new CountDownLatch(1);

    @Override
    public void initializeUpload(InputStream inputStream) throws IOException {
        this.in = inputStream;
    }

    @Override
    public void waitForCompletion() {
        while ((done.getCount() > 0) && (exception == null)) {
            try {
                completed = done.await(50, TimeUnit.MILLISECONDS);
                if (completed) {
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isCompleted() {
        return completed;
    }

    @Override
    public byte[] getUploadedObject() {
        return out.toByteArray();
    }

    @Override
    public Exception getException() {
        return exception;
    }

    @Override
    public void run() {
        try {
            Streams.copy(in, out);
            completed = true;
        } catch (IOException e) {
            exception = e;
        } finally {
            done.countDown();
        }
    }
}
