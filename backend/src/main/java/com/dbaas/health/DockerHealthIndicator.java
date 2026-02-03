package com.dbaas.health;

import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Docker daemon connectivity.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DockerHealthIndicator implements HealthIndicator {

    private final DockerClient dockerClient;

    @Override
    public Health health() {
        try {
            var info = dockerClient.infoCmd().exec();
            
            return Health.up()
                    .withDetail("dockerVersion", info.getServerVersion())
                    .withDetail("containers", info.getContainers())
                    .withDetail("containersRunning", info.getContainersRunning())
                    .withDetail("images", info.getImages())
                    .withDetail("memoryTotal", formatBytes(info.getMemTotal()))
                    .build();
                    
        } catch (Exception e) {
            log.error("Docker health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withException(e)
                    .build();
        }
    }

    private String formatBytes(Long bytes) {
        if (bytes == null) return "unknown";
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        return String.format("%.2f GB", gb);
    }
}
