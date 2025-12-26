package com.datasync.tool.controller;

import com.datasync.tool.entity.SyncLog;
import com.datasync.tool.repository.SyncLogRepository;
import com.datasync.tool.entity.SyncTask;
import com.datasync.tool.service.SyncTaskService;
import com.datasync.tool.service.TaskExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;
import org.quartz.CronExpression;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TaskController {
    private final SyncTaskService syncTaskService;
    private final TaskExecutionService taskExecutionService;
    private final SyncLogRepository syncLogRepository;

    @GetMapping("/{id}/latest-log")
    public ResponseEntity<SyncLog> getLatestLog(@PathVariable Long id) {
        return syncLogRepository.findFirstByTaskIdOrderByStartTimeDesc(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cron/next-executions")
    public ResponseEntity<List<Date>> getNextExecutions(@RequestParam String cron, @RequestParam(defaultValue = "5") int count) {
        try {
            if (!CronExpression.isValidExpression(cron)) {
                return ResponseEntity.badRequest().build();
            }
            CronExpression ce = new CronExpression(cron);
            List<Date> nextExecutions = new ArrayList<>();
            Date lastDate = new Date();
            for (int i = 0; i < count; i++) {
                Date nextDate = ce.getNextValidTimeAfter(lastDate);
                if (nextDate != null) {
                    nextExecutions.add(nextDate);
                    lastDate = nextDate;
                } else {
                    break;
                }
            }
            return ResponseEntity.ok(nextExecutions);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public List<SyncTask> getAll() {
        return syncTaskService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SyncTask> getById(@PathVariable Long id) {
        return syncTaskService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SyncTask> create(@RequestBody SyncTask task) {
        SyncTask savedTask = syncTaskService.save(task);
        taskExecutionService.updateTaskSchedule(savedTask);
        return ResponseEntity.ok(savedTask);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SyncTask> update(@PathVariable Long id, @RequestBody SyncTask task) {
        task.setId(id);
        SyncTask savedTask = syncTaskService.save(task);
        taskExecutionService.updateTaskSchedule(savedTask);
        return ResponseEntity.ok(savedTask);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        syncTaskService.deleteById(id);
        // Also need to remove from scheduler
        SyncTask dummyTask = new SyncTask();
        dummyTask.setId(id);
        dummyTask.setStatus("DISABLED");
        taskExecutionService.updateTaskSchedule(dummyTask);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<String> execute(@PathVariable Long id) {
        SyncTask task = syncTaskService.findById(id).orElseThrow();
        if ("FOLDER".equals(task.getType())) {
            return ResponseEntity.badRequest().body("文件夹类型无法执行");
        }
        String result = taskExecutionService.executeTask(id);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/copy")
    public ResponseEntity<SyncTask> copy(@PathVariable Long id) {
        SyncTask original = syncTaskService.findById(id).orElseThrow();
        SyncTask copy = new SyncTask();
        copy.setName(original.getName() + "_copy");
        copy.setContent(original.getContent());
        copy.setCron(original.getCron());
        copy.setDescription(original.getDescription());
        copy.setStatus("DISABLED");
        copy.setType(original.getType());
        copy.setParentId(original.getParentId());
        SyncTask saved = syncTaskService.save(copy);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/import")
    public ResponseEntity<SyncTask> importTask(@RequestBody SyncTask task) {
        task.setId(null);
        SyncTask savedTask = syncTaskService.save(task);
        taskExecutionService.updateTaskSchedule(savedTask);
        return ResponseEntity.ok(savedTask);
    }
}
