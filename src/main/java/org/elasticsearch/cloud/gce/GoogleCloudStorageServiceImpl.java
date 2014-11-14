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

import com.google.api.client.googleapis.GoogleUtils;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.StorageScopes;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.Objects;
import com.google.api.services.storage.model.StorageObject;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchIllegalArgumentException;
import org.elasticsearch.cloud.gce.blobstore.ConcurrentUpload;
import org.elasticsearch.cloud.gce.blobstore.GoogleCloudStorageConcurrentUpload;
import org.elasticsearch.cloud.gce.blobstore.GoogleCloudStorageOutputStream;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.blobstore.BlobMetaData;
import org.elasticsearch.common.blobstore.support.PlainBlobMetaData;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.concurrent.Executor;

import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

/**
 * See https://code.google.com/p/google-api-java-client/source/browse/storage-cmdline-sample/src/main/java/com/google/api/services/samples/storage/cmdline/StorageSample.java?repo=samples#477
 * See https://github.com/pliablematter/simple-cloud-storage/blob/master/src/main/java/com/pliablematter/cloudstorage/CloudStorage.java
 */
public class GoogleCloudStorageServiceImpl extends AbstractLifecycleComponent<GoogleCloudStorageService> implements GoogleCloudStorageService {

    private GoogleCredential credentials;

    private Storage client;

    @Inject
    public GoogleCloudStorageServiceImpl(Settings settings) throws IOException, GeneralSecurityException {
        super(settings);

        this.credentials = loadCredentials();

        if (logger.isDebugEnabled()) {
            logger.debug("using service account id [{}] with key [{}]", credentials.getServiceAccountId(), credentials.getServiceAccountPrivateKey());
        }

        // Now we can set up other large objects

        // Sets up the HTTP transport
        NetHttpTransport.Builder httpTransport = new NetHttpTransport.Builder();
        httpTransport.trustCertificates(GoogleUtils.getCertificateTrustStore());

        String proxyHost = settings.get(Fields.REPOSITORIES_GS + Fields.PROXY_HOST);
        if (proxyHost != null) {
            String portString = settings.get(Fields.REPOSITORIES_GS + Fields.PROXY_PORT, "80");
            Integer proxyPort;
            try {
                proxyPort = Integer.parseInt(portString, 10);
            } catch (NumberFormatException ex) {
                throw new ElasticsearchIllegalArgumentException("The configured proxy port value [" + portString + "] is invalid", ex);
            }
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
            httpTransport.setProxy(proxy);
        }

        String applicationName = settings.get(Fields.REPOSITORIES_GS + Fields.APPLICATION_NAME, "elasticsearch-google-cloud-storage-service");

        // Instanciates the Google Cloud Storage client
        Storage.Builder storage = new Storage.Builder(httpTransport.build(), JacksonFactory.getDefaultInstance(), credentials);
        storage.setApplicationName(applicationName);

        client = storage.build();
        logger.debug("Google Cloud Storage client initialized");
    }

    private GoogleCredential loadCredentials() throws IOException {
        String credentialsFilePath = settings.get(Fields.REPOSITORIES_GS + Fields.CREDENTIALS_FILE);
        if (credentialsFilePath == null) {
            throw new ElasticsearchIllegalArgumentException("The configured " + Fields.CREDENTIALS_FILE + " value [" + credentialsFilePath + "] is invalid");
        }
        logger.debug("Google Cloud Storage credentials file set to [{}]", credentialsFilePath);

        try (InputStream is = new FileInputStream(new File(credentialsFilePath))) {
            logger.debug("loading credentials from [{}]", credentialsFilePath);
            return GoogleCredential.fromStream(is)
                    .createScoped(Collections.singleton(StorageScopes.DEVSTORAGE_FULL_CONTROL));
        } catch (IOException e) {
            logger.error("Cannot load Google Cloud Storage credentials from file [{}]", credentialsFilePath, e.getMessage());
            throw e;
        }
    }

    @Override
    public boolean doesBucketExist(String bucketName) throws IOException {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("checking if bucket [{}] exists", bucketName);
            }
            Bucket bucket = client.buckets().get(bucketName).execute();
            if (bucket != null) {
                return Strings.hasText(bucket.getId());
            }
        } catch (GoogleJsonResponseException e) {
            if ((e.getStatusCode() == HTTP_FORBIDDEN) || (e.getStatusCode() == HTTP_NOT_FOUND)) {
                return false;
            }
            throw e;
        }
        return false;
    }

    @Override
    public void createBucket(String projectName, String bucketName, String location) throws IOException {
        Bucket bucket = new Bucket();
        bucket.setName(bucketName);
        if (location != null) {
            bucket.setLocation(location);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("creating bucket [{}] for project [{}]", bucketName, projectName);
        }
        client.buckets().insert(projectName, bucket).execute();
    }

    @Override
    public boolean blobExists(String bucketName, String blobName) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("checking if blob [{}] exists in bucket [{}]", blobName, bucketName);
        }
        StorageObject blob = client.objects().get(bucketName, blobName).execute();
        return blob != null;
    }

    @Override
    public void deleteBlob(String bucketName, String blobName) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("deleting blob [{}] in bucket [{}]", blobName, bucketName);
        }
        client.objects().delete(bucketName, blobName).execute();
    }

    @Override
    public InputStream getInputStream(String bucketName, String blobName) throws IOException {
        Storage.Objects.Get object = client.objects().get(bucketName, blobName);
        return object.executeMediaAsInputStream();
    }

    @Override
    public OutputStream getOutputStream(Executor executor, String bucketName, String blobName, int bufferSizeInBytes) throws IOException {
        // The concurrent upload does buffering internally
        ConcurrentUpload upload = prepareConcurrentUpload(bucketName, blobName);
        // ConcurrentUpload is executed in a dedicated thread
        return new GoogleCloudStorageOutputStream(executor, upload, bufferSizeInBytes);
    }

    protected <T> T prepareConcurrentUpload(String bucketName, String blobName) {
        return (T) new GoogleCloudStorageConcurrentUpload(this, bucketName, blobName);
    }

    @Override
    public Storage.Objects.Insert prepareInsert(String bucketName, String blobName, InputStream input) throws IOException {
        InputStreamContent streamContent =  new InputStreamContent("application/octet-stream", input);
        Storage.Objects.Insert insert = client.objects().insert(bucketName, null, streamContent);
        insert.setName(blobName);
        return insert;
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
    public ImmutableMap<String, BlobMetaData> listBlobsByPrefix(final String bucketName, final String path, final String prefix) throws IOException {
        final ImmutableMap.Builder<String, BlobMetaData> blobsBuilder = ImmutableMap.builder();
        forEachStorageObject(bucketName, path, prefix, new StorageObjectCallback() {
            @Override
            void doWithStorageObject(StorageObject object) {
                String name = object.getName().substring(path.length());
                blobsBuilder.put(name, new PlainBlobMetaData(name, object.getSize().longValue()));
            }
        });
        return blobsBuilder.build();
    }

    @Override
    public void deleteBlobs(String bucketName, String path) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("delete all blobs of bucket [{}] and path [{}]",
                    bucketName, path);
        }

        forEachStorageObject(bucketName, path, null, new StorageObjectCallback() {
            @Override
            void doWithStorageObject(StorageObject object) throws IOException {
                client.objects().delete(object.getBucket(), object.getName()).execute();
            }
        });
    }

    private void forEachStorageObject(String bucketName, String path, String prefix, StorageObjectCallback callback) throws IOException {
        if (callback == null) {
            return;
        }
        Storage.Objects.List list = client.objects().list(bucketName);
        if (prefix != null) {
            list.setPrefix(path + prefix);
        } else {
            list.setPrefix(path);
        }
        list.setMaxResults(100L);
        Objects objects = list.execute();

        while ((objects.getItems() != null) && (!objects.getItems().isEmpty())) {

            for (StorageObject object : objects.getItems()) {
                callback.doWithStorageObject(object);
            }

            String nextPageToken = objects.getNextPageToken();
            if (nextPageToken == null) {
                break;
            }
            list.setPageToken(nextPageToken);

            // Fetch next page of objects
            objects = list.execute();
        }
    }

    private abstract class StorageObjectCallback {
        abstract void doWithStorageObject(StorageObject object) throws IOException;
    }
}
