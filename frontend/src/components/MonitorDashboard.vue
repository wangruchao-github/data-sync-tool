<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue';
import axios from 'axios';
import { ElMessage } from 'element-plus';
import { Monitor, CircleCheck, CircleClose, List, Timer, Calendar, Top } from '@element-plus/icons-vue';

const stats = ref<any>({
  totalTasks: 0,
  todayTotal: 0,
  todaySuccess: 0,
  todayFailure: 0,
  trend: {},
  ranking: []
});

const tasks = ref<any[]>([]);
const loading = ref(false);

const maxTrendCount = computed(() => {
  const values = Object.values(stats.value.trend || {}).map(v => Number(v));
  return Math.max(...values, 5); // 至少保证一个基础高度
});

const getBarHeight = (count: number) => {
  const height = (count / maxTrendCount.value) * 150 + 20;
  return height + 'px';
};

const fetchStats = async () => {
  try {
    const res = await axios.get('/api/monitor/stats');
    stats.value = res.data;
  } catch (e) {
    ElMessage.error('获取统计数据失败');
  }
};

const fetchTasks = async () => {
  try {
    const res = await axios.get('/api/monitor/tasks');
    tasks.value = res.data;
  } catch (e) {
    ElMessage.error('获取任务监控失败');
  } finally {
    loading.value = false;
  }
};

const formatTime = (timeStr: string) => {
  if (!timeStr) return '-';
  return new Date(timeStr).toLocaleString();
};

let timer: any = null;

onMounted(() => {
  fetchStats();
  loading.value = true;
  fetchTasks();
  timer = setInterval(() => {
    fetchTasks();
    fetchStats();
  }, 5000);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <div class="monitor-container">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="20" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" @click="fetchStats" style="cursor: pointer">
          <div class="stat-content">
            <div class="stat-icon total"><el-icon><List /></el-icon></div>
            <div class="stat-info">
              <div class="stat-label">总任务数</div>
              <div class="stat-value">{{ stats.totalTasks }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" @click="fetchStats" style="cursor: pointer">
          <div class="stat-content">
            <div class="stat-icon today"><el-icon><Calendar /></el-icon></div>
            <div class="stat-info">
              <div class="stat-label">今日执行</div>
              <div class="stat-value">
                {{ stats.todayTotal }}
                <el-tag v-if="stats.todayRunning > 0" type="warning" size="small" class="running-tag">
                  {{ stats.todayRunning }} 运行中
                </el-tag>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" @click="fetchStats" style="cursor: pointer">
          <div class="stat-content">
            <div class="stat-icon success"><el-icon><CircleCheck /></el-icon></div>
            <div class="stat-info">
              <div class="stat-label">今日成功</div>
              <div class="stat-value success-text">{{ stats.todaySuccess }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" @click="fetchStats" style="cursor: pointer">
          <div class="stat-content">
            <div class="stat-icon failure"><el-icon><CircleClose /></el-icon></div>
            <div class="stat-info">
              <div class="stat-label">今日失败</div>
              <div class="stat-value failure-text">{{ stats.todayFailure }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势与排行 -->
    <el-row :gutter="20" class="trend-row">
      <el-col :span="16">
        <el-card shadow="never" class="trend-card">
          <template #header>
            <div class="card-header">
              <span>最近 7 天执行趋势</span>
            </div>
          </template>
          <div class="trend-placeholder">
            <div v-for="(count, date) in stats.trend" :key="date" class="trend-bar-item">
              <div class="trend-bar" :style="{ height: getBarHeight(Number(count)) }">
                <span class="bar-value">{{ count }}</span>
              </div>
              <div class="trend-date">{{ String(date).substring(5) }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="ranking-card">
          <template #header>
            <div class="card-header">
              <span>任务执行排行 (Top 10)</span>
            </div>
          </template>
          <div class="ranking-list">
            <div v-for="(item, index) in stats.ranking" :key="item.taskId" class="ranking-item">
               <div class="ranking-index" :class="{ 'top-three': (index as number) < 3 }">{{ (index as number) + 1 }}</div>
               <div class="ranking-name" :title="item.taskName">{{ item.taskName }}</div>
               <div class="ranking-count">{{ item.count }} 次</div>
             </div>
            <el-empty v-if="!stats.ranking?.length" description="暂无数据" :image-size="40" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 任务监控列表 -->
    <el-card shadow="never" class="task-monitor-card">
      <template #header>
        <div class="card-header">
          <span>任务运行监控</span>
          <el-button link :icon="Timer" @click="fetchTasks">刷新</el-button>
        </div>
      </template>
      <el-table :data="tasks" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="任务名称" min-width="150" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'ENABLED' ? 'success' : 'info'">
              {{ scope.row.status === 'ENABLED' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cron" label="Cron 表达式" width="150" />
        <el-table-column label="下次执行时间" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.nextFireTime) }}
          </template>
        </el-table-column>
        <el-table-column label="最近执行" width="180">
          <template #default="scope">
            <div v-if="scope.row.lastStartTime">
              <el-tag :type="scope.row.lastResult === 'SUCCESS' ? 'success' : (scope.row.lastResult === 'RUNNING' ? 'warning' : 'danger')" size="small">
                {{ scope.row.lastResult === 'SUCCESS' ? '成功' : (scope.row.lastResult === 'RUNNING' ? '运行中' : '失败') }}
              </el-tag>
              <div class="last-time">{{ formatTime(scope.row.lastStartTime) }}</div>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="运行进度" min-width="200">
          <template #default="scope">
            <div v-if="scope.row.lastResult === 'RUNNING'">
              <el-progress 
                :percentage="scope.row.totalCount > 0 ? Math.floor((scope.row.processedCount / scope.row.totalCount) * 100) : 0" 
                :indeterminate="scope.row.totalCount <= 0"
                :status="scope.row.totalCount > 0 ? '' : 'warning'"
              />
              <div class="progress-info">
                {{ scope.row.processedCount || 0 }} / {{ scope.row.totalCount > 0 ? scope.row.totalCount : '未知' }}
              </div>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.monitor-container {
  padding: 20px;
}

.stat-cards {
  margin-bottom: 20px;
}

.stat-card {
  height: 100px;
  display: flex;
  align-items: center;
}

.stat-content {
  display: flex;
  align-items: center;
  width: 100%;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-right: 15px;
}

.stat-icon.total { background: #eef5fe; color: #409eff; }
.stat-icon.today { background: #fff7eb; color: #e6a23c; }
.stat-icon.success { background: #f0f9eb; color: #67c23a; }
.stat-icon.failure { background: #fef0f0; color: #f56c6c; }

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.running-tag {
  font-weight: normal;
}

.success-text { color: #67c23a; }
.failure-text { color: #f56c6c; }

.last-time {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.progress-info {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.trend-row {
  margin-bottom: 20px;
}

.trend-card, .ranking-card {
  height: 320px;
}

.trend-placeholder {
  height: 220px;
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  padding: 40px 0 10px 0;
  border-bottom: 1px solid #f0f0f0;
}

.trend-bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
}

.trend-bar {
  width: 30px;
  background: #409eff;
  border-radius: 4px 4px 0 0;
  position: relative;
  transition: height 0.3s;
}

.bar-value {
  position: absolute;
  top: -20px;
  width: 100%;
  text-align: center;
  font-size: 12px;
  color: #606266;
}

.trend-date {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}

.ranking-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ranking-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ranking-index {
  width: 20px;
  height: 20px;
  background: #f0f2f5;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #909399;
}

.ranking-index.top-three {
  background: #409eff;
  color: #fff;
}

.ranking-name {
  flex: 1;
  font-size: 14px;
  color: #606266;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ranking-count {
  font-size: 13px;
  color: #909399;
  font-weight: bold;
}

.last-time {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
