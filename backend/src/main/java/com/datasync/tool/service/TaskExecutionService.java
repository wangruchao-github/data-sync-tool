package com.datasync.tool.service;

import com.datasync.tool.entity.DataSource;
import com.datasync.tool.entity.SyncLog;
import com.datasync.tool.entity.SyncTask;
import com.datasync.tool.repository.SyncLogRepository;
import com.datasync.tool.repository.SyncTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutionService implements ApplicationContextAware {
    private final DataSourceService dataSourceService;
    private final SyncTaskRepository syncTaskRepository;
    private final SyncLogRepository syncLogRepository;
    private final ObjectMapper objectMapper;
    private final Scheduler scheduler;
    private static ApplicationContext context;

    private final ExecutorService taskExecutor = new ThreadPoolExecutor(
            5, 5, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        context = applicationContext;
    }

    @PostConstruct
    public void init() throws SchedulerException {
        // 应用启动时，清理异常终止的任务状态
        cleanupRunningTasks();
        
        if (!scheduler.isStarted()) {
            scheduler.start();
        }
        refreshAllSchedules();
    }

    private void cleanupRunningTasks() {
        try {
            List<SyncLog> runningLogs = syncLogRepository.findAllByResult("RUNNING");
            if (!runningLogs.isEmpty()) {
                log.info("Found {} tasks in RUNNING state on startup. Marking as FAILURE.", runningLogs.size());
                for (SyncLog syncLog : runningLogs) {
                    syncLog.setResult("FAILURE");
                    syncLog.setMessage("Task terminated unexpectedly due to application shutdown or crash.");
                    syncLog.setEndTime(LocalDateTime.now());
                    syncLogRepository.save(syncLog);
                }
            }
        } catch (Exception e) {
            log.error("Failed to cleanup running tasks", e);
        }
    }

    public void refreshAllSchedules() {
        List<SyncTask> tasks = syncTaskRepository.findAllByType("TASK");
        for (SyncTask task : tasks) {
            updateTaskSchedule(task);
        }
    }

    public void updateTaskSchedule(SyncTask task) {
        if ("FOLDER".equals(task.getType())) {
            return;
        }
        try {
            JobKey jobKey = JobKey.jobKey("task_" + task.getId(), "sync_tasks");
            scheduler.deleteJob(jobKey);

            if (task.getCron() != null && !task.getCron().isEmpty() && "ENABLED".equals(task.getStatus())) {
                JobDetail jobDetail = JobBuilder.newJob(SyncJob.class)
                        .withIdentity(jobKey)
                        .usingJobData("taskId", task.getId())
                        .build();

                CronTrigger trigger = TriggerBuilder.newTrigger()
                        .withIdentity("trigger_" + task.getId(), "sync_tasks")
                        .withSchedule(CronScheduleBuilder.cronSchedule(task.getCron()))
                        .build();

                scheduler.scheduleJob(jobDetail, trigger);
                log.info("Scheduled task {}: {}", task.getId(), task.getCron());
            }
        } catch (Exception e) {
            log.error("Failed to schedule task " + task.getId(), e);
        }
    }

    @DisallowConcurrentExecution
    public static class SyncJob implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            Long taskId = context.getJobDetail().getJobDataMap().getLong("taskId");
            TaskExecutionService service = TaskExecutionService.context.getBean(TaskExecutionService.class);
            service.executeTask(taskId);
        }
    }

    public String executeTask(Long taskId) {
        SyncTask task = syncTaskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));

        new Thread(() -> {
            try {
                runTask(task);
            } catch (Exception e) {
                log.error("Task execution failed", e);
            }
        }).start();

        return "Task started successfully";
    }

    private void runTask(SyncTask task) {
        SyncLog syncLog = new SyncLog();
        syncLog.setTaskId(task.getId());
        syncLog.setTaskName(task.getName());
        syncLog.setStartTime(LocalDateTime.now());
        syncLog.setResult("RUNNING");
        syncLog = syncLogRepository.save(syncLog); // Save immediately to show in stats

        int totalCount = 0;
        AtomicInteger totalSyncCountAtomic = new AtomicInteger(0);
        List<Map<String, Object>> nodeDetails = new ArrayList<>();

        try {
            JsonNode flow = objectMapper.readTree(task.getContent());
            JsonNode nodes = flow.get("nodes");

            // ... (rest of parsing)
            JsonNode inputNode = null;
            JsonNode outputNode = null;
            List<JsonNode> mappingNodes = new ArrayList<>();

            for (JsonNode node : nodes) {
                String type = node.path("type").asText();
                String label = node.path("label").asText("");
                if ("input".equals(type)) {
                    inputNode = node;
                } else if ("output".equals(type)) {
                    outputNode = node;
                } else if ("字段映射".equals(label) || "mapping".equals(type)) {
                    mappingNodes.add(node);
                }
            }

            if (inputNode == null || outputNode == null) {
                throw new RuntimeException("Task must have at least one input and one output node");
            }

            // 记录输入节点日志
            Map<String, Object> inputLog = new HashMap<>();
            inputLog.put("nodeId", inputNode.path("id").asText());
            inputLog.put("nodeType", "INPUT");
            inputLog.put("nodeName", "MySQL输入");
            inputLog.put("startTime", LocalDateTime.now().toString());
            
            JsonNode sourceData = inputNode.path("data");
            String sourceSql = sourceData.path("sql").asText();
            inputLog.put("sql", sourceSql);
            inputLog.put("batchSize", sourceData.path("batchSize").asInt(1000));
            nodeDetails.add(inputLog);

            // 记录处理节点日志
            for (JsonNode mNode : mappingNodes) {
                Map<String, Object> mLog = new HashMap<>();
                mLog.put("nodeId", mNode.path("id").asText());
                mLog.put("nodeType", "MAPPING");
                mLog.put("nodeName", "字段映射");
                mLog.put("mappingCount", mNode.path("data").path("mappings").size());
                nodeDetails.add(mLog);
            }

            // 2. Prepare Data Sources
            JsonNode targetData = outputNode.path("data");
            
            Map<String, Object> outputLog = new HashMap<>();
            outputLog.put("nodeId", outputNode.path("id").asText());
            outputLog.put("nodeType", "OUTPUT");
            outputLog.put("nodeName", "MySQL输出");
            outputLog.put("tableName", targetData.path("tableName").asText());
            outputLog.put("writeMode", targetData.path("writeMode").asText("APPEND"));
            nodeDetails.add(outputLog);

            if (sourceData.isMissingNode() || targetData.isMissingNode()) {
                throw new RuntimeException("Node data is missing");
            }

            Long sourceDsId = sourceData.path("dataSourceId").asLong(0L);
            Long targetDsId = targetData.path("dataSourceId").asLong(0L);
            
            if (sourceDsId == 0 || targetDsId == 0) {
                throw new RuntimeException("DataSource ID is missing in configuration");
            }

            DataSource sourceDs = dataSourceService.findById(sourceDsId);
            DataSource targetDs = dataSourceService.findById(targetDsId);

            String sourceUrl = getJdbcUrl(sourceDs);
            String targetUrl = getJdbcUrl(targetDs);

            String targetTable = targetData.path("tableName").asText();
            int batchSize = sourceData.path("batchSize").asInt(1000);
            
            String writeMode = targetData.path("writeMode").asText("APPEND");
            String primaryKey = targetData.path("primaryKey").asText("");
            String conflictStrategy = targetData.path("conflictStrategy").asText("UPDATE");
            
            boolean deleteAfterSync = targetData.path("deleteAfterSync").asBoolean(false);
            String sourcePrimaryKey = targetData.path("sourcePrimaryKey").asText("");
            String sourceTableName = targetData.path("sourceTableName").asText("");

            if (sourceSql.isEmpty() || targetTable.isEmpty()) {
                throw new RuntimeException("SQL or Target Table name is missing");
            }

            // 3. Auto-create or update target table
            List<Map<String, String>> targetFields = new ArrayList<>();
            JsonNode fieldsNode = targetData.path("fields");
            if (fieldsNode.isArray()) {
                for (JsonNode field : fieldsNode) {
                    Map<String, String> f = new HashMap<>();
                    f.put("sourceName", field.path("sourceName").asText(""));
                    f.put("name", field.path("name").asText());
                    f.put("type", field.path("type").asText("VARCHAR(255)"));
                    f.put("comment", field.path("comment").asText(""));
                    f.put("isPk", field.path("isPk").asBoolean() ? "true" : "false");
                    targetFields.add(f);
                }
            }
            
            if (targetFields.isEmpty()) {
                throw new RuntimeException("No output fields configured");
            }

            ensureTargetTable(targetDs, targetUrl, targetTable, targetFields, primaryKey);

            long syncStartTime = System.currentTimeMillis();
            SyncLog finalSyncLog = syncLog;

            // 4. Batch Processing
            try (Connection sourceConn = DriverManager.getConnection(sourceUrl, sourceDs.getUsername(), sourceDs.getPassword())) {
                
                // 获取源数据总数用于进度显示
                int sourceTotal = getSourceCount(sourceConn, sourceSql);
                finalSyncLog.setTotalCount(sourceTotal);
                finalSyncLog.setProcessedCount(0);
                syncLogRepository.save(finalSyncLog);

                // Handle Write Mode: OVERWRITE
                if ("OVERWRITE".equalsIgnoreCase(writeMode)) {
                    try (Connection targetConn = DriverManager.getConnection(targetUrl, targetDs.getUsername(), targetDs.getPassword())) {
                        try (Statement stmt = targetConn.createStatement()) {
                            stmt.execute("SET SESSION lock_wait_timeout = 60");
                            stmt.execute("TRUNCATE TABLE `" + targetTable + "`");
                        }
                        log.info("Truncated table: {}", targetTable);
                    }
                }
                
                List<Future<?>> futures = new ArrayList<>();
                String streamingSql = sourceSql;
                
                try (Statement stmt = sourceConn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                    // Enable MySQL streaming
                    stmt.setFetchSize(Integer.MIN_VALUE);
                    
                    try (ResultSet rs = stmt.executeQuery(streamingSql)) {
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();
                        
                        List<Map<String, Object>> currentBatchData = new ArrayList<>();
                        int batchCount = 0;
                        
                        while (rs.next()) {
                            Map<String, Object> row = new HashMap<>();
                            for (int i = 1; i <= columnCount; i++) {
                                row.put(metaData.getColumnLabel(i), rs.getObject(i));
                            }
                            currentBatchData.add(row);
                            
                            if (currentBatchData.size() >= batchSize) {
                                final List<Map<String, Object>> batchToProcess = currentBatchData;
                                final int batchNum = ++batchCount;
                                
                                futures.add(taskExecutor.submit(() -> {
                                    processBatch(batchToProcess, targetUrl, targetDs, targetTable, targetFields, 
                                               mappingNodes, primaryKey, conflictStrategy, deleteAfterSync, 
                                               sourceUrl, sourceDs, sourcePrimaryKey, sourceTableName, 
                                               finalSyncLog, batchNum, totalSyncCountAtomic);
                                }));
                                
                                currentBatchData = new ArrayList<>();
                            }
                        }
                        
                        // Process remaining data
                        if (!currentBatchData.isEmpty()) {
                            final List<Map<String, Object>> batchToProcess = currentBatchData;
                            final int batchNum = ++batchCount;
                            futures.add(taskExecutor.submit(() -> {
                                processBatch(batchToProcess, targetUrl, targetDs, targetTable, targetFields, 
                                           mappingNodes, primaryKey, conflictStrategy, deleteAfterSync, 
                                           sourceUrl, sourceDs, sourcePrimaryKey, sourceTableName, 
                                           finalSyncLog, batchNum, totalSyncCountAtomic);
                            }));
                        }
                    }
                }
                
                // Wait for all batches to complete
                for (Future<?> future : futures) {
                    future.get();
                }
            }
            
            int totalProcessed = totalSyncCountAtomic.get();
            // 更新节点完成日志
            long duration = System.currentTimeMillis() - syncStartTime;
            inputLog.put("endTime", LocalDateTime.now().toString());
            inputLog.put("rowCount", totalProcessed);
            inputLog.put("durationMs", duration);
            outputLog.put("rowCount", totalProcessed);
            outputLog.put("durationMs", duration);

            syncLog.setResult("SUCCESS");
            syncLog.setMessage("Successfully synchronized " + totalProcessed + " records.");
        } catch (Exception e) {
            log.error("Task execution failed", e);
            syncLog.setResult("FAILURE");
            syncLog.setMessage(e.getMessage());
            // 记录失败时的异常堆栈
            try {
                Map<String, Object> errorLog = new HashMap<>();
                errorLog.put("nodeType", "ERROR");
                errorLog.put("nodeName", "Execution Error");
                errorLog.put("time", LocalDateTime.now().toString());
                errorLog.put("error", e.getMessage());
                nodeDetails.add(errorLog);
            } catch (Exception ex) {
                log.warn("Failed to add error to nodeDetails", ex);
            }
        } finally {
            syncLog.setEndTime(LocalDateTime.now());
            // 确保即使失败也记录当前进度
            syncLog.setProcessedCount(totalSyncCountAtomic.get());
            syncLog.setSyncCount(totalSyncCountAtomic.get());
            syncLog.setDurationMs(Duration.between(syncLog.getStartTime(), syncLog.getEndTime()).toMillis());
            try {
                syncLog.setNodeDetails(objectMapper.writeValueAsString(nodeDetails));
            } catch (Exception e) {
                log.warn("Failed to serialize node details", e);
            }
            syncLogRepository.save(syncLog);
        }
    }

    private void processBatch(List<Map<String, Object>> currentBatch, String targetUrl, DataSource targetDs, 
                             String targetTable, List<Map<String, String>> targetFields, 
                             List<JsonNode> mappingNodes, String primaryKey, String conflictStrategy, 
                             boolean deleteAfterSync, String sourceUrl, DataSource sourceDs, 
                             String sourcePrimaryKey, String sourceTableName, 
                             SyncLog finalSyncLog, int batchNum, AtomicInteger totalSyncCountAtomic) {
        long batchStart = System.currentTimeMillis();
        try (Connection threadTargetConn = DriverManager.getConnection(targetUrl, targetDs.getUsername(), targetDs.getPassword())) {
            // Set session timeout for each worker thread
            try (Statement stmt = threadTargetConn.createStatement()) {
                stmt.execute("SET SESSION innodb_lock_wait_timeout = 120");
            }

            threadTargetConn.setAutoCommit(false);
            
            // Apply intermediate mappings
            long mappingStart = System.currentTimeMillis();
            List<Map<String, Object>> currentData = applyMapping(currentBatch, mappingNodes);
            
            // Apply Output Node Field Mapping
            List<Map<String, Object>> finalMappedData = new ArrayList<>();
            for (Map<String, Object> row : currentData) {
                Map<String, Object> mappedRow = new HashMap<>();
                for (Map<String, String> fieldDef : targetFields) {
                    String sourceName = fieldDef.get("sourceName");
                    String targetName = fieldDef.get("name");
                    
                    Object value;
                    if (sourceName != null && !sourceName.isEmpty()) {
                        value = row.get(sourceName);
                    } else {
                        // 如果没有配置源字段名，尝试按目标字段名寻找
                        value = row.get(targetName);
                    }
                    
                    // 类型转换支持
                    String targetType = fieldDef.get("type");
                    mappedRow.put(targetName, convertType(value, targetType));
                }
                finalMappedData.add(mappedRow);
            }
            long mappingEnd = System.currentTimeMillis();

            long insertStart = System.currentTimeMillis();
            insertBatch(threadTargetConn, targetTable, targetFields, finalMappedData, primaryKey, conflictStrategy);
            threadTargetConn.commit();
            long insertEnd = System.currentTimeMillis();
            
            int currentProcessed = totalSyncCountAtomic.addAndGet(currentBatch.size());
            
            // 更新进度 (使用原生SQL以提高并发性能)
            syncLogRepository.updateProcessedCount(finalSyncLog.getId(), currentProcessed);
            
            long deleteDuration = 0;
            // Delete from source if enabled
            if (deleteAfterSync && !sourcePrimaryKey.isEmpty() && !sourceTableName.isEmpty()) {
                long deleteStart = System.currentTimeMillis();
                try (Connection threadSourceConn = DriverManager.getConnection(sourceUrl, sourceDs.getUsername(), sourceDs.getPassword())) {
                    deleteFromSource(threadSourceConn, sourceTableName, sourcePrimaryKey, currentBatch);
                }
                deleteDuration = System.currentTimeMillis() - deleteStart;
            }
            
            long totalBatchDuration = System.currentTimeMillis() - batchStart;
            log.info("Batch {} processed: size={}, total={}ms [Mapping: {}ms, Insert: {}ms, Delete: {}ms]", 
                    batchNum, currentBatch.size(), totalBatchDuration, (mappingEnd - mappingStart), (insertEnd - insertStart), deleteDuration);

        } catch (Exception e) {
            log.error("Batch processing failed for batch " + batchNum, e);
            throw new RuntimeException(e);
        }
    }

    private int getSourceCount(Connection conn, String sql) {
        // Remove any LIMIT/OFFSET from the SQL if we're wrapping it to get total count
        String lowerSql = sql.toLowerCase();
        if (lowerSql.contains("limit")) {
            return -1; // If it already has a limit, total count might be misleading
        }
        
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") as t";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            log.warn("Failed to get source count: {}", e.getMessage());
        }
        return -1;
    }

    private String getJdbcUrl(DataSource ds) {
        return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                ds.getHost(), ds.getPort(), ds.getDatabaseName());
    }

    private void ensureTargetTable(DataSource ds, String url, String tableName, List<Map<String, String>> fields, String primaryKey) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword())) {
            // Set session timeout for DDL operations
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET SESSION innodb_lock_wait_timeout = 60");
            }
            DatabaseMetaData metaData = conn.getMetaData();
            
            try (ResultSet tables = metaData.getTables(null, null, null, new String[]{"TABLE"})) {
                boolean exists = false;
                while (tables.next()) {
                    if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                        exists = true;
                        break;
                    }
                }
                
                if (!exists) {
                    // Create Table
                    StringBuilder sql = new StringBuilder("CREATE TABLE `").append(tableName).append("` (");
                    
                    // If user specified a primary key that exists in the fields list, use it.
                    // Otherwise, if no 'id' field, add an auto-incrementing 'id'.
                    boolean pkFound = false;
                    if (primaryKey != null && !primaryKey.isEmpty()) {
                        pkFound = fields.stream().anyMatch(f -> primaryKey.equalsIgnoreCase(f.get("name")));
                    }
                    
                    boolean hasIdField = fields.stream().anyMatch(f -> "id".equalsIgnoreCase(f.get("name")));
                    
                    if (!pkFound && !hasIdField) {
                        sql.append("`id` INT AUTO_INCREMENT PRIMARY KEY");
                    }
                    
                    for (int i = 0; i < fields.size(); i++) {
                        Map<String, String> field = fields.get(i);
                        String name = field.get("name");
                        String comment = field.get("comment");
                        boolean isPk = "true".equals(field.get("isPk"));
                        
                        // Handle comma
                        if (i > 0 || (!pkFound && !hasIdField)) {
                            sql.append(", ");
                        }
                        
                        sql.append("`").append(name).append("` ").append(sanitizeType(field.get("type")));
                        
                        // If this is marked as PK in field config or matches the global primaryKey setting
                        if (isPk || name.equalsIgnoreCase(primaryKey)) {
                            sql.append(" PRIMARY KEY");
                        }

                        if (comment != null && !comment.isEmpty()) {
                            sql.append(" COMMENT '").append(comment.replace("'", "''")).append("'");
                        }
                    }
                    sql.append(")");
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(sql.toString());
                    }
                    log.info("Created table: {} with primary key: {}", tableName, primaryKey);
                } else {
                    // Check and Update Fields
                    DatabaseMetaData md = conn.getMetaData();
                    for (Map<String, String> field : fields) {
                        String colName = field.get("name");
                        boolean colExists = false;
                        try (ResultSet cols = md.getColumns(null, null, tableName, colName)) {
                            if (cols.next()) {
                                colExists = true;
                            }
                        }
                        
                        if (!colExists) {
                            String commentClause = "";
                            String comment = field.get("comment");
                            if (comment != null && !comment.isEmpty()) {
                                commentClause = String.format(" COMMENT '%s'", comment.replace("'", "''"));
                            }
                            
                            String sql = String.format("ALTER TABLE `%s` ADD COLUMN `%s` %s%s", 
                                    tableName, colName, sanitizeType(field.get("type")), commentClause);
                            try (Statement stmt = conn.createStatement()) {
                                stmt.execute(sql);
                            }
                            log.info("Added column {} to table {}", colName, tableName);
                        }
                    }

                    // IMPORTANT: Ensure Primary Key exists for Upsert to work
                    if (primaryKey != null && !primaryKey.isEmpty()) {
                        boolean hasPk = false;
                        try (ResultSet pks = md.getPrimaryKeys(null, null, tableName)) {
                            if (pks.next()) {
                                hasPk = true;
                            }
                        }
                        
                        if (!hasPk) {
                            // If no primary key exists, try to add it
                            try (Statement stmt = conn.createStatement()) {
                                String addPkSql = String.format("ALTER TABLE `%s` ADD PRIMARY KEY (`%s`)", tableName, primaryKey);
                                stmt.execute(addPkSql);
                                log.info("Added primary key constraint on {} for table {}", primaryKey, tableName);
                            } catch (SQLException e) {
                                log.warn("Could not add primary key constraint: {}. Upsert might not work if no unique index exists.", e.getMessage());
                            }
                        }
                    }
                }
            }
        }
    }

    private String sanitizeType(String type) {
        if (type == null || type.isEmpty()) return "VARCHAR(255)";
        String upperType = type.toUpperCase();
        if (upperType.equals("VARCHAR") || upperType.equals("STRING")) {
            return "VARCHAR(255)";
        }
        if (upperType.equals("INT") || upperType.equals("INTEGER")) {
            return "INT";
        }
        if (upperType.equals("DATETIME") || upperType.equals("TIMESTAMP")) {
            return "DATETIME";
        }
        return type;
    }

    private List<Map<String, Object>> applyMapping(List<Map<String, Object>> data, List<JsonNode> mappingNodes) {
        if (mappingNodes.isEmpty()) return data;

        List<Map<String, Object>> currentData = data;
        
        // 按顺序应用所有映射节点
        for (JsonNode mappingNode : mappingNodes) {
            JsonNode mappings = mappingNode.path("data").path("mappings");
            if (!mappings.isArray() || mappings.isEmpty()) continue;
            
            List<Map<String, Object>> nextData = new ArrayList<>();
            for (Map<String, Object> row : currentData) {
                Map<String, Object> newRow = new HashMap<>();
                for (JsonNode m : mappings) {
                    String source = m.path("source").asText();
                    String target = m.path("target").asText();
                    if (!source.isEmpty() && row.containsKey(source)) {
                        newRow.put(target.isEmpty() ? source : target, row.get(source));
                    }
                }
                nextData.add(newRow);
            }
            currentData = nextData;
        }
        
        return currentData;
    }

    private void insertBatch(Connection conn, String tableName, List<Map<String, String>> fields, 
                             List<Map<String, Object>> data, String primaryKey, String conflictStrategy) throws SQLException {
        if (data.isEmpty()) return;

        boolean useUpsert = !primaryKey.isEmpty() && "UPDATE".equalsIgnoreCase(conflictStrategy);
        boolean useIgnore = !primaryKey.isEmpty() && "IGNORE".equalsIgnoreCase(conflictStrategy);

        StringBuilder sql = new StringBuilder();
        if (useIgnore) {
            sql.append("INSERT IGNORE INTO `").append(tableName).append("` (");
        } else {
            sql.append("INSERT INTO `").append(tableName).append("` (");
        }
        
        StringBuilder values = new StringBuilder("VALUES (");
        List<String> colNames = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            String col = fields.get(i).get("name");
            colNames.add(col);
            sql.append("`").append(col).append("` ");
            values.append("?");
            if (i < fields.size() - 1) {
                sql.append(", ");
                values.append(", ");
            }
        }
        sql.append(") ").append(values).append(")");

        if (useUpsert) {
            sql.append(" ON DUPLICATE KEY UPDATE ");
            List<String> updateParts = new ArrayList<>();
            for (String col : colNames) {
                if (col.equalsIgnoreCase(primaryKey)) continue;
                // Use VALUES(col) to ensure update with current values
                updateParts.add("`" + col + "` = VALUES(`" + col + "`) ");
            }
            sql.append(String.join(", ", updateParts));
        }

        String finalSql = sql.toString();
        
        int maxRetries = 5;
        int retryCount = 0;
        SQLException lastException = null;
        Random random = new Random();

        while (retryCount < maxRetries) {
            try (PreparedStatement pstmt = conn.prepareStatement(finalSql)) {
                for (Map<String, Object> row : data) {
                    for (int i = 0; i < colNames.size(); i++) {
                        pstmt.setObject(i + 1, row.get(colNames.get(i)));
                    }
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                return; // Success
            } catch (SQLException e) {
                lastException = e;
                // MySQL Error Codes: 1205 (Lock wait timeout exceeded), 1213 (Deadlock found)
                if (e.getErrorCode() == 1205 || e.getErrorCode() == 1213) {
                    retryCount++;
                    log.warn("Database lock issue (code: {}). Retrying {}/{}...", e.getErrorCode(), retryCount, maxRetries);
                    try {
                        // Wait with exponential backoff and jitter
                        long sleepTime = (long) (Math.pow(2, retryCount) * 1000) + random.nextInt(1000);
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                } else {
                    throw e; // Other SQL exceptions should not be retried
                }
            }
        }
        throw lastException;
    }

    private void deleteFromSource(Connection conn, String tableName, String primaryKey, List<Map<String, Object>> data) throws SQLException {
        if (data.isEmpty()) return;

        StringBuilder sql = new StringBuilder("DELETE FROM `")
                .append(tableName)
                .append("` WHERE `")
                .append(primaryKey)
                .append("` = ?");

        String finalSql = sql.toString();
        int maxRetries = 5;
        int retryCount = 0;
        SQLException lastException = null;
        Random random = new Random();

        while (retryCount < maxRetries) {
            try (PreparedStatement pstmt = conn.prepareStatement(finalSql)) {
                for (Map<String, Object> row : data) {
                    Object pkValue = row.get(primaryKey);
                    if (pkValue == null) {
                        // Try case-insensitive lookup
                        for (String key : row.keySet()) {
                            if (key.equalsIgnoreCase(primaryKey)) {
                                pkValue = row.get(key);
                                break;
                            }
                        }
                    }

                    if (pkValue != null) {
                        pstmt.setObject(1, pkValue);
                        pstmt.addBatch();
                    }
                }
                int[] results = pstmt.executeBatch();
                log.info("Deleted {} records from source table {}", results.length, tableName);
                return; // Success
            } catch (SQLException e) {
                lastException = e;
                if (e.getErrorCode() == 1205 || e.getErrorCode() == 1213) {
                    retryCount++;
                    log.warn("Database lock issue in deleteFromSource (code: {}). Retrying {}/{}...", e.getErrorCode(), retryCount, maxRetries);
                    try {
                        long sleepTime = (long) (Math.pow(2, retryCount) * 1000) + random.nextInt(1000);
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                } else {
                    throw e;
                }
            }
        }
        throw lastException;
    }

    private Object convertType(Object value, String targetType) {
        if (value == null) return null;
        if (targetType == null) return value;

        String type = targetType.toUpperCase();
        try {
            if (type.contains("INT") || type.contains("BIGINT")) {
                if (value instanceof Number) return ((Number) value).longValue();
                return Long.parseLong(value.toString());
            } else if (type.contains("DECIMAL") || type.contains("DOUBLE") || type.contains("FLOAT")) {
                if (value instanceof Number) return ((Number) value).doubleValue();
                return Double.parseDouble(value.toString());
            } else if (type.contains("DATETIME") || type.contains("TIMESTAMP")) {
                // Keep as is if it's already a date/time object, otherwise string
                return value;
            } else if (type.contains("BOOLEAN")) {
                if (value instanceof Boolean) return value;
                String s = value.toString().toLowerCase();
                return "true".equals(s) || "1".equals(s) || "yes".equals(s);
            } else if (type.contains("JSON")) {
                // If it's already a map or list, maybe stringify it? 
                // For now, keep as is and let JDBC handle it
                return value;
            }
        } catch (Exception e) {
            log.warn("Failed to convert value '{}' to type {}: {}", value, targetType, e.getMessage());
        }
        
        return value;
    }
}
