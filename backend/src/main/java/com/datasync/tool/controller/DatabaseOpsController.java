package com.datasync.tool.controller;

import com.datasync.tool.entity.DatabaseExportRecord;
import com.datasync.tool.service.DatabaseOpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/database-ops")
@RequiredArgsConstructor
@CrossOrigin
public class DatabaseOpsController {
    private final DatabaseOpsService databaseOpsService;

    @GetMapping("/tables")
    public List<String> getTables(@RequestParam Long dataSourceId) {
        return databaseOpsService.getTables(dataSourceId);
    }

    @GetMapping("/structure")
    public List<Map<String, Object>> getTableStructure(@RequestParam Long dataSourceId, @RequestParam String tableName) {
        return databaseOpsService.getTableStructure(dataSourceId, tableName);
    }

    @GetMapping("/data")
    public Map<String, Object> getTableData(
            @RequestParam Long dataSourceId,
            @RequestParam String tableName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String filterField,
            @RequestParam(required = false) String filterValue,
            @RequestParam(required = false) String customWhere) {
        return databaseOpsService.getTableData(dataSourceId, tableName, page, size, 
                sortField, sortOrder, filterField, filterValue, customWhere);
    }

    @PostMapping("/export")
    public DatabaseExportRecord startExport(
            @RequestParam Long dataSourceId,
            @RequestParam String tableName,
            @RequestParam String format) {
        return databaseOpsService.startExport(dataSourceId, tableName, format);
    }

    @GetMapping("/export/records")
    public List<DatabaseExportRecord> getExportRecords() {
        return databaseOpsService.getExportRecords();
    }

    @GetMapping("/export/download/{id}")
    public ResponseEntity<Resource> downloadExport(@PathVariable Long id) {
        DatabaseExportRecord record = databaseOpsService.getExportRecord(id);

        if (!"COMPLETED".equals(record.getStatus())) {
            throw new RuntimeException("Export not completed");
        }

        try {
            Path path = Paths.get(record.getFilePath());
            Resource resource = new UrlResource(path.toUri());
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName().toString() + "\"")
                    .body(resource);
        } catch (Exception e) {
            throw new RuntimeException("File download failed: " + e.getMessage());
        }
    }
}
