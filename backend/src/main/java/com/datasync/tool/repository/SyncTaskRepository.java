package com.datasync.tool.repository;

import com.datasync.tool.entity.SyncTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SyncTaskRepository extends JpaRepository<SyncTask, Long> {
    long countByType(String type);
    List<SyncTask> findAllByType(String type);
}
