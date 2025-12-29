package com.datasync.tool.controller;

import com.datasync.tool.service.ApiExecutionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/exec")
@RequiredArgsConstructor
public class ApiExecutionController {

    private final ApiExecutionService apiExecutionService;

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<?> execute(HttpServletRequest request, @RequestBody(required = false) Map<String, Object> body) {
        String fullPath = (String) request.getAttribute(org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        // Remove the /api/exec prefix
        String apiPath = fullPath.substring("/api/exec".length());
        if (apiPath.isEmpty()) {
            apiPath = "/";
        }
        
        String method = request.getMethod();
        
        // Collect parameters from query string and body
        Map<String, Object> params = new HashMap<>();
        
        // 1. Query parameters
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            params.put(paramName, request.getParameter(paramName));
        }
        
        // 2. Body parameters
        if (body != null) {
            params.putAll(body);
        }
        
        try {
            Object result = apiExecutionService.execute(apiPath, method, params, request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 500);
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
