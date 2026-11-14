package com.strongwine.strongwine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.strongwine.strongwine.entity.AdminAuditLog;
import com.strongwine.strongwine.repository.AdminAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditLogService.class);
    private static final int MAX_ACTOR_LEN = 150;
    private static final int MAX_ACTION_LEN = 80;
    private static final int MAX_TARGET_TYPE_LEN = 80;
    private static final int MAX_IP_LEN = 64;

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public AdminAuditLogService(AdminAuditLogRepository adminAuditLogRepository) {
        this.adminAuditLogRepository = adminAuditLogRepository;
        this.objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    public void log(
        String actor,
        String action,
        String targetType,
        Long targetId,
        Object beforeState,
        Object afterState,
        String ipAddress
    ) {
        AdminAuditLog entry = new AdminAuditLog();
        entry.setActorUsername(normalizeRequired(actor, "system", MAX_ACTOR_LEN));
        entry.setAction(normalizeRequired(action, "UNKNOWN", MAX_ACTION_LEN));
        entry.setTargetType(normalizeRequired(targetType, "UNKNOWN", MAX_TARGET_TYPE_LEN));
        entry.setTargetId(targetId);
        entry.setBeforeJson(serializeState(beforeState));
        entry.setAfterJson(serializeState(afterState));
        entry.setIpAddress(normalizeOptional(ipAddress, MAX_IP_LEN));
        adminAuditLogRepository.save(entry);
    }

    private String serializeState(Object state) {
        if (state == null) {
            return null;
        }
        if (state instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception ex) {
            log.warn("Failed to serialize audit log state payload of type {}: {}", state.getClass().getSimpleName(), ex.getMessage());
            String escaped = ex.getMessage() == null ? "unknown" : ex.getMessage().replace("\"", "'");
            return "{\"serializationError\":\"" + escaped + "\"}";
        }
    }

    private String normalizeRequired(String value, String fallback, int maxLen) {
        String effective = (value == null || value.isBlank()) ? fallback : value.trim();
        return effective.length() > maxLen ? effective.substring(0, maxLen) : effective;
    }

    private String normalizeOptional(String value, int maxLen) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLen ? trimmed.substring(0, maxLen) : trimmed;
    }
}
