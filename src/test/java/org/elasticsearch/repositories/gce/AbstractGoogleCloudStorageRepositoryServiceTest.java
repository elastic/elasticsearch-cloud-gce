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

import org.elasticsearch.cloud.gce.AbstractGceTest;
import org.elasticsearch.cloud.gce.GoogleCloudStorageService;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.PluginsService;
import org.elasticsearch.repositories.RepositoryMissingException;
import org.elasticsearch.test.store.MockDirectoryHelper;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;

public abstract class AbstractGoogleCloudStorageRepositoryServiceTest extends AbstractGceTest {

    protected String basePath;

    @Before
    public final void wipeBefore() {
        wipeRepositories();
        basePath = "repo-" + randomInt();
        cleanRepositoryFiles(basePath);
    }

    @After
    public final void wipeAfter() {
        wipeRepositories();
        cleanRepositoryFiles(basePath);
    }


    @Override
    public Settings indexSettings() {
        // During restore we frequently restore index to exactly the same state it was before, that might cause the same
        // checksum file to be written twice during restore operation
        return ImmutableSettings.builder().put(super.indexSettings())
                .put(MockDirectoryHelper.RANDOM_PREVENT_DOUBLE_WRITE, false)
                .put(MockDirectoryHelper.RANDOM_NO_DELETE_OPEN_FILE, false)
                .put("plugins." + PluginsService.LOAD_PLUGIN_FROM_CLASSPATH, true)
                .build();
    }

    /**
     * Deletes repositories, supports wildcard notation.
     */
    public static void wipeRepositories(String... repositories) {
        // if nothing is provided, delete all
        if (repositories.length == 0) {
            repositories = new String[]{"*"};
        }
        for (String repository : repositories) {
            try {
                client().admin().cluster().prepareDeleteRepository(repository).execute().actionGet();
            } catch (RepositoryMissingException ex) {
                // ignore
            }
        }
    }

    /**
     * Deletes content of the repository files in the bucket
     */
    public void cleanRepositoryFiles(String path) {
        String defaultBucket = internalCluster().getInstance(Settings.class).get("repositories.gcs.bucket");
        logger.info("--> remove blobs in bucket [{}]", defaultBucket);
        GoogleCloudStorageService client = internalCluster().getInstance(GoogleCloudStorageService.class);
        try {
            client.deleteBlobs(defaultBucket, path);
        } catch (IOException e) {
            logger.error("exception when deleting blobs in test", e);
        }
    }
}
