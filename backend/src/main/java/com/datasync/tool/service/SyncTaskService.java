package com.datasync.tool.service;

import com.datasync.tool.entity.SyncTask;
import com.datasync.tool.repository.SyncTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SyncTaskService {
    private final SyncTaskRepository syncTaskRepository;

    public List<SyncTask> findAll() {
        return syncTaskRepository.findAll();
    }

    public Optional<SyncTask> findById(Long id) {
        return syncTaskRepository.findById(id);
    }

    public SyncTask save(SyncTask task) {
        return syncTaskRepository.save(task);
    }

    public void deleteById(Long id) {
        syncTaskRepository.deleteById(id);
    }
}
