package com.cangshuo.toolbox.storage;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Only registered when storage is enabled, so disabled deployments keep an unaffected /actuator/health. */
@Component
@ConditionalOnProperty(name = "toolbox.storage.enabled", havingValue = "true")
public class StorageHealthIndicator implements HealthIndicator {
    private final MinioClient client;
    private final StorageProperties properties;

    public StorageHealthIndicator(MinioClient client, StorageProperties properties) {
        this.client = client; this.properties = properties;
    }

    @Override public Health health() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build());
            return exists ? Health.up().withDetail("bucket", properties.bucket()).build()
                    : Health.up().withDetail("bucket", properties.bucket()).withDetail("bucketCreated", false).build();
        } catch (Exception exception) {
            return Health.down().withDetail("bucket", properties.bucket()).build();
        }
    }
}
