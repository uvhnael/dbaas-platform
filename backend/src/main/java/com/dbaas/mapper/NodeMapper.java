package com.dbaas.mapper;

import com.dbaas.model.dto.NodeResponse;
import com.dbaas.model.Node;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * MapStruct mapper for Node entity to NodeResponse DTO.
 * Configured with Spring component model for dependency injection.
 */
@Mapper(componentModel = "spring")
public interface NodeMapper {

    /**
     * Map Node entity to NodeResponse.
     * Excludes internal containerId field.
     */
    NodeResponse toResponse(Node node);

    /**
     * Map list of Node entities to list of NodeResponse DTOs.
     */
    List<NodeResponse> toResponseList(List<Node> nodes);
}
