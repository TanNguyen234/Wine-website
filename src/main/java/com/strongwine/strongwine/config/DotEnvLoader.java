package com.strongwine.strongwine.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Load .env file into Spring Environment
 */
@Component
public class DotEnvLoader implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        Path envPath = Paths.get(".env");
        
        if (!Files.exists(envPath)) {
            System.out.println("⚠️  No .env file found. Using default configuration.");
            return;
        }

        try {
            Map<String, Object> envProps = new HashMap<>();
            
            try (Stream<String> lines = Files.lines(envPath)) {
                lines.filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("#"))
                     .forEach(line -> {
                         int separatorIndex = line.indexOf('=');
                         if (separatorIndex > 0) {
                             String key = line.substring(0, separatorIndex).trim();
                             String value = line.substring(separatorIndex + 1).trim();
                             
                             if (value.startsWith("\"") && value.endsWith("\"")) {
                                 value = value.substring(1, value.length() - 1);
                             }
                             
                             envProps.put(key, value);
                             System.setProperty(key, value);
                         }
                     });
            }
            
            if (!envProps.isEmpty()) {
                environment.getPropertySources().addFirst(new MapPropertySource("dotenv", envProps));
                System.out.println("✅ Loaded " + envProps.size() + " variables from .env file");
            }
            
        } catch (IOException e) {
            System.err.println("❌ Failed to load .env file: " + e.getMessage());
        }
    }
}
