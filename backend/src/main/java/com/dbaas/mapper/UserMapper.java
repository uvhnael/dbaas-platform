package com.dbaas.mapper;

import com.dbaas.model.User;
import com.dbaas.model.dto.UserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MapStruct mapper for User entity to UserDto.
 * Configured with Spring component model for dependency injection.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Map User entity to UserDto.
     * Excludes sensitive fields like password hash.
     */
    @Mapping(target = "clusterCount", ignore = true)
    UserDto toDto(User user);

    /**
     * Map list of User entities to list of UserDto.
     */
    List<UserDto> toDtoList(List<User> users);

    /**
     * Map User with cluster count.
     */
    @Mapping(target = "clusterCount", source = "clusterCount")
    UserDto toDtoWithClusterCount(User user, Integer clusterCount);
}
