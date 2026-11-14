package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.AdminAuditLog;
import com.strongwine.strongwine.repository.AdminAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/admin/audit-logs")
public class AdminAuditController {

    private static final int PAGE_SIZE = 20;

    private final AdminAuditLogRepository adminAuditLogRepository;

    public AdminAuditController(AdminAuditLogRepository adminAuditLogRepository) {
        this.adminAuditLogRepository = adminAuditLogRepository;
    }

    @GetMapping
    public String listAuditLogs(@RequestParam(required = false) String action,
                                @RequestParam(required = false) String actor,
                                @RequestParam(defaultValue = "0") int page,
                                Model model) {
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, PAGE_SIZE);

        String trimmedActor = actor != null && !actor.isBlank() ? actor.trim() : null;
        String trimmedAction = action != null && !action.isBlank() ? action.trim() : null;

        Page<AdminAuditLog> auditPage;
        if (trimmedActor != null && trimmedAction == null) {
            auditPage = adminAuditLogRepository.findByActorUsernameContainingIgnoreCaseOrderByCreatedAtDesc(
                    trimmedActor, pageable);
        } else if (trimmedAction != null) {
            List<AdminAuditLog> recent = adminAuditLogRepository.findTop100ByOrderByCreatedAtDesc();
            String actionLower = trimmedAction.toLowerCase(Locale.ROOT);
            String actorLower = trimmedActor != null ? trimmedActor.toLowerCase(Locale.ROOT) : null;
            List<AdminAuditLog> filtered = (recent != null ? recent : List.<AdminAuditLog>of()).stream()
                    .filter(log -> log.getAction() != null
                            && log.getAction().toLowerCase(Locale.ROOT).contains(actionLower))
                    .filter(log -> actorLower == null
                            || (log.getActorUsername() != null
                            && log.getActorUsername().toLowerCase(Locale.ROOT).contains(actorLower)))
                    .toList();
            int start = Math.min(safePage * PAGE_SIZE, filtered.size());
            int end = Math.min(start + PAGE_SIZE, filtered.size());
            auditPage = new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
        } else {
            auditPage = adminAuditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        if (auditPage == null) {
            auditPage = Page.empty(pageable);
        }

        model.addAttribute("auditLogs", auditPage.getContent());
        model.addAttribute("auditPage", auditPage);
        model.addAttribute("currentPage", auditPage.getNumber());
        model.addAttribute("totalPages", auditPage.getTotalPages());
        model.addAttribute("totalEntries", auditPage.getTotalElements());
        model.addAttribute("hasNext", auditPage.hasNext());
        model.addAttribute("hasPrevious", auditPage.hasPrevious());
        model.addAttribute("selectedAction", trimmedAction != null ? trimmedAction : "");
        model.addAttribute("selectedActor", trimmedActor != null ? trimmedActor : "");

        return "admin-audit-logs";
    }
}
