package com.datasync.tool.repository;

import com.datasync.tool.entity.DatabaseExportRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DatabaseExportRepository extends JpaRepository<DatabaseExportRecord, Long> {
    List<DatabaseExportRecord> findAllByOrderByCreatedAtDesc();
}
