package com.datasync.tool.service;

import com.datasync.tool.entity.ApiDefinition;
import com.datasync.tool.repository.ApiDefinitionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiExecutionService {

    private final ApiDefinitionRepository apiDefinitionRepository;
    private final DataSourceService dataSourceService;
    private final ObjectMapper objectMapper;
    private final GroovyShell groovyShell = new GroovyShell();

    public Object execute(String path, String method, Map<String, Object> params, HttpServletRequest request) {
        Optional<ApiDefinition> apiOpt = apiDefinitionRepository.findByPathAndMethod(path, method);
        if (apiOpt.isEmpty()) {
            throw new RuntimeException("API not found: " + method + " " + path);
        }

        ApiDefinition api = apiOpt.get();
        if (!"ONLINE".equals(api.getStatus())) {
            throw new RuntimeException("API is offline");
        }

        if ("PRIVATE".equals(api.getApiType())) {
            // Check if user is authenticated
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                throw new RuntimeException("Authentication required for private API");
            }
        }

        return debugExecute(api, params, request);
    }

    public Object debugExecute(ApiDefinition api, Map<String, Object> params, HttpServletRequest request) {
        try {
            JsonNode flow = objectMapper.readTree(api.getContent());
            return executeFlow(flow, params, request);
        } catch (Exception e) {
            log.error("Failed to execute API: {}", api.getName(), e);
            throw new RuntimeException("Execution failed: " + e.getMessage());
        }
    }

    private String getNodeType(JsonNode node) {
        // Vue Flow nodes have a top-level "type" (usually "default") 
        // and our functional type is in "data.type"
        JsonNode dataNode = node.path("data");
        if (dataNode.has("type")) {
            return dataNode.get("type").asText().toUpperCase();
        }
        return node.path("type").asText().toUpperCase();
    }

    private Object executeFlow(JsonNode flow, Map<String, Object> params, HttpServletRequest request) throws Exception {
        JsonNode nodes = flow.path("nodes");
        JsonNode edges = flow.path("edges");
        if (edges.isMissingNode()) {
            edges = flow.path("connections");
        }

        Map<String, JsonNode> nodeMap = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();

        for (JsonNode node : nodes) {
            String id = node.path("id").asText();
            nodeMap.put(id, node);
            inDegree.put(id, 0);
        }

        for (JsonNode edge : edges) {
            String source = edge.path("source").asText();
            String target = edge.path("target").asText();
            if (nodeMap.containsKey(source) && nodeMap.containsKey(target)) {
                adj.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
                inDegree.put(target, inDegree.get(target) + 1);
            }
        }

        // Find entry node
        JsonNode entryNode = null;
        for (JsonNode node : nodes) {
            if ("ENTRY".equals(getNodeType(node))) {
                entryNode = node;
                break;
            }
        }

        Map<String, Object> context = new HashMap<>(params);
        if (entryNode == null) {
            // Fallback: execute any query or groovy node if no entry
            for (JsonNode node : nodes) {
                String nodeType = getNodeType(node);
                if ("QUERY".equals(nodeType) || "GROOVY".equals(nodeType) || "OUTPUT_GROOVY".equals(nodeType)) {
                    return executeNode(node, context);
                }
            }
            return Collections.singletonMap("message", "No executable node found");
        }

        // Topological Sort Execution
        Queue<String> queue = new LinkedList<>();
        queue.add(entryNode.path("id").asText());

        Object lastResult = null;
        Set<String> visited = new HashSet<>();

        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            if (visited.contains(nodeId)) continue;
            visited.add(nodeId);

            JsonNode node = nodeMap.get(nodeId);
            lastResult = executeNode(node, context);

            List<String> targets = adj.get(nodeId);
            if (targets != null) {
                for (String targetId : targets) {
                    inDegree.put(targetId, inDegree.get(targetId) - 1);
                    if (inDegree.get(targetId) == 0) {
                        queue.add(targetId);
                    }
                }
            }
        }

        return lastResult;
    }

    private Object executeNode(JsonNode node, Map<String, Object> context) throws Exception {
        String type = getNodeType(node);
        switch (type) {
            case "ENTRY":
                return context;
            case "AUTH":
            case "AUTH_TOKEN":
                return validateToken(node, context);
            case "QUERY":
                Object queryResult = executeQueryNode(node, context);
                context.put("queryResult", queryResult);
                return queryResult;
            case "GROOVY":
            case "OUTPUT_GROOVY":
                Object groovyResult = executeGroovyNode(node, context);
                context.put("groovyResult", groovyResult);
                return groovyResult;
            case "OUTPUT":
            case "RESPONSE":
                return executeOutputNode(node, context);
            default:
                return null;
        }
    }

    private Object validateToken(JsonNode node, Map<String, Object> context) {
        log.info("Validating token for node: {}", node.path("id").asText());
        return true;
    }

    private Object executeQueryNode(JsonNode node, Map<String, Object> context) throws Exception {
        JsonNode data = node.path("data");
        JsonNode config = data.path("config");
        if (config.isMissingNode()) {
            config = data;
        }
        
        Long dataSourceId = config.path("dataSourceId").asLong();
        String sql = config.path("sql").asText();

        // Parameter replacement: ${paramName}
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value != null) {
                sql = sql.replace("${" + key + "}", value.toString());
            }
        }

        com.datasync.tool.entity.DataSource dsEntity = dataSourceService.findById(dataSourceId);
        
        List<Map<String, Object>> results = new ArrayList<>();
        try (java.sql.Connection conn = dataSourceService.getConnection(dsEntity);
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(sql)) {
            
            java.sql.ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnName(i), rs.getObject(i));
                }
                results.add(row);
            }
        }
        return results;
    }

    private Object executeGroovyNode(JsonNode node, Map<String, Object> context) {
        JsonNode data = node.path("data");
        JsonNode config = data.path("config");
        if (config.isMissingNode()) {
            config = data;
        }
        
        String script = config.path("script").asText();
        if (script == null || script.isEmpty()) {
            return Collections.singletonMap("error", "No script provided in Groovy node");
        }

        try {
            Binding binding = new Binding();
            context.forEach(binding::setVariable);
            binding.setVariable("db", new DatabaseHelper(dataSourceService));
            binding.setVariable("log", log);
            
            GroovyShell shell = new GroovyShell(binding);
            return shell.evaluate(script);
        } catch (Exception e) {
            log.error("Failed to execute Groovy script", e);
            throw new RuntimeException("Groovy script execution failed: " + e.getMessage());
        }
    }

    @RequiredArgsConstructor
    public static class DatabaseHelper {
        private final DataSourceService dataSourceService;

        public List<Map<String, Object>> query(Long dataSourceId, String sql, Object... params) {
            com.datasync.tool.entity.DataSource dsEntity = dataSourceService.findById(dataSourceId);
            
            // Very simple parameter replacement for the helper as well
            String finalSql = sql;
            if (params != null && params.length > 0) {
                for (int i = 0; i < params.length; i++) {
                    finalSql = finalSql.replaceFirst("\\?", "'" + params[i].toString() + "'");
                }
            }

            try (java.sql.Connection conn = dataSourceService.getConnection(dsEntity);
                 java.sql.Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery(finalSql)) {
                
                java.sql.ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(metaData.getColumnName(i), rs.getObject(i));
                    }
                    results.add(row);
                }
                return results;
            } catch (Exception e) {
                throw new RuntimeException("Database query failed: " + e.getMessage(), e);
            }
        }
    }

    private Object executeOutputNode(JsonNode node, Map<String, Object> context) {
        JsonNode data = node.path("data");
        JsonNode config = data.path("config");
        if (config.isMissingNode()) {
            config = data;
        }
        
        String outputType = config.path("type").asText("JSON");
        if ("STATIC".equals(outputType)) {
            return config.path("content").asText();
        }
        
        // Default order of preference for results
        if (context.containsKey("groovyResult")) {
            return context.get("groovyResult");
        }
        if (context.containsKey("queryResult")) {
            return context.get("queryResult");
        }
        
        return context;
    }
}
