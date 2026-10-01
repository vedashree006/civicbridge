package com.civicbridge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class StorageConfig {

    @Value("${app.storage.path}")
    private String storagePath;

    @Bean
    public Path uploadRootPath() throws IOException {
        Path root = Path.of(storagePath);
        Files.createDirectories(root);
        return root;
    }
}
