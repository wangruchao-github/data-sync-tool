package com.datasync.tool.controller;

import com.datasync.tool.entity.SyncLog;
import com.datasync.tool.entity.SyncTask;
import com.datasync.tool.repository.SyncLogRepository;
import com.datasync.tool.repository.SyncTaskRepository;
import lombok.RequiredArgsConstructor;
import org.quartz.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final SyncTaskRepository taskRepository;
    private final SyncLogRepository logRepository;
    private final Scheduler scheduler;

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 总任务数
        stats.put("totalTasks", taskRepository.countByType("TASK"));
        
        // 今日统计
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        stats.put("todayTotal", logRepository.countByStartTimeGreaterThanEqual(todayStart));
        stats.put("todaySuccess", logRepository.countByStartTimeGreaterThanEqualAndResult(todayStart, "SUCCESS"));
        stats.put("todayFailure", logRepository.countByStartTimeGreaterThanEqualAndResult(todayStart, "FAILURE"));
        stats.put("todayRunning", logRepository.countByStartTimeGreaterThanEqualAndResult(todayStart, "RUNNING"));
        
        // 最近7天趋势
        LocalDateTime sevenDaysAgo = LocalDateTime.of(LocalDate.now().minusDays(6), LocalTime.MIN);
        List<Object[]> dayStats = logRepository.countByDay(sevenDaysAgo);
        
        Map<String, Long> trendMap = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            trendMap.put(LocalDate.now().minusDays(i).toString(), 0L);
        }
        
        for (Object[] row : dayStats) {
            trendMap.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        
        stats.put("trend", trendMap);
        
        // 任务执行排行 (最近7天)
        List<Object[]> rankingStats = logRepository.countByTask(sevenDaysAgo);
        List<Map<String, Object>> ranking = rankingStats.stream().map(row -> {
            Map<String, Object> map = new HashMap<>();
            map.put("taskId", row[0]);
            map.put("taskName", row[1]);
            map.put("count", row[2]);
            return map;
        }).collect(Collectors.toList());
        stats.put("ranking", ranking);
        
        return stats;
    }

    @GetMapping("/tasks")
    public List<Map<String, Object>> getTaskMonitor() {
        List<SyncTask> tasks = taskRepository.findAllByType("TASK");
        return tasks.stream().map(task -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", task.getId());
            map.put("name", task.getName());
            map.put("status", task.getStatus());
            map.put("cron", task.getCron());
            
            // 下次执行时间
            try {
                JobKey jobKey = JobKey.jobKey("task_" + task.getId(), "sync_tasks");
                List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
                if (triggers != null && !triggers.isEmpty()) {
                    Date nextFireTime = triggers.get(0).getNextFireTime();
                    map.put("nextFireTime", nextFireTime != null ? nextFireTime.getTime() : null);
                } else {
                    // Try by trigger key if job key doesn't work
                    TriggerKey triggerKey = TriggerKey.triggerKey("trigger_" + task.getId(), "sync_tasks");
                    Trigger trigger = scheduler.getTrigger(triggerKey);
                    if (trigger != null) {
                        Date nextFireTime = trigger.getNextFireTime();
                        map.put("nextFireTime", nextFireTime != null ? nextFireTime.getTime() : null);
                    }
                }
            } catch (SchedulerException e) {
                // Ignore
            }
            
            // 获取最新执行进度
            logRepository.findFirstByTaskIdOrderByStartTimeDesc(task.getId()).ifPresent(latestLog -> {
                map.put("lastResult", latestLog.getResult());
                map.put("lastStartTime", latestLog.getStartTime());
                if ("RUNNING".equals(latestLog.getResult())) {
                    map.put("totalCount", latestLog.getTotalCount());
                    map.put("processedCount", latestLog.getProcessedCount());
                }
            });
            
            return map;
        }).collect(Collectors.toList());
    }
}
