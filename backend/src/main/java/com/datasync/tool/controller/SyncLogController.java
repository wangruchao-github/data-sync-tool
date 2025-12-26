package com.datasync.tool.controller;

import com.datasync.tool.entity.SyncLog;
import com.datasync.tool.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class SyncLogController {
    private final SyncLogRepository syncLogRepository;

    @GetMapping
    public Page<SyncLog> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return syncLogRepository.findAllByOrderByStartTimeDesc(pageable);
    }

    @GetMapping("/task/{taskId}")
    public Page<SyncLog> getLogsByTaskId(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return syncLogRepository.findByTaskIdOrderByStartTimeDesc(taskId, pageable);
    }

    @GetMapping("/task/{taskId}/latest")
    public SyncLog getLatestLogByTaskId(@PathVariable Long taskId) {
        return syncLogRepository.findFirstByTaskIdOrderByStartTimeDesc(taskId).orElse(null);
    }

    @GetMapping("/search")
    public Page<SyncLog> searchLogs(
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return syncLogRepository.findWithFilters(taskId, startTime, endTime, pageable);
    }
}
