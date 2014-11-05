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

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.StorageObject;
import org.apache.lucene.util.IOUtils;
import org.elasticsearch.cloud.gce.GoogleCloudStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of ConcurrentUpload for Google Cloud Storage. This class executes a
 * Storage.Objects.Insert request that reads an InputStream (wrapped in an InputStreamContent)
 * and uploads it in multiple chunk_size
 */
public class GoogleCloudStorageConcurrentUpload implements ConcurrentUpload<StorageObject> {

    /**
     * Only 1 concurrent request is allowed by the GCS API
     */
    private final CountDownLatch done = new CountDownLatch(1);

    private GoogleCloudStorageService client;

    private String bucketName;
    private String blobName;

    private InputStream input;

    private Storage.Objects.Insert request;
    private StorageObject response;

    private Exception exception;

    public GoogleCloudStorageConcurrentUpload(GoogleCloudStorageService client, String bucketName, String blobName) {
        this.client = client;
        this.bucketName = bucketName;
        this.blobName = blobName;
    }

    @Override
    public void initializeUpload(InputStream inputStream) throws IOException {
        this.input = inputStream;
        request = client.prepareInsert(bucketName, blobName, inputStream);
    }

    @Override
    public void run() {
        try {
            response = request.execute();
        } catch (Exception e) {
            exception = e;

        } finally {
            done.countDown();
            IOUtils.closeWhileHandlingException(input);
        }
    }

    @Override
    public void waitForCompletion() {
        try {
            done.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exception = e;
        }
    }

    @Override
    public boolean isCompleted() {
        return  (request.getMediaHttpUploader() != null)
                && (request.getMediaHttpUploader().getUploadState() != null)
                && (request.getMediaHttpUploader().getUploadState().equals(MediaHttpUploader.UploadState.MEDIA_COMPLETE));
    }

    @Override
    public StorageObject getUploadedObject() {
        return response;
    }

    @Override
    public Exception getException() {
        return exception;
    }


}
