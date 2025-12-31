package com.datasync.tool.service;

import com.datasync.tool.entity.DataSource;
import com.datasync.tool.entity.DatabaseExportRecord;
import com.datasync.tool.repository.DatabaseExportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseOpsService {
    private final DataSourceService dataSourceService;
    private final DatabaseExportRepository exportRepository;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    private static final String DEFAULT_EXPORT_DIR = "exports";

    public List<String> getTables(Long dataSourceId) {
        DataSource ds = dataSourceService.findById(dataSourceId);
        String url = getJdbcUrl(ds);
        List<String> tables = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword())) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables(ds.getDatabaseName(), null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get tables: " + e.getMessage(), e);
        }
        return tables;
    }

    public List<Map<String, Object>> getTableStructure(Long dataSourceId, String tableName) {
        DataSource ds = dataSourceService.findById(dataSourceId);
        String url = getJdbcUrl(ds);
        List<Map<String, Object>> columns = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword())) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getColumns(ds.getDatabaseName(), null, tableName, "%")) {
                while (rs.next()) {
                    Map<String, Object> col = new HashMap<>();
                    col.put("name", rs.getString("COLUMN_NAME"));
                    col.put("type", rs.getString("TYPE_NAME"));
                    col.put("size", rs.getInt("COLUMN_SIZE"));
                    col.put("nullable", rs.getString("IS_NULLABLE"));
                    col.put("defaultValue", rs.getString("COLUMN_DEF"));
                    col.put("comment", rs.getString("REMARKS"));
                    columns.add(col);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get table structure: " + e.getMessage(), e);
        }
        return columns;
    }

    public Map<String, Object> getTableData(Long dataSourceId, String tableName, int page, int size, 
                                           String sortField, String sortOrder, 
                                           String filterField, String filterValue, String customWhere) {
        DataSource ds = dataSourceService.findById(dataSourceId);
        String url = getJdbcUrl(ds);
        List<Map<String, Object>> data = new ArrayList<>();
        long total = 0;

        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword())) {
            // Get columns for validation
            List<String> columns = new ArrayList<>();
            DatabaseMetaData metaDataObj = conn.getMetaData();
            try (ResultSet rs = metaDataObj.getColumns(ds.getDatabaseName(), null, tableName, "%")) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }

            StringBuilder whereClause = new StringBuilder();
            List<Object> params = new ArrayList<>();

            if (customWhere != null && !customWhere.trim().isEmpty()) {
                whereClause.append(" WHERE ").append(customWhere);
            } else if (filterField != null && !filterField.trim().isEmpty() && filterValue != null && !filterValue.trim().isEmpty()) {
                if (columns.contains(filterField)) {
                    whereClause.append(" WHERE ").append(filterField).append(" LIKE ?");
                    params.add("%" + filterValue + "%");
                }
            }

            // Get total count
            String countSql = "SELECT COUNT(*) FROM " + tableName + whereClause.toString();
            try (PreparedStatement pstmt = conn.prepareStatement(countSql)) {
                for (int i = 0; i < params.size(); i++) {
                    pstmt.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getLong(1);
                    }
                }
            }

            // Build data SQL
            StringBuilder dataSql = new StringBuilder("SELECT * FROM ").append(tableName);
            dataSql.append(whereClause);

            if (sortField != null && !sortField.trim().isEmpty() && columns.contains(sortField)) {
                dataSql.append(" ORDER BY ").append(sortField);
                if ("descending".equalsIgnoreCase(sortOrder) || "DESC".equalsIgnoreCase(sortOrder)) {
                    dataSql.append(" DESC ");
                } else {
                    dataSql.append(" ASC ");
                }
            }

            dataSql.append(String.format(" LIMIT %d, %d", (page - 1) * size, size));
            
            try (PreparedStatement pstmt = conn.prepareStatement(dataSql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    pstmt.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = pstmt.executeQuery()) {
                    ResultSetMetaData rsMetaData = rs.getMetaData();
                    int columnCount = rsMetaData.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(rsMetaData.getColumnName(i), rs.getObject(i));
                        }
                        data.add(row);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get table data: " + e.getMessage(), e);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("data", data);
        return result;
    }

    public DatabaseExportRecord startExport(Long dataSourceId, String tableName, String format) {
        DatabaseExportRecord record = new DatabaseExportRecord();
        record.setDataSourceId(dataSourceId);
        record.setTableName(tableName);
        record.setFormat(format.toUpperCase());
        record.setStatus("PENDING");
        record = exportRepository.save(record);

        executeExport(record.getId());
        return record;
    }

    @Async
    public void executeExport(Long recordId) {
        DatabaseExportRecord record = exportRepository.findById(recordId).orElse(null);
        if (record == null) return;

        long startTime = System.currentTimeMillis();
        try {
            record.setStatus("PROCESSING");
            exportRepository.save(record);

            DataSource ds = dataSourceService.findById(record.getDataSourceId());
            String url = getJdbcUrl(ds);

            String exportPath = systemConfigService.getConfig("ops.export.path", DEFAULT_EXPORT_DIR);
            Path dirPath = Paths.get(exportPath);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            String fileName = String.format("%s_%s_%d.%s", record.getTableName(), 
                record.getFormat().toLowerCase(), System.currentTimeMillis(), 
                record.getFormat().equalsIgnoreCase("EXCEL") ? "xlsx" : record.getFormat().toLowerCase());
            File file = new File(exportPath, fileName);

            try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM " + record.getTableName())) {

                if ("SQL".equalsIgnoreCase(record.getFormat())) {
                    exportToSql(rs, file, record.getTableName());
                } else if ("JSON".equalsIgnoreCase(record.getFormat())) {
                    exportToJson(rs, file);
                } else if ("EXCEL".equalsIgnoreCase(record.getFormat())) {
                    exportToExcel(rs, file, record.getTableName());
                }
            }

            record.setStatus("COMPLETED");
            record.setFilePath(file.getAbsolutePath());
            record.setCompletedAt(LocalDateTime.now());
            record.setDuration(System.currentTimeMillis() - startTime);
            exportRepository.save(record);

        } catch (Exception e) {
            log.error("Export failed", e);
            record.setStatus("FAILED");
            record.setErrorMessage(e.getMessage());
            record.setCompletedAt(LocalDateTime.now());
            record.setDuration(System.currentTimeMillis() - startTime);
            exportRepository.save(record);
        }
    }

    private void exportToSql(ResultSet rs, File file, String tableName) throws Exception {
        try (FileWriter writer = new FileWriter(file)) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            while (rs.next()) {
                StringBuilder sb = new StringBuilder();
                sb.append("INSERT INTO ").append(tableName).append(" (");
                for (int i = 1; i <= columnCount; i++) {
                    sb.append(metaData.getColumnName(i));
                    if (i < columnCount) sb.append(", ");
                }
                sb.append(") VALUES (");
                for (int i = 1; i <= columnCount; i++) {
                    Object val = rs.getObject(i);
                    if (val == null) {
                        sb.append("NULL");
                    } else if (val instanceof String || val instanceof LocalDateTime || val instanceof Timestamp) {
                        sb.append("'").append(val.toString().replace("'", "''")).append("'");
                    } else {
                        sb.append(val);
                    }
                    if (i < columnCount) sb.append(", ");
                }
                sb.append(");\n");
                writer.write(sb.toString());
            }
        }
    }

    private void exportToJson(ResultSet rs, File file) throws Exception {
        List<Map<String, Object>> data = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(metaData.getColumnName(i), rs.getObject(i));
            }
            data.add(row);
        }
        objectMapper.writeValue(file, data);
    }

    private void exportToExcel(ResultSet rs, File file, String tableName) throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(file)) {
            Sheet sheet = workbook.createSheet(tableName);
            
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            // Header
            Row headerRow = sheet.createRow(0);
            for (int i = 1; i <= columnCount; i++) {
                Cell cell = headerRow.createCell(i - 1);
                cell.setCellValue(metaData.getColumnName(i));
            }
            
            // Data
            int rowNum = 1;
            while (rs.next()) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 1; i <= columnCount; i++) {
                    Cell cell = row.createCell(i - 1);
                    Object val = rs.getObject(i);
                    if (val != null) {
                        cell.setCellValue(val.toString());
                    }
                }
            }
            workbook.write(fos);
        }
    }

    private String getJdbcUrl(DataSource ds) {
        if ("MYSQL".equalsIgnoreCase(ds.getType())) {
            return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                    ds.getHost(), ds.getPort(), ds.getDatabaseName());
        }
        // Add other database types if needed
        throw new UnsupportedOperationException("Unsupported database type: " + ds.getType());
    }

    public List<DatabaseExportRecord> getExportRecords() {
        return exportRepository.findAllByOrderByCreatedAtDesc();
    }

    public DatabaseExportRecord getExportRecord(Long id) {
        return exportRepository.findById(id).orElseThrow(() -> new RuntimeException("Record not found"));
    }
}
