package com.cangshuo.toolbox.storage;

import io.minio.MinioClient;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({StorageProperties.class, QuotaProperties.class})
public class StorageConfiguration {

    @Bean
    @ConditionalOnProperty(name = "toolbox.storage.enabled", havingValue = "true")
    MinioClient minioClient(StorageProperties properties) {
        return MinioClient.builder().endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey()).build();
    }

    @Bean
    @ConditionalOnProperty(name = "toolbox.storage.enabled", havingValue = "true")
    ObjectStorage minioObjectStorage(MinioClient client, StorageProperties properties, Clock clock) {
        return new MinioObjectStorage(client, properties, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "toolbox.storage.enabled", havingValue = "false", matchIfMissing = true)
    ObjectStorage disabledObjectStorage() {
        return new DisabledObjectStorage();
    }
}
