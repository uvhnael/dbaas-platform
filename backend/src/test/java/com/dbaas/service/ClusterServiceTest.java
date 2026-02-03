package com.dbaas.service;

import com.dbaas.model.dto.ClusterConnectionDTO;
import com.dbaas.exception.ClusterNotFoundException;
import com.dbaas.model.Cluster;
import com.dbaas.model.ClusterStatus;
import com.dbaas.model.dto.CreateClusterRequest;
import com.dbaas.service.cluster.ClusterCrudService;
import com.dbaas.service.cluster.ClusterHealthService;
import com.dbaas.service.cluster.ClusterScalingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ClusterService (Facade).
 * Tests delegation to specialized services.
 */
@ExtendWith(MockitoExtension.class)
class ClusterServiceTest {

    @Mock
    private ClusterCrudService clusterCrudService;

    @Mock
    private ClusterScalingService clusterScalingService;

    @Mock
    private ClusterHealthService clusterHealthService;

    private ClusterService clusterService;

    private Cluster testCluster;
    private final String userId = "user-123";

    @BeforeEach
    void setUp() {
        clusterService = new ClusterService(
                clusterCrudService,
                clusterScalingService,
                clusterHealthService);

        testCluster = Cluster.builder()
                .id(UUID.randomUUID().toString().substring(0, 8))
                .name("test-cluster")
                .userId(userId)
                .mysqlVersion("8.0")
                .replicaCount(2)
                .status(ClusterStatus.RUNNING)
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("getCluster")
    class GetCluster {

        @Test
        @DisplayName("should delegate to ClusterCrudService and return cluster")
        void shouldDelegateToClusterCrudService() {
            // Given
            when(clusterCrudService.getCluster(testCluster.getId(), userId)).thenReturn(testCluster);

            // When
            Cluster result = clusterService.getCluster(testCluster.getId(), userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testCluster.getId());
            assertThat(result.getName()).isEqualTo("test-cluster");
            verify(clusterCrudService).getCluster(testCluster.getId(), userId);
        }

        @Test
        @DisplayName("should propagate ClusterNotFoundException from delegate")
        void shouldPropagateNotFoundException() {
            // Given
            when(clusterCrudService.getCluster("unknown-id", userId))
                    .thenThrow(new ClusterNotFoundException("unknown-id"));

            // When/Then
            assertThatThrownBy(() -> clusterService.getCluster("unknown-id", userId))
                    .isInstanceOf(ClusterNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getClusterInternal")
    class GetClusterInternal {

        @Test
        @DisplayName("should delegate to ClusterCrudService for internal access")
        void shouldDelegateForInternalAccess() {
            // Given
            when(clusterCrudService.getClusterInternal(testCluster.getId())).thenReturn(testCluster);

            // When
            Cluster result = clusterService.getClusterInternal(testCluster.getId());

            // Then
            assertThat(result).isNotNull();
            verify(clusterCrudService).getClusterInternal(testCluster.getId());
        }
    }

    @Nested
    @DisplayName("listClusters")
    class ListClusters {

        @Test
        @DisplayName("should delegate to ClusterCrudService")
        void shouldDelegateToClusterCrudService() {
            // Given
            List<Cluster> clusters = List.of(testCluster);
            when(clusterCrudService.listClusters(userId)).thenReturn(clusters);

            // When
            List<Cluster> result = clusterService.listClusters(userId);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("test-cluster");
            verify(clusterCrudService).listClusters(userId);
        }

        @Test
        @DisplayName("should return empty list when no clusters")
        void shouldReturnEmptyListWhenNoClusters() {
            // Given
            when(clusterCrudService.listClusters(userId)).thenReturn(List.of());

            // When
            List<Cluster> result = clusterService.listClusters(userId);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("listClustersPaged")
    class ListClustersPaged {

        @Test
        @DisplayName("should delegate paging to ClusterCrudService")
        void shouldDelegatePagingToClusterCrudService() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Cluster> page = new PageImpl<>(List.of(testCluster), pageable, 1);
            when(clusterCrudService.listClustersPaged(userId, pageable)).thenReturn(page);

            // When
            Page<Cluster> result = clusterService.listClustersPaged(userId, pageable);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(clusterCrudService).listClustersPaged(userId, pageable);
        }
    }

    @Nested
    @DisplayName("createCluster")
    class CreateCluster {

        @Test
        @DisplayName("should delegate to ClusterCrudService")
        void shouldDelegateToClusterCrudService() {
            // Given
            CreateClusterRequest request = CreateClusterRequest.builder()
                    .name("new-cluster")
                    .mysqlVersion("8.0")
                    .replicaCount(2)
                    .description("Test cluster")
                    .build();

            Cluster createdCluster = Cluster.builder()
                    .id("new-id")
                    .name("new-cluster")
                    .userId(userId)
                    .mysqlVersion("8.0")
                    .replicaCount(2)
                    .status(ClusterStatus.PROVISIONING)
                    .createdAt(Instant.now())
                    .build();

            when(clusterCrudService.createCluster(request, userId)).thenReturn(createdCluster);

            // When
            Cluster result = clusterService.createCluster(request, userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("new-cluster");
            assertThat(result.getStatus()).isEqualTo(ClusterStatus.PROVISIONING);
            verify(clusterCrudService).createCluster(request, userId);
        }
    }

    @Nested
    @DisplayName("Cluster Health Operations")
    class ClusterHealthOperations {

        @Test
        @DisplayName("getClusterHealth should delegate to ClusterHealthService")
        void getClusterHealthShouldDelegate() {
            // Given
            when(clusterHealthService.getClusterHealth(testCluster.getId(), userId))
                    .thenReturn(ClusterStatus.RUNNING);

            // When
            ClusterStatus result = clusterService.getClusterHealth(testCluster.getId(), userId);

            // Then
            assertThat(result).isEqualTo(ClusterStatus.RUNNING);
            verify(clusterHealthService).getClusterHealth(testCluster.getId(), userId);
        }
    }

    @Nested
    @DisplayName("Cluster Connection")
    class ClusterConnection {

        @Test
        @DisplayName("getClusterConnection should delegate to ClusterCrudService")
        void getClusterConnectionShouldDelegate() {
            // Given
            ClusterConnectionDTO connectionDTO = ClusterConnectionDTO.builder()
                    .host("localhost")
                    .port(3306)
                    .username("app_user")
                    .build();

            when(clusterCrudService.getClusterConnection(testCluster.getId(), userId))
                    .thenReturn(connectionDTO);

            // When
            ClusterConnectionDTO result = clusterService.getClusterConnection(testCluster.getId(), userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getHost()).isEqualTo("localhost");
            verify(clusterCrudService).getClusterConnection(testCluster.getId(), userId);
        }
    }
}
