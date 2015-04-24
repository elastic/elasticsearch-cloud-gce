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

package org.elasticsearch.discovery.gce.mock;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Tags;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.cloud.gce.GceComputeService;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.discovery.gce.GceComputeEngineTest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomInt;

/**
 *
 */
public abstract class GceComputeServiceAbstractMock extends AbstractLifecycleComponent<GceComputeServiceAbstractMock>
    implements GceComputeService {

    protected abstract List<ArrayList<String>> getTags();
    protected abstract List<String> getZones();
    private final List<String> zoneList;

    protected GceComputeServiceAbstractMock(Settings settings) {
        super(settings);

        int numNodes = getTags().size() > getZones().size() ? getTags().size() : getZones().size();

        logger.debug("starting GCE Api Mock with {} nodes:", numNodes);
        for (int i = 0; i < numNodes; i++) {
            List<String> tags = getTags().size() > i ? getTags().get(i) : null;
            String zone = getZones().size() > i ? getZones().get(i) : null;
            logger.debug(" - node #{}: tags [{}], zone [{}]", i, tags, zone);
        }

        String[] zoneList = settings.getAsArray(Fields.ZONE);
        this.zoneList = Lists.newArrayList(zoneList);
    }

    private Collection<Instance> instances = null;

    private void computeInstances() {
        instances = new ArrayList<Instance>();
        int nodeNumber = 0;
        // For each instance (item of tags)
        for (List<String> tags : getTags()) {
            String zone = zoneList.isEmpty() ? "dummy" : zoneList.get(randomInt(zoneList.size()-1));
            logger.info(" ----> GCE Mock API: Adding node [{}] in zone [{}]", nodeNumber, zone);
            Instance instance = new Instance();
            instance.setName("Mock Node " + tags);
            instance.setMachineType("Mock Type machine");
            instance.setStatus("STARTED");
            instance.setZone(zone);
            Tags instanceTags = new Tags();
            instanceTags.setItems(tags);
            instance.setTags(instanceTags);
            NetworkInterface networkInterface = new NetworkInterface();
            networkInterface.setNetworkIP("localhost");
            List<NetworkInterface> networkInterfaces = new ArrayList<NetworkInterface>();
            networkInterfaces.add(networkInterface);
            instance.setNetworkInterfaces(networkInterfaces);

            // Add metadata es_port:930X where X is the instance number
            Metadata metadata = new Metadata();
            metadata.put("es_port", "" + GceComputeEngineTest.getPort(nodeNumber));
            instance.setMetadata(metadata);

            instances.add(instance);

            nodeNumber++;
        }
    }

    @Override
    public Collection<Instance> instances() {
        if (instances == null || instances.size() == 0) {
            computeInstances();
        }

        return instances;
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
}
