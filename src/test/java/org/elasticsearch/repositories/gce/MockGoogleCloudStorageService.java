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

package org.elasticsearch.repositories.gce;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.StorageObject;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.cloud.gce.GoogleCloudStorageService;
import org.elasticsearch.common.blobstore.BlobMetaData;
import org.elasticsearch.common.blobstore.support.PlainBlobMetaData;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;

/**
 * In memory storage for unit tests
 */
public class MockGoogleCloudStorageService extends AbstractLifecycleComponent<GoogleCloudStorageService>
        implements GoogleCloudStorageService {

    protected Map<String, Set<String>> buckets = new ConcurrentHashMap<>();
    protected Map<String, ByteArrayOutputStream> blobs = new ConcurrentHashMap<>();

    /**
     * This is the OutputStream in which the request implemented in MockStorage.MockObjects.MockInsert
     */
    private ByteArrayOutputStream requestOutputStream;

    @Inject
    public MockGoogleCloudStorageService(Settings settings, ByteArrayOutputStream requestOutputStream) {
        super(settings);
        this.requestOutputStream = requestOutputStream;
    }

    private String key(String bucketName, String blob) {
        return String.format("#%s#%s", bucketName, blob);
    }

    private String reverseKey(String path) {
        if (path.contains("#")) {
            return path.substring(path.lastIndexOf('#') + 1);
        }
        return path;
    }

    @Override
    protected void doStart() throws ElasticsearchException {

    }

    @Override
    protected void doStop() throws ElasticsearchException {

    }

    @Override
    protected void doClose() throws ElasticsearchException {

    }

    @Override
    public boolean doesBucketExist(String bucketName) throws IOException {
        for (Set<String> b : buckets.values()) {
            if (b.contains(bucketName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void createBucket(String projectName, String bucketName, String location) throws IOException {
        Set<String> projectBuckets = buckets.get(projectName);
        if (projectBuckets == null) {
            projectBuckets = new CopyOnWriteArraySet<>();
            projectBuckets.add(bucketName);
            buckets.put(projectName, projectBuckets);
        } else {
            if (projectBuckets.contains(bucketName)) {
                throw new FileAlreadyExistsException("Mock service indicates that bucket already exists");
            }
        }
    }

    @Override
    public void deleteBlobs(String bucketName, String path) throws IOException {
        Set<String> markAsDeleted = new HashSet<>();

        for (String blobName : blobs.keySet()) {
            if (blobName.startsWith(path)) {
                markAsDeleted.add(blobName);
            }
        }
        if (!markAsDeleted.isEmpty()) {
            for (String delete : markAsDeleted) {
                blobs.remove(delete);
            }
        }
    }

    @Override
    public boolean blobExists(String bucketName, String blobName) throws IOException {
        return blobs.containsKey(key(bucketName, blobName));
    }

    @Override
    public void deleteBlob(String bucketName, String blobName) throws IOException {
        if (!blobs.containsKey(key(bucketName, blobName))) {
            throw new FileNotFoundException("Mock service indicates that bucket does not exists");
        }

    }

    @Override
    public InputStream getInputStream(String bucketName, String blobName) throws IOException {
        if (!blobs.containsKey(key(bucketName, blobName))) {
            throw new FileNotFoundException("Mock service indicates that blob does not exists");
        }
        return new ByteArrayInputStream(blobs.get(key(bucketName, blobName)).toByteArray());
    }

    @Override
    public OutputStream getOutputStream(Executor executor, String bucketName, String blobName, int bufferSize) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blobs.put(key(bucketName, blobName), outputStream);
        return outputStream;
    }

    @Override
    public ImmutableMap<String, BlobMetaData> listBlobsByPrefix(String bucketName, String path, String prefix) throws IOException {
        ImmutableMap.Builder<String, BlobMetaData> blobsBuilder = ImmutableMap.builder();
        for (String blobKey : blobs.keySet()) {
            String blobName = reverseKey(blobKey);
            if (blobName.startsWith(path + (prefix != null ? prefix : ""))) {
                blobsBuilder.put(blobName, new PlainBlobMetaData(blobName, blobs.get(blobKey).size()));
            }
        }
        return blobsBuilder.build();
    }

    @Override
    public Storage.Objects.Insert prepareInsert(String bucketName, String blobName, InputStream input) throws IOException {
        try {
            InputStreamContent streamContent =  new InputStreamContent("application/octet-stream", input);
            return new MockStorage(requestOutputStream).objects().insert(bucketName, new StorageObject().setName(blobName), streamContent);
        } catch (GeneralSecurityException gse) {
            // We are mocking a class, we don't really care about this exception.
            throw new IOException(gse);
        }
    }
}
