package com.datasync.tool.repository;

import com.datasync.tool.entity.SyncLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {
    Page<SyncLog> findByTaskIdOrderByStartTimeDesc(Long taskId, Pageable pageable);
    Page<SyncLog> findAllByOrderByStartTimeDesc(Pageable pageable);
    List<SyncLog> findByTaskIdOrderByStartTimeDesc(Long taskId);
    List<SyncLog> findAllByOrderByStartTimeDesc();
    Optional<SyncLog> findFirstByTaskIdOrderByStartTimeDesc(Long taskId);

    @Query("SELECT s FROM SyncLog s WHERE (:taskId IS NULL OR s.taskId = :taskId) " +
           "AND (:startTime IS NULL OR s.startTime >= :startTime) " +
           "AND (:endTime IS NULL OR s.startTime <= :endTime) " +
           "ORDER BY s.startTime DESC")
    Page<SyncLog> findWithFilters(Long taskId, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    long countByStartTimeGreaterThanEqual(LocalDateTime startTime);
    long countByStartTimeGreaterThanEqualAndResult(LocalDateTime startTime, String result);
    List<SyncLog> findAllByResult(String result);

    @Modifying
    @Transactional
    @Query("UPDATE SyncLog s SET s.processedCount = :count WHERE s.id = :id")
    void updateProcessedCount(@Param("id") Long id, @Param("count") Integer count);

    @Query(value = "SELECT DATE(start_time) as date, COUNT(*) as count FROM sync_log " +
            "WHERE start_time >= :startTime " +
            "GROUP BY DATE(start_time) ORDER BY DATE(start_time) ASC", nativeQuery = true)
    List<Object[]> countByDay(@Param("startTime") LocalDateTime startTime);

    @Query(value = "SELECT l.task_id, t.name, COUNT(*) as count FROM sync_log l " +
            "JOIN sync_task t ON l.task_id = t.id " +
            "WHERE l.start_time >= :startTime " +
            "GROUP BY l.task_id, t.name ORDER BY count DESC LIMIT 10", nativeQuery = true)
    List<Object[]> countByTask(@Param("startTime") LocalDateTime startTime);
}
