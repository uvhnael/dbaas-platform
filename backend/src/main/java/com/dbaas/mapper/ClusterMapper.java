package com.dbaas.mapper;

import com.dbaas.model.dto.ClusterResponse;
import com.dbaas.model.Cluster;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * MapStruct mapper for Cluster entity to ClusterResponse DTO.
 * Configured with Spring component model for dependency injection.
 */
@Mapper(componentModel = "spring")
public interface ClusterMapper {

    /**
     * Map Cluster entity to ClusterResponse.
     * Excludes sensitive fields like passwords and container IDs.
     */
    @Mapping(target = "totalNodes", expression = "java(calculateTotalNodes(cluster))")
    @Mapping(target = "runningNodes", constant = "0")
    ClusterResponse toResponse(Cluster cluster);

    /**
     * Map list of Cluster entities to list of ClusterResponse DTOs.
     */
    List<ClusterResponse> toResponseList(List<Cluster> clusters);

    /**
     * Calculate total nodes: master(1) + proxy(1) + replicas
     */
    default int calculateTotalNodes(Cluster cluster) {
        return 2 + cluster.getReplicaCount();
    }

    /**
     * Map Cluster entity with running nodes count.
     */
    @Mapping(target = "totalNodes", expression = "java(calculateTotalNodes(cluster))")
    @Mapping(target = "runningNodes", source = "runningNodes")
    ClusterResponse toResponseWithStats(Cluster cluster, int runningNodes);
}
