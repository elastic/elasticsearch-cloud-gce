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
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.blobstore.BlobMetaData;
import org.elasticsearch.common.blobstore.BlobPath;
import org.elasticsearch.common.blobstore.BlobStoreException;
import org.elasticsearch.common.blobstore.support.AbstractBlobContainer;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

public class GoogleCloudStorageBlobContainer extends AbstractBlobContainer {

    protected final ESLogger logger = ESLoggerFactory.getLogger(GoogleCloudStorageBlobContainer.class.getName());

    protected final GoogleCloudStorageBlobStore blobStore;

    protected final String keyPath;

    public GoogleCloudStorageBlobContainer(BlobPath path, GoogleCloudStorageBlobStore blobStore) {
        super(path);
        this.blobStore = blobStore;

        String keyPath = path.buildAsString("/");
        if (!keyPath.isEmpty()) {
            keyPath = keyPath + "/";
        }
        this.keyPath = keyPath;
    }

    @Override
    public boolean blobExists(String blobName) {
        try {
            return blobStore.client().blobExists(blobStore.bucket(), buildKey(blobName));
        } catch (GoogleJsonResponseException e) {
            GoogleJsonError error = e.getDetails();
            if ((e.getStatusCode() == HTTP_NOT_FOUND) || ((error != null) && (error.getCode() == HTTP_NOT_FOUND))) {
                return false;
            }
        } catch (IOException e) {
            throw new BlobStoreException("failed to check if blob exists", e);
        }
        return false;
    }

    @Override
    public InputStream openInput(String blobName) throws IOException {
        try {
            return blobStore.client().getInputStream(blobStore.bucket(), buildKey(blobName));
        } catch (GoogleJsonResponseException e) {
            GoogleJsonError error = e.getDetails();
            if ((e.getStatusCode() == HTTP_NOT_FOUND) || ((error != null) && (error.getCode() == HTTP_NOT_FOUND))) {
                throw new FileNotFoundException(e.getMessage());
            }
            throw e;
        }
    }

    @Override
    public OutputStream createOutput(String blobName) throws IOException {
        return blobStore.client().getOutputStream(blobStore.executor(), blobStore.bucket(), buildKey(blobName), blobStore.bufferSizeInBytes());
    }

    @Override
    public void deleteBlob(String blobName) throws IOException {
        blobStore.client().deleteBlob(blobStore.bucket(), buildKey(blobName));
    }

    @Override
    public ImmutableMap<String, BlobMetaData> listBlobsByPrefix(@Nullable String prefix) throws IOException {
        return blobStore.client().listBlobsByPrefix(blobStore.bucket(), keyPath, prefix);
    }

    @Override
    public ImmutableMap<String, BlobMetaData> listBlobs() throws IOException {
        return listBlobsByPrefix(null);
    }

    protected String buildKey(String blobName) {
        return keyPath + blobName;
    }
}
