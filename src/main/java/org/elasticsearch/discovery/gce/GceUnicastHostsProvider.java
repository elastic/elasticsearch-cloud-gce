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

package org.elasticsearch.discovery.gce;

import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.NetworkInterface;
import org.elasticsearch.Version;
import org.elasticsearch.cloud.gce.GceComputeService;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.discovery.zen.ping.unicast.UnicastHostsProvider;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.List;

import static org.elasticsearch.cloud.gce.GceComputeService.Fields;

/**
 *
 */
public class GceUnicastHostsProvider extends AbstractComponent implements UnicastHostsProvider {

    static final class Status {
        private static final String TERMINATED = "TERMINATED";
    }

    private final GceComputeService gceComputeService;
    private TransportService transportService;
    private NetworkService networkService;

    private final String project;
    private final String zone;
    private final String[] tags;

    private final TimeValue refreshInterval;
    private long lastRefresh;
    private List<DiscoveryNode> cachedDiscoNodes;

    @Inject
    public GceUnicastHostsProvider(Settings settings, GceComputeService gceComputeService,
            TransportService transportService,
            NetworkService networkService) {
        super(settings);
        this.gceComputeService = gceComputeService;
        this.transportService = transportService;
        this.networkService = networkService;

        this.refreshInterval = componentSettings.getAsTime(Fields.REFRESH,
                settings.getAsTime("cloud.gce." + Fields.REFRESH, TimeValue.timeValueSeconds(0)));

        this.project = componentSettings.get(Fields.PROJECT, settings.get("cloud.gce." + Fields.PROJECT));
        this.zone = componentSettings.get(Fields.ZONE, settings.get("cloud.gce." + Fields.ZONE));

        // Check that we have all needed properties
        checkProperty(Fields.PROJECT, project);
        checkProperty(Fields.ZONE, zone);

        this.tags = settings.getAsArray("discovery.gce.tags");
        if (logger.isDebugEnabled()) {
            logger.debug("using tags {}", Lists.newArrayList(this.tags));
        }
    }

    /**
     * We build the list of Nodes from GCE Management API
     * Information can be cached using `plugins.refresh_interval` property if needed.
     * Setting `plugins.refresh_interval` to `-1` will cause infinite caching.
     * Setting `plugins.refresh_interval` to `0` will disable caching (default).
     */
    @Override
    public List<DiscoveryNode> buildDynamicNodes() {
        if (refreshInterval.millis() != 0) {
            if (cachedDiscoNodes != null &&
                    (refreshInterval.millis() < 0 || (System.currentTimeMillis() - lastRefresh) < refreshInterval.millis())) {
                if (logger.isTraceEnabled()) logger.trace("using cache to retrieve node list");
                return cachedDiscoNodes;
            }
            lastRefresh = System.currentTimeMillis();
        }
        logger.debug("start building nodes list using GCE API");

        cachedDiscoNodes = Lists.newArrayList();
        String ipAddress = null;
        try {
            InetAddress inetAddress = networkService.resolvePublishHostAddress(null);
            if (inetAddress != null) {
                ipAddress = inetAddress.getHostAddress();
            }
        } catch (IOException e) {
            // We can't find the publish host address... Hmmm. Too bad :-(
            // We won't simply filter it
        }

        try {
            Collection<Instance> instances = gceComputeService.instances();

            if (instances == null) {
                logger.trace("no instance found for project [{}], zone [{}].", this.project, this.zone);
                return cachedDiscoNodes;
            }

            for (Instance instance : instances) {
                String name = instance.getName();
                String type = instance.getMachineType();

                String status = instance.getStatus();
                logger.trace("gce instance {} with status {} found.", name, status);

                // We don't want to connect to TERMINATED status instances
                // See https://github.com/elasticsearch/elasticsearch-cloud-gce/issues/3
                if (Status.TERMINATED.equals(status)) {
                    logger.debug("node {} is TERMINATED. Ignoring", name);
                    continue;
                }

                // see if we need to filter by tag
                boolean filterByTag = false;
                if (tags.length > 0) {
                    logger.trace("start filtering instance {} with tags {}.", name, tags);
                    if (instance.getTags() == null || instance.getTags().isEmpty()) {
                        // If this instance have no tag, we filter it
                        logger.trace("no tags for this instance but we asked for tags. {} won't be part of the cluster.", name);
                        filterByTag = true;
                    } else {
                        // check that all tags listed are there on the instance
                        logger.trace("comparing instance tags {} with tags filter {}.", instance.getTags().getItems(), tags);
                        for (String tag : tags) {
                            boolean found = false;
                            for (String instancetag : instance.getTags().getItems()) {
                                if (instancetag.equals(tag)) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                filterByTag = true;
                                break;
                            }
                        }
                    }
                }
                if (filterByTag) {
                    logger.trace("filtering out instance {} based tags {}, not part of {}", name, tags,
                            instance.getTags() == null ? "" : instance.getTags().getItems());
                    continue;
                } else {
                    logger.trace("instance {} with tags {} is added to discovery", name, tags);
                }

                String ip_public = null;
                String ip_private = null;

                List<NetworkInterface> interfaces = instance.getNetworkInterfaces();

                for (NetworkInterface networkInterface : interfaces) {
                    if (ip_public == null) {
                        // Trying to get Public IP Address (For future use)
                        if (networkInterface.getAccessConfigs() != null) {
                            for (AccessConfig accessConfig : networkInterface.getAccessConfigs()) {
                                if (Strings.hasText(accessConfig.getNatIP())) {
                                    ip_public = accessConfig.getNatIP();
                                    break;
                                }
                            }
                        }
                    }

                    if (ip_private == null) {
                        ip_private = networkInterface.getNetworkIP();
                    }

                    // If we have both public and private, we can stop here
                    if (ip_private != null && ip_public != null) break;
                }

                try {
                    if (ip_private.equals(ipAddress)) {
                        // We found the current node.
                        // We can ignore it in the list of DiscoveryNode
                        logger.trace("current node found. Ignoring {} - {}", name, ip_private);
                    } else {
                        String address = ip_private;
                        // Test if we have es_port metadata defined here
                        if (instance.getMetadata() != null && instance.getMetadata().containsKey("es_port")) {
                            Object es_port = instance.getMetadata().get("es_port");
                            logger.trace("es_port is defined with {}", es_port);
                            if (es_port instanceof String) {
                                address = address.concat(":").concat((String) es_port);
                            } else {
                                // Ignoring other values
                                logger.trace("es_port is instance of {}. Ignoring...", es_port.getClass().getName());
                            }
                        }

                        // ip_private is a single IP Address. We need to build a TransportAddress from it
                        TransportAddress[] addresses = transportService.addressesFromString(address);

                        // If user has set `es_port` metadata, we don't need to ping all ports
                        // we only limit to 1 addresses, makes no sense to ping 100 ports
                        for (int i = 0; i < addresses.length; i++) {
                            logger.trace("adding {}, type {}, address {}, transport_address {}, status {}", name, type,
                                     ip_private, addresses[i], status);
                            cachedDiscoNodes.add(new DiscoveryNode("#cloud-" + name + "-" + i, addresses[i], Version.CURRENT));
                        }
                    }
                } catch (Exception e) {
                    logger.warn("failed to add {}, address {}", e, name, ip_private);
                }

            }
        } catch (Throwable e) {
            logger.warn("Exception caught during discovery {} : {}", e.getClass().getName(), e.getMessage());
            logger.trace("Exception caught during discovery", e);
        }

        logger.debug("{} node(s) added", cachedDiscoNodes.size());
        logger.debug("using dynamic discovery nodes {}", cachedDiscoNodes);

        return cachedDiscoNodes;
    }

    private void checkProperty(String name, String value) {
        if (!Strings.hasText(value)) {
            logger.warn("cloud.gce.{} is not set.", name);
        }
    }
}
