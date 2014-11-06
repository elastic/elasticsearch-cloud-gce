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

import org.elasticsearch.common.Preconditions;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.Executor;


public class GoogleCloudStorageOutputStream extends OutputStream {

    /**
     * The PipedOutputStream is used by the caller to write data that are directly
     * piped to the PipedInputStream.
     */
    private PipedOutputStream output = new PipedOutputStream();

    /**
     * The PipedInputStream is used by a ConcurrentUpload object to read the data to send
     * to Google Cloud Storage.
     */
    private PipedInputStream input;

    /**
     * A ConcurrentUpload represents an upload request that is executed in the background.
     * This object reads the data to send from a PipedInputStream.
     */
    private ConcurrentUpload upload;

    /**
     * Executor used to executes the concurrent upload
     */
    private Executor executor;

    public GoogleCloudStorageOutputStream(Executor executor, ConcurrentUpload upload) {
        Preconditions.checkNotNull(executor, "An executor must be provided");
        this.executor = executor;
        Preconditions.checkNotNull(upload, "An upload request must be provided");
        this.upload = upload;
    }

    @Override
    public void write(int b) throws IOException {
        if (input == null) {
            // Connects output -> input
            input = new PipedInputStream(output);

            // Connects input -> concurrent upload
            upload.initializeUpload(input);

            // Starts the concurrent upload
            executor.execute(upload);
        }

        // Rethrow exception if something wrong happen
        checkForConcurrentUploadErrors();

        output.write(b);
    }

    @Override
    public void close() throws IOException {
        if (output != null) {
            try {
                output.close();
            } catch (IOException e) {
                // Ignore
            }
        }

        if (input != null) {
            try {
                // Waits for the upload request to complete
                upload.waitForCompletion();

                // Rethrow exception if something wrong happen
                checkForConcurrentUploadErrors();

            } finally {
                output = null;
                input = null;
                upload = null;
            }
        }
    }

    /**
     * Check the status of the ConcurrentUpload and throws an exception if something wrong happen
     *
     * @throws IOException
     */
    protected void checkForConcurrentUploadErrors() throws IOException {
        if ((upload != null) && (upload.getException() != null)) {
            throw new IOException("Detected exception while uploading blob", upload.getException());
        }
    }
}
