package com.dbaas.controller;

import com.dbaas.model.Cluster;
import com.dbaas.model.ClusterStatus;
import com.dbaas.model.User;
import com.dbaas.model.UserRole;
import com.dbaas.model.dto.CreateClusterRequest;
import com.dbaas.repository.ClusterRepository;
import com.dbaas.repository.UserRepository;
import com.dbaas.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ClusterController.
 * Uses @SpringBootTest with full application context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClusterControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClusterRepository clusterRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User testUser;
    private User adminUser;
    private Cluster testCluster;
    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        // Clean up
        clusterRepository.deleteAll();
        userRepository.deleteAll();

        // Create regular user
        testUser = User.builder()
                .id(UUID.randomUUID().toString())
                .username("testuser")
                .email("test@example.com")
                .passwordHash(passwordEncoder.encode("password"))
                .role(UserRole.USER)
                .enabled(true)
                .createdAt(Instant.now())
                .build();
        userRepository.save(testUser);
        userToken = jwtService.generateToken(testUser);

        // Create admin user
        adminUser = User.builder()
                .id(UUID.randomUUID().toString())
                .username("admin")
                .email("admin@example.com")
                .passwordHash(passwordEncoder.encode("adminpass"))
                .role(UserRole.ADMIN)
                .enabled(true)
                .createdAt(Instant.now())
                .build();
        userRepository.save(adminUser);
        adminToken = jwtService.generateToken(adminUser);

        // Create test cluster owned by regular user
        testCluster = Cluster.builder()
                .id(UUID.randomUUID().toString())
                .name("test-cluster")
                .userId(testUser.getId())
                .mysqlVersion("8.0")
                .replicaCount(2)
                .status(ClusterStatus.RUNNING)
                .dbUser("app_user")
                .proxyPort(33061)
                .enableOrchestrator(true)
                .enableBackup(false)
                .replicaContainerIds(new ArrayList<>())
                .createdAt(Instant.now())
                .build();
        clusterRepository.save(testCluster);
    }

    @Test
    @DisplayName("GET /api/v1/clusters - should return user's clusters")
    void getClusters_ForAuthenticatedUser_ShouldReturnUserClusters() throws Exception {
        mockMvc.perform(get("/api/v1/clusters")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].name").value("test-cluster"));
    }

    @Test
    @DisplayName("GET /api/v1/clusters - should return 401 without authentication")
    void getClusters_WithoutAuth_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/clusters"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/clusters/{id} - should return cluster details for owner")
    void getClusterById_ForOwner_ShouldReturnClusterDetails() throws Exception {
        mockMvc.perform(get("/api/v1/clusters/" + testCluster.getId())
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(testCluster.getId()))
                .andExpect(jsonPath("$.data.name").value("test-cluster"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
    }

    @Test
    @DisplayName("GET /api/v1/clusters/{id} - should return 404 for non-existent cluster")
    void getClusterById_NonExistent_ShouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/clusters/non-existent-id")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CLUSTER_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/clusters - should create cluster and return PROVISIONING status")
    void createCluster_WithValidRequest_ShouldReturnProvisioningStatus() throws Exception {
        CreateClusterRequest request = new CreateClusterRequest();
        request.setName("new-cluster");
        request.setMysqlVersion("8.0");
        request.setReplicaCount(1);

        mockMvc.perform(post("/api/v1/clusters")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("new-cluster"))
                .andExpect(jsonPath("$.data.status").value("PROVISIONING"));

        // Verify cluster was created in database
        assertThat(clusterRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("POST /api/v1/clusters - should return 400 for invalid request")
    void createCluster_WithInvalidRequest_ShouldReturn400() throws Exception {
        CreateClusterRequest request = new CreateClusterRequest();
        // Missing required fields

        mockMvc.perform(post("/api/v1/clusters")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/v1/clusters/{id} - should delete cluster for owner")
    void deleteCluster_ForOwner_ShouldDeleteAndReturn202() throws Exception {
        mockMvc.perform(delete("/api/v1/clusters/" + testCluster.getId())
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("Admin can access all clusters")
    void getClusters_AsAdmin_ShouldReturnAllClusters() throws Exception {
        // Create another user with a cluster
        User anotherUser = User.builder()
                .id(UUID.randomUUID().toString())
                .username("anotheruser")
                .email("another@example.com")
                .passwordHash(passwordEncoder.encode("password"))
                .role(UserRole.USER)
                .enabled(true)
                .createdAt(Instant.now())
                .build();
        userRepository.save(anotherUser);

        Cluster anotherCluster = Cluster.builder()
                .id(UUID.randomUUID().toString())
                .name("another-cluster")
                .userId(anotherUser.getId())
                .mysqlVersion("8.0")
                .replicaCount(1)
                .status(ClusterStatus.RUNNING)
                .replicaContainerIds(new ArrayList<>())
                .createdAt(Instant.now())
                .build();
        clusterRepository.save(anotherCluster);

        // Admin should see all clusters
        mockMvc.perform(get("/api/v1/admin/clusters")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("Regular user cannot access admin endpoints")
    void adminEndpoints_AsRegularUser_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/clusters")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }
}
