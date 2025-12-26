package com.datasync.tool.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sync_task")
public class SyncTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type = "TASK"; // TASK, FOLDER

    private Long parentId;

    private String description;

    @Column(name = "cron_expression")
    private String cron = "0 0 * * * ?"; // 默认每小时执行一次

    @Column(columnDefinition = "TEXT")
    private String content; // JSON string representing the flow (nodes and edges)

    private String status; // ENABLED, DISABLED

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "DISABLED";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
