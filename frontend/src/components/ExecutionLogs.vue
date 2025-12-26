<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue';
import axios from 'axios';
import { ElMessage } from 'element-plus';
import { Refresh, Document, View, Position, Search, Delete } from '@element-plus/icons-vue';

interface SyncTask {
  id: number;
  name: string;
}

interface NodeDetail {
  nodeId: string;
  nodeType: string;
  nodeName: string;
  startTime?: string;
  endTime?: string;
  rowCount?: number;
  durationMs?: number;
  sql?: string;
  tableName?: string;
  mappingCount?: number;
}

interface SyncLog {
  id: number;
  taskId: number;
  taskName: string;
  startTime: string;
  endTime: string;
  result: string;
  message: string;
  syncCount: number;
  totalCount: number;
  processedCount: number;
  durationMs: number;
  nodeDetails?: string;
}

const emit = defineEmits(['jump-to-task']);

const logs = ref<SyncLog[]>([]);
const loading = ref(false);
const total = ref(0);
const page = ref(1);
const pageSize = ref(10);
const tasks = ref<SyncTask[]>([]);
const pollingTimer = ref<any>(null);

const filter = reactive({
  taskId: null as number | null,
  timeRange: [] as any[]
});

const detailDialogVisible = ref(false);
const selectedLog = ref<SyncLog | null>(null);
const parsedNodeDetails = ref<NodeDetail[]>([]);

const fetchTasks = async () => {
  try {
    const res = await axios.get('/api/tasks');
    tasks.value = res.data || [];
  } catch (e) {
    console.error('Fetch tasks failed:', e);
  }
};

const fetchLogs = async (isPolling = false) => {
  if (!isPolling) loading.value = true;
  try {
    const params: any = {
      page: page.value - 1,
      size: pageSize.value
    };

    if (filter.taskId) {
      params.taskId = filter.taskId;
    }

    if (filter.timeRange && filter.timeRange.length === 2) {
      params.startTime = filter.timeRange[0].toISOString();
      params.endTime = filter.timeRange[1].toISOString();
    }

    const res = await axios.get('/api/logs/search', { params });
    logs.value = res.data.content;
    total.value = res.data.totalElements;

    // Check if any log is in RUNNING state to continue polling
    const hasRunning = logs.value.some(log => log.result === 'RUNNING');
    if (hasRunning && !pollingTimer.value) {
      startPolling();
    } else if (!hasRunning && pollingTimer.value) {
      stopPolling();
    }
  } catch (error) {
    console.error('Fetch logs failed:', error);
    if (!isPolling) ElMessage.error('获取日志失败');
  } finally {
    if (!isPolling) loading.value = false;
  }
};

const startPolling = () => {
  if (pollingTimer.value) return;
  pollingTimer.value = setInterval(() => {
    fetchLogs(true);
  }, 3000);
};

const stopPolling = () => {
  if (pollingTimer.value) {
    clearInterval(pollingTimer.value);
    pollingTimer.value = null;
  }
};

const handleSearch = () => {
  page.value = 1;
  fetchLogs();
};

const resetFilter = () => {
  filter.taskId = null;
  filter.timeRange = [];
  handleSearch();
};

const handlePageChange = (val: number) => {
  page.value = val;
  fetchLogs();
};

const showDetail = (row: SyncLog) => {
  selectedLog.value = row;
  try {
    parsedNodeDetails.value = row.nodeDetails ? JSON.parse(row.nodeDetails) : [];
  } catch (e) {
    parsedNodeDetails.value = [];
  }
  detailDialogVisible.value = true;
};

const jumpToTask = (taskId: number) => {
  emit('jump-to-task', taskId);
};

const formatDuration = (ms: number) => {
  if (ms == null) return '-';
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
};

const formatTime = (timeStr: string) => {
  if (!timeStr) return '-';
  return new Date(timeStr).toLocaleString();
};

import { onUnmounted } from 'vue';

onMounted(() => {
  fetchTasks();
  fetchLogs();
});

onUnmounted(() => {
  stopPolling();
});
</script>

<template>
  <div class="logs-container">
    <div class="toolbar">
      <div class="filter-group">
        <el-select v-model="filter.taskId" placeholder="选择任务" clearable style="width: 200px">
          <el-option v-for="task in tasks" :key="task.id" :label="task.name" :value="task.id" />
        </el-select>
        <el-date-picker
          v-model="filter.timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          style="margin-left: 10px;"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch" style="margin-left: 10px;">查询</el-button>
        <el-button :icon="Delete" @click="resetFilter">重置</el-button>
      </div>
      <el-button :icon="Refresh" @click="fetchLogs" :loading="loading">刷新日志</el-button>
    </div>

    <el-table :data="logs" v-loading="loading" style="width: 100%" border stripe>
      <el-table-column prop="taskName" label="任务名称" min-width="150" show-overflow-tooltip />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.result === 'SUCCESS' ? 'success' : (row.result === 'RUNNING' ? 'primary' : 'danger')">
            {{ row.result === 'SUCCESS' ? '成功' : (row.result === 'RUNNING' ? '同步中' : '失败') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="同步进度" min-width="200">
        <template #default="{ row }">
          <div v-if="row.result === 'RUNNING' || (row.totalCount > 0 && row.result === 'FAILURE')" class="progress-wrapper">
            <el-progress 
              :percentage="row.totalCount > 0 ? Math.floor((row.processedCount / row.totalCount) * 100) : 0" 
              :status="row.result === 'FAILURE' ? 'exception' : ''"
              :stroke-width="10"
            />
            <div class="progress-text">{{ row.processedCount || 0 }} / {{ row.totalCount || '?' }}</div>
          </div>
          <span v-else-if="row.result === 'SUCCESS'" style="font-weight: bold; color: #67c23a;">
            已同步 {{ row.syncCount }} 条
          </span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="开始时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.startTime) }}
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="100" align="right">
        <template #default="{ row }">
          {{ formatDuration(row.durationMs) }}
        </template>
      </el-table-column>
      <el-table-column prop="message" label="详情信息" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="View" @click="showDetail(row)">详情</el-button>
          <el-button link type="success" :icon="Position" @click="jumpToTask(row.taskId)">跳转</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-container">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 详情弹窗 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="任务执行详情"
      width="800px"
      destroy-on-close
    >
      <div v-if="selectedLog" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务名称">{{ selectedLog.taskName }}</el-descriptions-item>
          <el-descriptions-item label="执行结果">
            <el-tag :type="selectedLog.result === 'SUCCESS' ? 'success' : 'danger'">
              {{ selectedLog.result === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatTime(selectedLog.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ formatTime(selectedLog.endTime) }}</el-descriptions-item>
          <el-descriptions-item label="同步条数">{{ selectedLog.syncCount }}</el-descriptions-item>
          <el-descriptions-item label="总耗时">{{ formatDuration(selectedLog.durationMs) }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="node-title">节点执行详情</h4>
        <el-timeline>
          <el-timeline-item
            v-for="(node, index) in parsedNodeDetails"
            :key="index"
            :type="selectedLog.result === 'SUCCESS' ? 'primary' : 'danger'"
            :timestamp="node.startTime ? formatTime(node.startTime) : ''"
          >
            <el-card class="node-card">
              <template #header>
                <div class="node-header">
                  <span class="node-name">{{ node.nodeName }}</span>
                  <el-tag size="small">{{ node.nodeType }}</el-tag>
                </div>
              </template>
              <div class="node-info">
                <div v-if="node.sql" class="info-item">
                  <strong>执行SQL:</strong>
                  <pre class="sql-code"><code>{{ node.sql }}</code></pre>
                </div>
                <div v-if="node.tableName" class="info-item">
                  <strong>目标表:</strong> {{ node.tableName }}
                </div>
                <div v-if="node.rowCount !== undefined" class="info-item">
                  <strong>处理条数:</strong> <span class="count">{{ node.rowCount }}</span>
                </div>
                <div v-if="node.durationMs !== undefined" class="info-item">
                  <strong>节点耗时:</strong> {{ formatDuration(node.durationMs) }}
                </div>
                <div v-if="node.mappingCount !== undefined" class="info-item">
                  <strong>映射字段数:</strong> {{ node.mappingCount }}
                </div>
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>

        <div v-if="selectedLog.result === 'FAILURE'" class="error-section">
          <h4>错误信息</h4>
          <el-alert type="danger" :closable="false">{{ selectedLog.message }}</el-alert>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.logs-container {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05);
}

.toolbar {
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-group {
  display: flex;
  align-items: center;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.progress-wrapper {
  padding: 5px 0;
}

.progress-text {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  text-align: center;
}

.node-title {
  margin: 20px 0 15px;
  padding-left: 10px;
  border-left: 4px solid #409eff;
}

.node-card {
  margin-bottom: 10px;
}

.node-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.node-name {
  font-weight: bold;
}

.info-item {
  margin-bottom: 8px;
  font-size: 14px;
}

.sql-code {
  background: #f8f9fa;
  padding: 10px;
  border-radius: 4px;
  overflow-x: auto;
  margin: 5px 0;
}

.sql-code code {
  font-family: 'Courier New', Courier, monospace;
  color: #d63384;
}

.count {
  color: #409eff;
  font-weight: bold;
}

.error-section {
  margin-top: 20px;
}

.error-section h4 {
  color: #f56c6c;
  margin-bottom: 10px;
}
</style>
