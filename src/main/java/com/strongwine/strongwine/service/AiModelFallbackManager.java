package com.strongwine.strongwine.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Robust AI Model Fallback Manager
 * Handles priority, 60s cooldowns, and permanent 404 skips.
 */
@Component
public class AiModelFallbackManager {

    private static final long COOLDOWN_SECONDS = 60;
    
    // Strict priority list
    private final List<String> priorityList = List.of(
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash-lite",
        "gemini-3-flash",
        "gemini-2.5-flash",
        "gemma-3-27b",
        "gemma-3-12b",
        "gemma-3-4b",
        "gemma-3-1b"
    );

    private final Map<String, Instant> cooldowns = new ConcurrentHashMap<>();
    private final Set<String> permanentlyUnavailable = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, AtomicLong> successCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> totalCounts = new ConcurrentHashMap<>();

    public Optional<String> getNextModel(Set<String> excludedModels) {
        Instant now = Instant.now();
        
        return priorityList.stream()
            .filter(model -> !permanentlyUnavailable.contains(model))
            .filter(model -> !excludedModels.contains(model))
            .filter(model -> {
                Instant expiry = cooldowns.get(model);
                return expiry == null || now.isAfter(expiry);
            })
            .findFirst();
    }

    public void reportError(String model, int statusCode) {
        if (statusCode == 404) {
            permanentlyUnavailable.add(model);
        } else if (statusCode == 429 || (statusCode >= 500 && statusCode < 600)) {
            cooldowns.put(model, Instant.now().plusSeconds(COOLDOWN_SECONDS));
        }
        totalCounts.computeIfAbsent(model, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void reportSuccess(String model) {
        cooldowns.remove(model);
        successCounts.computeIfAbsent(model, k -> new AtomicLong(0)).incrementAndGet();
        totalCounts.computeIfAbsent(model, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void reportTimeout(String model) {
        cooldowns.put(model, Instant.now().plusSeconds(COOLDOWN_SECONDS));
        totalCounts.computeIfAbsent(model, k -> new AtomicLong(0)).incrementAndGet();
    }

    public double getSuccessRate(String model) {
        long total = totalCounts.getOrDefault(model, new AtomicLong(0)).get();
        if (total == 0) return 0.0;
        return (double) successCounts.getOrDefault(model, new AtomicLong(0)).get() / total;
    }
    
    public List<String> getAllModels() {
        return priorityList;
    }
}
