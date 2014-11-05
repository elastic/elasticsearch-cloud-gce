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

import org.elasticsearch.cloud.gce.GoogleCloudStorageService;
import org.elasticsearch.cloud.gce.blobstore.GoogleCloudStorageBlobStore;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.blobstore.BlobPath;
import org.elasticsearch.common.blobstore.BlobStore;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.index.snapshots.IndexShardRepository;
import org.elasticsearch.repositories.RepositoryException;
import org.elasticsearch.repositories.RepositoryName;
import org.elasticsearch.repositories.RepositorySettings;
import org.elasticsearch.repositories.blobstore.BlobStoreRepository;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.elasticsearch.cloud.gce.GoogleCloudStorageService.Fields.*;


/**
 * Repository implementation that uses Google Cloud Storage service as a backend.
 */
public class GoogleCloudStorageRepository extends BlobStoreRepository {

    public final static String TYPE = "gcs";

    private final GoogleCloudStorageBlobStore blobStore;

    private final BlobPath basePath;

    private ByteSizeValue chunkSize;

    private boolean compress;

    @Inject
    public GoogleCloudStorageRepository(RepositoryName name, RepositorySettings repositorySettings, IndexShardRepository indexShardRepository, GoogleCloudStorageService googleCloudStorageService) throws IOException {
        super(name.name(), repositorySettings, indexShardRepository);

        String projectId = repositorySettings.settings().get(PROJECT_ID, componentSettings.get(PROJECT_ID, settings.get(REPOSITORIES_GS + PROJECT_ID, settings.get("cloud.gce." + PROJECT_ID))));
        if (!Strings.hasText(projectId)) {
            throw new RepositoryException(name.name(), "No project id defined for Google Cloud Storage repository");
        }

        String bucketName = repositorySettings.settings().get(BUCKET, componentSettings.get(BUCKET, settings.get(REPOSITORIES_GS + BUCKET)));
        if (!Strings.hasText(bucketName)) {
            throw new RepositoryException(name.name(), "No bucket defined for Google Cloud Storage repository");
        }

        String bucketLocation = repositorySettings.settings().get(BUCKET_LOCATION, componentSettings.get(BUCKET_LOCATION));
        if (bucketLocation == null) {
            // Checks for a default location
            String defaultLocation = repositorySettings.settings().get(REPOSITORIES_GS + BUCKET_LOCATION, settings.get(REPOSITORIES_GS + BUCKET_LOCATION));
            if (defaultLocation != null) {
                defaultLocation = defaultLocation.toUpperCase(Locale.ENGLISH);
                if ("US".equals(defaultLocation)) {
                    // Default location - setting location to null
                    bucketLocation = null;
                } else if ("EU".equals(defaultLocation)) {
                    bucketLocation = "EU";
                } else if ("ASIA".equals(defaultLocation)) {
                    bucketLocation = "ASIA";
                }
            }
        }

        this.chunkSize = repositorySettings.settings().getAsBytesSize(CHUNK_SIZE, componentSettings.getAsBytesSize(CHUNK_SIZE, new ByteSizeValue(100, ByteSizeUnit.MB)));
        this.compress = repositorySettings.settings().getAsBoolean(COMPRESS, componentSettings.getAsBoolean(COMPRESS, false));

        String basePath = repositorySettings.settings().get(BASE_PATH, null);
        if (Strings.hasLength(basePath)) {
            // Remove starting / if any
            basePath = Strings.trimLeadingCharacter(basePath, '/');
            BlobPath path = new BlobPath();
            for (String elem : Strings.splitStringToArray(basePath, '/')) {
                path = path.add(elem);
            }
            this.basePath = path;
        } else {
            this.basePath = BlobPath.cleanPath();
        }

        int concurrentStreams = repositorySettings.settings().getAsInt("concurrent_streams", componentSettings.getAsInt("concurrent_streams", 5));
        ExecutorService concurrentStreamPool = EsExecutors.newScaling(1, concurrentStreams, 5, TimeUnit.SECONDS, EsExecutors.daemonThreadFactory(settings, "[s3_stream]"));

        logger.debug("using  projet id [{}], bucket [{}], location [{}], base_path [{}], chunk_size [{}], compress [{}]",
                projectId, bucketName, bucketLocation, basePath, chunkSize, compress);
        this.blobStore = new GoogleCloudStorageBlobStore(settings, concurrentStreamPool, googleCloudStorageService, projectId, bucketName, bucketLocation);
    }


    @Override
    protected BlobStore blobStore() {
        return blobStore;
    }

    @Override
    protected BlobPath basePath() {
        return basePath;
    }

    @Override
    protected boolean isCompress() {
        return compress;
    }

    @Override
    protected ByteSizeValue chunkSize() {
        return chunkSize;
    }
}
