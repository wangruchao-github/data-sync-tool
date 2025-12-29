package com.datasync.tool.controller;

import com.datasync.tool.entity.ApiDefinition;
import com.datasync.tool.repository.ApiDefinitionRepository;
import com.datasync.tool.service.ApiExecutionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;

import java.util.*;

@RestController
@RequestMapping("/api/api-definitions")
@RequiredArgsConstructor
public class ApiController {

    private final ApiDefinitionRepository apiDefinitionRepository;
    private final ApiExecutionService apiExecutionService;

    @GetMapping
    public List<ApiDefinition> getAll() {
        return apiDefinitionRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiDefinition> getById(@PathVariable String id) {
        return apiDefinitionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ApiDefinition save(@RequestBody ApiDefinition apiDefinition) {
        return apiDefinitionRepository.save(apiDefinition);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        apiDefinitionRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/debug")
    public ResponseEntity<?> debug(@PathVariable String id, @RequestBody Map<String, Object> params, HttpServletRequest request) {
        return apiDefinitionRepository.findById(id).map(api -> {
            try {
                // Use a separate method in service that doesn't check status for debugging
                Object result = apiExecutionService.debugExecute(api, params, request);
                return ResponseEntity.ok(result);
            } catch (Exception e) {
                Map<String, Object> error = new HashMap<>();
                error.put("code", 500);
                error.put("message", e.getMessage());
                return ResponseEntity.internalServerError().body(error);
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/docs")
    public ResponseEntity<?> getDocs(@PathVariable String id) {
        return apiDefinitionRepository.findById(id).map(api -> {
            Map<String, Object> doc = new HashMap<>();
            doc.put("openapi", "3.0.0");
            
            Map<String, Object> info = new HashMap<>();
            info.put("title", api.getName());
            info.put("description", api.getDescription());
            info.put("version", api.getVersion());
            doc.put("info", info);

            Map<String, Object> paths = new HashMap<>();
            Map<String, Object> pathItem = new HashMap<>();
            Map<String, Object> operation = new HashMap<>();
            
            operation.put("summary", api.getName());
            operation.put("description", api.getDescription());
            
            List<Map<String, Object>> tags = new ArrayList<>();
            tags.add(Collections.singletonMap("name", api.getApiType()));
            operation.put("tags", Collections.singletonList(api.getApiType()));

            // Basic response
            Map<String, Object> responses = new HashMap<>();
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("description", "Successful response");
            Map<String, Object> content = new HashMap<>();
            Map<String, Object> json = new HashMap<>();
            
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            
            if (api.getResponseExample() != null && !api.getResponseExample().isEmpty()) {
                try {
                    // Try to parse as JSON for the example
                    schema.put("example", new com.fasterxml.jackson.databind.ObjectMapper().readTree(api.getResponseExample()));
                } catch (Exception e) {
                    schema.put("example", api.getResponseExample());
                }
            }
            
            json.put("schema", schema);
            content.put("application/json", json);
            successResponse.put("content", content);
            responses.put("200", successResponse);
            
            operation.put("responses", responses);
            
            pathItem.put(api.getMethod().toLowerCase(), operation);
            paths.put("/api/exec" + api.getPath(), pathItem);
            doc.put("paths", paths);

            return ResponseEntity.ok(doc);
        }).orElse(ResponseEntity.notFound().build());
    }
}
