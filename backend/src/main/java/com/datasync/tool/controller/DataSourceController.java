package com.datasync.tool.controller;

import com.datasync.tool.entity.DataSource;
import com.datasync.tool.service.DataSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/datasources")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // For development convenience
public class DataSourceController {
    private final DataSourceService dataSourceService;

    @GetMapping
    public List<DataSource> getAll() {
        return dataSourceService.findAll();
    }

    @PostMapping
    public DataSource create(@RequestBody DataSource dataSource) {
        return dataSourceService.save(dataSource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        dataSourceService.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/test")
    public ResponseEntity<Boolean> test(@RequestBody DataSource dataSource) {
        boolean success = dataSourceService.testConnection(dataSource);
        return ResponseEntity.ok(success);
    }

    @PostMapping("/{id}/preview")
    public List<Map<String, Object>> preview(@PathVariable Long id, @RequestBody Map<String, String> request) {
        return dataSourceService.previewSql(id, request.get("sql"));
    }

    @PostMapping("/{id}/columns")
    public List<String> getColumns(@PathVariable Long id, @RequestBody Map<String, String> request) {
        return dataSourceService.getColumns(id, request.get("sql"));
    }

    @GetMapping("/{id}/table-columns")
    public List<Map<String, Object>> getTableColumns(@PathVariable Long id, @RequestParam String tableName) {
        return dataSourceService.getTableColumns(id, tableName);
    }
}
