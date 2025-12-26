package com.datasync.tool.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sync_log")
public class SyncLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "task_name")
    private String taskName;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    private String result; // SUCCESS, FAILURE

    @Column(columnDefinition = "TEXT")
    private String message; // Execution info or error message

    @Column(name = "sync_count")
    private Integer syncCount; // Number of records synchronized

    @Column(name = "total_count")
    private Integer totalCount; // Total number of records to be synchronized

    @Column(name = "processed_count")
    private Integer processedCount; // Number of records processed so far

    @Column(name = "duration_ms")
    private Long durationMs; // Duration in milliseconds

    @Column(columnDefinition = "LONGTEXT")
    private String nodeDetails; // JSON detailing each node's execution (SQL, count, time, etc.)

    @PrePersist
    protected void onCreate() {
        if (startTime == null) startTime = LocalDateTime.now();
    }
}
