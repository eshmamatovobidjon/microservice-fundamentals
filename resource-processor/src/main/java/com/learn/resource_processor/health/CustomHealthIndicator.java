package com.learn.resource_processor.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        Health.Builder healthBuilder = Health.up();

        try {
            // Check Kafka connectivity
            checkKafkaHealth(healthBuilder);

            // Check Elasticsearch connectivity
            checkElasticsearchHealth(healthBuilder);

            // Check external service dependencies
            checkExternalServicesHealth(healthBuilder);

        } catch (Exception e) {
            log.error("Health check failed", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }

        return healthBuilder.build();
    }

    private void checkKafkaHealth(Health.Builder healthBuilder) {
        try {
            healthBuilder.withDetail("kafka", "UP");
        } catch (Exception e) {
            log.warn("Kafka health check failed", e);
            healthBuilder.withDetail("kafka", "DOWN - " + e.getMessage());
        }
    }

    private void checkElasticsearchHealth(Health.Builder healthBuilder) {
        try {
            String elasticsearchHost = System.getenv("ELASTICSEARCH_HOST");
            String elasticsearchPort = System.getenv("ELASTICSEARCH_PORT");

            if (elasticsearchHost == null) elasticsearchHost = "localhost";
            if (elasticsearchPort == null) elasticsearchPort = "9200";

            URL url = new URL("http://" + elasticsearchHost + ":" + elasticsearchPort + "/_health");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                healthBuilder.withDetail("elasticsearch", "UP");
            } else {
                healthBuilder.withDetail("elasticsearch", "DOWN - HTTP " + responseCode);
            }
        } catch (Exception e) {
            log.warn("Elasticsearch health check failed", e);
            healthBuilder.withDetail("elasticsearch", "DOWN - " + e.getMessage());
        }
    }

    private void checkExternalServicesHealth(Health.Builder healthBuilder) {
        try {
            // Check resource service
            checkServiceHealth("resource-service",
                    System.getenv("RESOURCE_SERVICE_NAME"),
                    System.getenv("RESOURCE_SERVICE_PORT"),
                    healthBuilder);

            // Check song service
            checkServiceHealth("song-service",
                    System.getenv("SONG_SERVICE_NAME"),
                    System.getenv("SONG_SERVICE_PORT"),
                    healthBuilder);

        } catch (Exception e) {
            log.warn("External services health check failed", e);
            healthBuilder.withDetail("external-services", "PARTIAL - " + e.getMessage());
        }
    }

    private void checkServiceHealth(String serviceName, String host, String port, Health.Builder healthBuilder) {
        try {
            if (host == null) host = "localhost";
            if (port == null) port = "8080";

            URL url = new URL("http://" + host + ":" + port + "/actuator/health");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                healthBuilder.withDetail(serviceName, "UP");
            } else {
                healthBuilder.withDetail(serviceName, "DOWN - HTTP " + responseCode);
            }
        } catch (Exception e) {
            log.warn("{} health check failed", serviceName, e);
            healthBuilder.withDetail(serviceName, "DOWN - " + e.getMessage());
        }
    }
}