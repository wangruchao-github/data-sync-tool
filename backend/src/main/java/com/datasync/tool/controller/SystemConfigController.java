package com.datasync.tool.controller;

import com.datasync.tool.entity.SystemConfig;
import com.datasync.tool.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping("/public")
    public ResponseEntity<Map<String, String>> getPublicConfigs() {
        return ResponseEntity.ok(systemConfigService.getPublicConfigs());
    }

    @GetMapping("/all")
    public ResponseEntity<List<SystemConfig>> getAllConfigs() {
        return ResponseEntity.ok(systemConfigService.getAllConfigs());
    }

    @PostMapping("/batch")
    public ResponseEntity<Void> saveConfigs(@RequestBody List<SystemConfig> configs) {
        systemConfigService.saveConfigs(configs);
        return ResponseEntity.ok().build();
    }
}
