package com.dbaas.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Docker client configuration for container management.
 * Auto-detects Windows vs Linux for appropriate Docker host.
 */
@Configuration
@Slf4j
public class DockerConfig {

    @Value("${docker.host:}")
    private String dockerHostConfig;

    @Value("${docker.tls-verify:false}")
    private boolean tlsVerify;

    /**
     * Determine Docker host based on OS.
     * Windows: tcp://localhost:2375 or npipe:////./pipe/docker_engine
     * Linux/Mac: unix:///var/run/docker.sock
     */
    private String getDockerHost() {
        if (dockerHostConfig != null && !dockerHostConfig.isEmpty()) {
            return dockerHostConfig;
        }

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            // Windows - use TCP (requires Docker Desktop with TCP enabled)
            // Or use npipe for named pipe
            log.info("Detected Windows OS, using TCP connection to Docker");
            return "tcp://localhost:2375";
        } else {
            // Linux/Mac - use Unix socket
            log.info("Detected Unix OS, using Unix socket for Docker");
            return "unix:///var/run/docker.sock";
        }
    }

    @Bean
    public DockerClientConfig dockerClientConfig() {
        String host = getDockerHost();
        log.info("Docker host: {}", host);

        return DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(host)
                .withDockerTlsVerify(tlsVerify)
                .build();
    }

    @Bean
    public DockerHttpClient dockerHttpClient(DockerClientConfig config) {
        return new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();
    }

    @Bean
    public DockerClient dockerClient(
            DockerClientConfig config,
            DockerHttpClient httpClient) {
        return DockerClientImpl.getInstance(config, httpClient);
    }
}
