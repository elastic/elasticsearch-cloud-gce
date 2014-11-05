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

package org.elasticsearch.cloud.gce;

import com.google.api.services.storage.Storage;
import org.elasticsearch.common.blobstore.BlobMetaData;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.component.LifecycleComponent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;

public interface GoogleCloudStorageService extends LifecycleComponent<GoogleCloudStorageService> {

    static public final class Fields {
        public static final String REPOSITORIES_GS = "repositories.gcs.";
        public static final String PROJECT_ID = "project_id";
        public static final String BUCKET = "bucket";
        public static final String BUCKET_LOCATION = "location";
        public static final String CHUNK_SIZE = "chunk_size";
        public static final String COMPRESS = "compress";
        public static final String BASE_PATH = "base_path";
        public static final String CREDENTIALS_FILE = "credentials_file";
        public static final String APPLICATION_NAME = "application_name";
        public static final String PROXY_HOST = "proxy_host";
        public static final String PROXY_PORT = "proxy_port";
    }

    /**
     * Return true if the given bucket exists
     *
     * @param bucketName
     * @return
     */
    boolean doesBucketExist(String bucketName) throws IOException;

    /**
     * Creates a new bucket.
     *
     * @param projectName
     * @param bucketName
     * @param location
     * @throws IOException
     */
    void createBucket(String projectName, String bucketName, String location) throws IOException;

    /**
     * Deletes all blobs in a given bucket
     *
     * @param bucketName
     * @param path
     */
    void deleteBlobs(String bucketName, String path) throws IOException;

    /**
     * Returns true if the blob exists
     *
     * @param bucketName
     * @param blobName
     * @return
     */
    boolean blobExists(String bucketName, String blobName) throws IOException;

    /**
     * Deletes a blob in a given bucket
     *
     * @param bucketName
     * @param blobName
     * @throws IOException
     */
    void deleteBlob(String bucketName, String blobName) throws IOException;

    /**
     * Returns an {@link java.io.InputStream} for a given blob
     *
     * @param bucketName
     * @param blobName
     * @return
     */
    InputStream getInputStream(String bucketName, String blobName) throws IOException;

    /**
     * Returns an {@link java.io.OutputStream} that can be used to write blobs.
     *
     * @param  executor
     * @param bucketName
     * @param blobName
     * @return
     */
    OutputStream getOutputStream(Executor executor, String bucketName, String blobName) throws IOException;

    /**
     * List all blobs in a given bucket which have a prefix
     *
     * @param bucketName
     * @param path
     * @param prefix
     * @return
     */
    ImmutableMap<String,BlobMetaData> listBlobsByPrefix(String bucketName, String path, String prefix) throws IOException;

    /**
     * Prepare an insert/upload request.
     *
     * @param bucketName
     * @param blobName
     * @param input
     * @return
     * @throws IOException
     */
    Storage.Objects.Insert prepareInsert(String bucketName, String blobName, InputStream input) throws IOException;

}
