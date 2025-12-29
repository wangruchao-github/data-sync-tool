package com.datasync.tool.service;

import com.datasync.tool.entity.DataSource;
import com.datasync.tool.repository.DataSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DataSourceService {
    private final DataSourceRepository dataSourceRepository;

    public List<DataSource> findAll() {
        return dataSourceRepository.findAll();
    }

    public DataSource findById(Long id) {
        return dataSourceRepository.findById(id).orElseThrow(() -> new RuntimeException("Data source not found"));
    }

    public DataSource save(DataSource dataSource) {
        return dataSourceRepository.save(dataSource);
    }

    public void deleteById(Long id) {
        dataSourceRepository.deleteById(id);
    }

    public String getJdbcUrl(DataSource ds) {
        if ("MYSQL".equalsIgnoreCase(ds.getType())) {
            return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                    ds.getHost(), ds.getPort(), ds.getDatabaseName());
        }
        // Add other database types here
        return null;
    }

    public Connection getConnection(DataSource ds) throws SQLException {
        return DriverManager.getConnection(getJdbcUrl(ds), ds.getUsername(), ds.getPassword());
    }

    public List<Map<String, Object>> previewSql(Long dataSourceId, String sql) {
        DataSource ds = findById(dataSourceId);
        String url = getJdbcUrl(ds);
        
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = getConnection(ds);
             Statement stmt = conn.createStatement()) {
            
            // Limit to 10 rows for preview
            String previewSql = sql.trim();
            if (!previewSql.toLowerCase().contains("limit")) {
                previewSql += " LIMIT 10";
            }
            
            try (ResultSet rs = stmt.executeQuery(previewSql)) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(metaData.getColumnName(i), rs.getObject(i));
                    }
                    results.add(row);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("SQL preview failed: " + e.getMessage(), e);
        }
        return results;
    }

    public List<String> getColumns(Long dataSourceId, String sql) {
        DataSource ds = findById(dataSourceId);
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                ds.getHost(), ds.getPort(), ds.getDatabaseName());
        
        List<String> columns = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword());
             Statement stmt = conn.createStatement()) {
            
            String schemaSql = sql.trim();
            if (!schemaSql.toLowerCase().contains("limit")) {
                schemaSql += " LIMIT 0";
            }
            
            try (ResultSet rs = stmt.executeQuery(schemaSql)) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(metaData.getColumnName(i));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get columns: " + e.getMessage(), e);
        }
        return columns;
    }

    public boolean testConnection(DataSource dataSource) {
        if ("MYSQL".equalsIgnoreCase(dataSource.getType())) {
            String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                    dataSource.getHost(), dataSource.getPort(), dataSource.getDatabaseName());
            try (Connection conn = DriverManager.getConnection(url, dataSource.getUsername(), dataSource.getPassword())) {
                return conn != null && !conn.isClosed();
            } catch (Exception e) {
                return false;
            }
        }
        // TODO: Implement other types
        return false;
    }

    public List<Map<String, Object>> getTableColumns(Long dataSourceId, String tableName) {
        DataSource ds = findById(dataSourceId);
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                ds.getHost(), ds.getPort(), ds.getDatabaseName());
        
        List<Map<String, Object>> columns = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword())) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            // Get Primary Keys
            Map<String, Boolean> primaryKeys = new HashMap<>();
            try (ResultSet pks = metaData.getPrimaryKeys(null, null, tableName)) {
                while (pks.next()) {
                    primaryKeys.put(pks.getString("COLUMN_NAME"), true);
                }
            }
            
            // Get Columns
            try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {
                while (rs.next()) {
                    Map<String, Object> column = new HashMap<>();
                    String name = rs.getString("COLUMN_NAME");
                    column.put("name", name);
                    String type = rs.getString("TYPE_NAME");
                    int size = rs.getInt("COLUMN_SIZE");
                    if (type.equalsIgnoreCase("VARCHAR") || type.equalsIgnoreCase("CHAR")) {
                        type = type + "(" + size + ")";
                    }
                    column.put("type", type);
                    column.put("comment", rs.getString("REMARKS"));
                    column.put("isPk", primaryKeys.getOrDefault(name, false));
                    columns.add(column);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get table columns: " + e.getMessage(), e);
        }
        return columns;
    }
}
