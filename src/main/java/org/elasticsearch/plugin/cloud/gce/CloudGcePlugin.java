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

package org.elasticsearch.plugin.cloud.gce;

import org.elasticsearch.cloud.gce.GceModule;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.repositories.RepositoriesModule;
import org.elasticsearch.repositories.gce.GoogleCloudStorageRepository;
import org.elasticsearch.repositories.gce.GoogleCloudStorageRepositoryModule;

import java.util.Collection;

/**
 *
 */
public class CloudGcePlugin extends AbstractPlugin {

    private final Settings settings;

    public CloudGcePlugin(Settings settings) {
        this.settings = settings;
    }

    @Override
    public String name() {
        return "cloud-gce";
    }

    @Override
    public String description() {
        return "Cloud Google Compute Engine Plugin";
    }

    @Override
    public Collection<Module> modules(Settings settings) {
        Collection<Module> modules = Lists.newArrayList();
        if (settings.getAsBoolean("cloud.enabled", true)) {
            modules.add(new GceModule(settings));
        }
        return modules;
    }


    @Override
    public Collection<Class<? extends LifecycleComponent>> services() {
        Collection<Class<? extends LifecycleComponent>> services = Lists.newArrayList();
        if (settings.getAsBoolean("cloud.enabled", true)) {
            services.add(GceModule.getGoogleCloudStorageServiceClass(settings));
        }
        return services;
    }

    public void onModule(RepositoriesModule repositoriesModule) {
        if (settings.getAsBoolean("cloud.enabled", true)) {
            repositoriesModule.registerRepository(GoogleCloudStorageRepository.TYPE, GoogleCloudStorageRepositoryModule.class);
        }
    }
}
