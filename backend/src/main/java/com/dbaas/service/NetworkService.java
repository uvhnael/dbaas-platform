package com.dbaas.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateNetworkResponse;
import com.github.dockerjava.api.model.Network;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for Docker network management.
 * Provides network isolation for each cluster and shared network for
 * Orchestrator.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkService {

    private static final String SHARED_NETWORK_NAME = "dbaas-shared";

    private final DockerClient dockerClient;

    @Value("${cluster.network.subnet-prefix:172.28}")
    private String subnetPrefix;

    // ========================================================================
    // CLUSTER NETWORK OPERATIONS
    // ========================================================================

    /**
     * Create an isolated network for a cluster.
     * 
     * @param clusterId The cluster identifier
     * @return Network ID
     */
    public String createClusterNetwork(String clusterId) {
        String networkName = "cluster-" + clusterId + "-network";
        String subnet = generateSubnet(clusterId);

        log.info("Creating network: {} with subnet: {}", networkName, subnet);

        CreateNetworkResponse network = dockerClient.createNetworkCmd()
                .withName(networkName)
                .withDriver("bridge")
                .withIpam(new Network.Ipam()
                        .withConfig(new Network.Ipam.Config()
                                .withSubnet(subnet)
                                .withGateway(subnet.replace(".0/24", ".1"))))
                .withLabels(Map.of(
                        "dbaas.cluster.id", clusterId,
                        "dbaas.managed", "true"))
                .exec();

        log.info("Network created: {}", network.getId());
        return network.getId();
    }

    /**
     * Remove a cluster network.
     */
    public void removeNetwork(String networkId) {
        try {
            dockerClient.removeNetworkCmd(networkId).exec();
            log.info("Network removed: {}", networkId);
        } catch (Exception e) {
            log.warn("Failed to remove network: {}", networkId, e);
        }
    }

    /**
     * Check if network exists.
     */
    public boolean networkExists(String networkName) {
        try {
            dockerClient.inspectNetworkCmd().withNetworkId(networkName).exec();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Generate unique subnet based on cluster ID.
     */
    private String generateSubnet(String clusterId) {
        // Use hash of clusterId to generate unique third octet
        int hash = Math.abs(clusterId.hashCode() % 254) + 1;
        return String.format("%s.%d.0/24", subnetPrefix, hash);
    }

    // ========================================================================
    // SHARED NETWORK OPERATIONS
    // ========================================================================

    /**
     * Connect a container to the shared network (dbaas-shared).
     * This allows Orchestrator to communicate with all MySQL nodes.
     * 
     * @param containerId Container to connect
     */
    public void connectToSharedNetwork(String containerId) {
        try {
            ensureSharedNetworkExists();

            dockerClient.connectToNetworkCmd()
                    .withNetworkId(SHARED_NETWORK_NAME)
                    .withContainerId(containerId)
                    .exec();

            log.debug("Container {} connected to shared network", containerId);
        } catch (Exception e) {
            log.warn("Failed to connect container to shared network: {}", e.getMessage());
        }
    }

    /**
     * Ensure the shared network exists, creating it if necessary.
     */
    private void ensureSharedNetworkExists() {
        if (!networkExists(SHARED_NETWORK_NAME)) {
            log.info("Creating shared network: {}", SHARED_NETWORK_NAME);
            dockerClient.createNetworkCmd()
                    .withName(SHARED_NETWORK_NAME)
                    .withDriver("bridge")
                    .withLabels(Map.of("dbaas.managed", "true"))
                    .exec();
        }
    }
}
