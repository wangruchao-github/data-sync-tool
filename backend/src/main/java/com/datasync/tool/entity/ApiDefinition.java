package com.datasync.tool.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "api_definition")
public class ApiDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(nullable = false, length = 10)
    private String method = "GET";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "api_type")
    private String apiType = "PRIVATE"; // PUBLIC/PRIVATE

    @Column(nullable = false)
    private String status = "DRAFT"; // DRAFT/ONLINE/OFFLINE

    private String version = "v1";

    @Column(columnDefinition = "LONGTEXT")
    private String content; // 存储节点编排的 JSON 数据

    @Column(name = "response_example", columnDefinition = "LONGTEXT")
    private String responseExample; // 响应示例

    @CreationTimestamp
    @Column(name = "created_time", updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;
}
