package com.dbaas.repository;

import com.dbaas.model.Cluster;
import com.dbaas.model.ClusterStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for ClusterRepository.
 * Uses H2 in-memory database for testing.
 */
@DataJpaTest
@ActiveProfiles("test")
class ClusterRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ClusterRepository clusterRepository;

    private Cluster cluster1;
    private Cluster cluster2;
    private final String userId = "user-123";

    @BeforeEach
    void setUp() {
        cluster1 = Cluster.builder()
                .id(UUID.randomUUID().toString().substring(0, 8))
                .name("production-cluster")
                .userId(userId)
                .mysqlVersion("8.0")
                .replicaCount(2)
                .status(ClusterStatus.RUNNING)
                .createdAt(Instant.now())
                .build();

        cluster2 = Cluster.builder()
                .id(UUID.randomUUID().toString().substring(0, 8))
                .name("staging-cluster")
                .userId(userId)
                .mysqlVersion("8.0")
                .replicaCount(1)
                .status(ClusterStatus.STOPPED)
                .createdAt(Instant.now())
                .build();

        entityManager.persist(cluster1);
        entityManager.persist(cluster2);
        entityManager.flush();
    }

    @Nested
    @DisplayName("findByUserId")
    class FindByUserId {

        @Test
        @DisplayName("should return all clusters for user")
        void shouldReturnAllClustersForUser() {
            // When
            List<Cluster> result = clusterRepository.findByUserId(userId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Cluster::getName)
                    .containsExactlyInAnyOrder("production-cluster", "staging-cluster");
        }

        @Test
        @DisplayName("should return empty list for unknown user")
        void shouldReturnEmptyListForUnknownUser() {
            // When
            List<Cluster> result = clusterRepository.findByUserId("unknown-user");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return paged results")
        void shouldReturnPagedResults() {
            // When
            Page<Cluster> result = clusterRepository.findByUserId(userId, PageRequest.of(0, 1));

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getTotalPages()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("countByUserId")
    class CountByUserId {

        @Test
        @DisplayName("should return correct count")
        void shouldReturnCorrectCount() {
            // When
            int count = clusterRepository.countByUserId(userId);

            // Then
            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("should return 0 for unknown user")
        void shouldReturnZeroForUnknownUser() {
            // When
            int count = clusterRepository.countByUserId("unknown-user");

            // Then
            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("findByStatus")
    class FindByStatus {

        @Test
        @DisplayName("should return clusters by status")
        void shouldReturnClustersByStatus() {
            // When
            List<Cluster> runningClusters = clusterRepository.findByStatus(ClusterStatus.RUNNING);
            List<Cluster> stoppedClusters = clusterRepository.findByStatus(ClusterStatus.STOPPED);

            // Then
            assertThat(runningClusters).hasSize(1);
            assertThat(runningClusters.get(0).getName()).isEqualTo("production-cluster");

            assertThat(stoppedClusters).hasSize(1);
            assertThat(stoppedClusters.get(0).getName()).isEqualTo("staging-cluster");
        }

        @Test
        @DisplayName("should return empty list when no clusters match status")
        void shouldReturnEmptyWhenNoMatch() {
            // When
            List<Cluster> result = clusterRepository.findByStatus(ClusterStatus.PROVISIONING);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("countByStatus")
    class CountByStatus {

        @Test
        @DisplayName("should return correct count by status")
        void shouldReturnCorrectCountByStatus() {
            // When
            long runningCount = clusterRepository.countByStatus(ClusterStatus.RUNNING);
            long stoppedCount = clusterRepository.countByStatus(ClusterStatus.STOPPED);

            // Then
            assertThat(runningCount).isEqualTo(1);
            assertThat(stoppedCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("existsByNameAndUserId")
    class ExistsByNameAndUserId {

        @Test
        @DisplayName("should return true when cluster exists")
        void shouldReturnTrueWhenExists() {
            // When
            boolean exists = clusterRepository.existsByNameAndUserId("production-cluster", userId);

            // Then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false when cluster does not exist")
        void shouldReturnFalseWhenNotExists() {
            // When
            boolean exists = clusterRepository.existsByNameAndUserId("non-existent", userId);

            // Then
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("should return false when name exists for different user")
        void shouldReturnFalseForDifferentUser() {
            // When
            boolean exists = clusterRepository.existsByNameAndUserId("production-cluster", "other-user");

            // Then
            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("searchByName")
    class SearchByName {

        @Test
        @DisplayName("should find clusters by partial name match")
        void shouldFindByPartialName() {
            // When
            Page<Cluster> result = clusterRepository.searchByName(userId, "prod", PageRequest.of(0, 10));

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("production-cluster");
        }

        @Test
        @DisplayName("should be case-insensitive")
        void shouldBeCaseInsensitive() {
            // When
            Page<Cluster> result = clusterRepository.searchByName(userId, "PRODUCTION", PageRequest.of(0, 10));

            // Then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should return all matching clusters")
        void shouldReturnAllMatching() {
            // When
            Page<Cluster> result = clusterRepository.searchByName(userId, "cluster", PageRequest.of(0, 10));

            // Then
            assertThat(result.getContent()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("findByUserIdAndStatus")
    class FindByUserIdAndStatus {

        @Test
        @DisplayName("should return clusters matching user and status")
        void shouldReturnClustersMatchingUserAndStatus() {
            // When
            List<Cluster> result = clusterRepository.findByUserIdAndStatus(userId, ClusterStatus.RUNNING);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("production-cluster");
        }
    }

    @Nested
    @DisplayName("countByUserIdAndStatus")
    class CountByUserIdAndStatus {

        @Test
        @DisplayName("should return correct count")
        void shouldReturnCorrectCount() {
            // When
            long count = clusterRepository.countByUserIdAndStatus(userId, ClusterStatus.RUNNING);

            // Then
            assertThat(count).isEqualTo(1);
        }
    }
}
