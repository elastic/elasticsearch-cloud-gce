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

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import org.elasticsearch.cloud.gce.GoogleCloudStorageService;
import org.elasticsearch.common.blobstore.BlobContainer;
import org.elasticsearch.common.blobstore.BlobPath;
import org.elasticsearch.common.blobstore.BlobStore;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.settings.Settings;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;

import static java.net.HttpURLConnection.HTTP_CONFLICT;

/**
 * BlobStore implementation that uses Google Cloud Storage service as a backend.
 */
public class GoogleCloudStorageBlobStore extends AbstractComponent implements BlobStore {

    private final GoogleCloudStorageService client;

    private final String bucket;

    public GoogleCloudStorageBlobStore(Settings settings, GoogleCloudStorageService client, String projectName, String bucketName, String bucketLocation) throws IOException {
        super(settings);
        this.client = client;
        this.bucket = bucketName;

        try {
            if (!client.doesBucketExist(bucketName)) {
                client.createBucket(projectName, bucketName, bucketLocation);
            }
        } catch (GoogleJsonResponseException e) {
            logger.error("exception occurs when checking for bucket existence", e);
            GoogleJsonError error = e.getDetails();
            if ((error != null) && (error.getCode() == HTTP_CONFLICT)) {
                String message = error.getMessage();
                if (message != null) {
                    if (message.contains("You already own this bucket.")
                            || message.contains("Sorry, that name is not available. Please try a different one.")) {
                        throw new FileAlreadyExistsException("Bucket [" + bucketName + "] cannot be created: " + message);
                    }
                }
            }
            throw e;
        }
    }

    public GoogleCloudStorageService client() {
        return client;
    }

    public String bucket() {
        return bucket;
    }

    @Override
    public BlobContainer blobContainer(BlobPath path) {
        return new GoogleCloudStorageBlobContainer(path, this);
    }

    @Override
    public void delete(BlobPath path) {
        String keyPath = path.buildAsString("/");
        if (!keyPath.isEmpty()) {
            keyPath = keyPath + "/";
        }

        try {
            client.deleteBlobs(bucket, keyPath);
        } catch (IOException e) {
            logger.warn("can not remove [{}] in bucket [{}]: {}", keyPath, bucket, e.getMessage());
        }
    }

    @Override
    public void close() {

    }
}
