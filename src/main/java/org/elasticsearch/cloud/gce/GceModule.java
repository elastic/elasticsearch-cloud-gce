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

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.discovery.gce.GceDiscovery;

import static org.elasticsearch.cloud.gce.GoogleCloudStorageService.Fields.CREDENTIALS_FILE;
import static org.elasticsearch.cloud.gce.GoogleCloudStorageService.Fields.REPOSITORIES_GS;

/**
 *
 */
public class GceModule extends AbstractModule {

    protected final ESLogger logger;

    private Settings settings;

    @Inject
    public GceModule(Settings settings) {
        this.logger = Loggers.getLogger(getClass(), settings);
        this.settings = settings;
    }

    @Override
    protected void configure() {
        // If we have set discovery to "gce", let's start the GCE compute service
        if (isDiscoveryReady(settings, logger)) {
            logger.debug("starting Google Compute Engine discovery service");
            bind(GceComputeService.class)
                    .to(settings.getAsClass("cloud.gce.api.impl", GceComputeServiceImpl.class))
                    .asEagerSingleton();
        }

        // If we have settings for google storage service, let's start theservice
        if (isSnapshotReady(settings, logger)) {
            logger.debug("starting Google Cloud Storage repository service");
            bind(GoogleCloudStorageService.class)
                    .to(getGoogleCloudStorageServiceClass(settings))
                    .asEagerSingleton();
        }
    }

    public static Class<? extends GoogleCloudStorageService> getGoogleCloudStorageServiceClass(Settings settings) {
        return settings.getAsClass("cloud.gce.storage.api.impl", GoogleCloudStorageServiceImpl.class);
    }

    /**
     * Check if discovery is meant to start
     *
     * @return true if we can start discovery features
     */
    public static boolean isCloudReady(Settings settings) {
        return (settings.getAsBoolean("cloud.enabled", true));
    }

    /**
     * Check if discovery is meant to start
     *
     * @return true if we can start discovery features
     */
    public static boolean isDiscoveryReady(Settings settings, ESLogger logger) {
        // Cloud services are disabled
        if (!isCloudReady(settings)) {
            logger.debug("cloud settings are disabled");
            return false;
        }

        // User set discovery.type: gce
        if (!GceDiscovery.GCE.equalsIgnoreCase(settings.get("discovery.type"))) {
            logger.debug("discovery.type not set to {}", GceDiscovery.GCE);
            return false;
        }
        logger.debug("all required properties for Google Compute Engine discovery are set!");

        return true;
    }

    /**
     * Check if we have repository settings available
     *
     * @return true if we can use snapshot and restore
     */
    public static boolean isSnapshotReady(Settings settings, ESLogger logger) {
        // Cloud services are disabled
        if (!isCloudReady(settings)) {
            logger.debug("cloud settings are disabled");
            return false;
        }

        if (isPropertyMissing(settings, REPOSITORIES_GS + CREDENTIALS_FILE, logger)) {
            logger.debug("{} not set", REPOSITORIES_GS + CREDENTIALS_FILE);
            return false;
        }
        logger.debug("all required properties for Google Storage repository are set");
        return true;
    }

    private static boolean isPropertyMissing(Settings settings, String name, ESLogger logger) throws ElasticsearchException {
        if (!Strings.hasText(settings.get(name))) {
            if (logger != null) {
                logger.warn("{} is not set or is incorrect.", name);
            }
            return true;
        }
        return false;
    }
}