<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Download, Refresh, CircleCheck, CircleClose, Loading } from '@element-plus/icons-vue';
import axios from 'axios';
import { ElMessage } from 'element-plus';

interface ExportRecord {
  id: number;
  dataSourceId: number;
  tableName: string;
  format: string;
  status: string;
  createdAt: string;
  completedAt: string;
  errorMessage: string;
  duration: number;
}

const records = ref<ExportRecord[]>([]);
const loading = ref(false);

const fetchRecords = async () => {
  loading.value = true;
  try {
    const res = await axios.get('/api/database-ops/export/records');
    records.value = res.data;
  } catch (e) {
    ElMessage.error('获取导出记录失败');
  } finally {
    loading.value = false;
  }
};

const formatTime = (timeStr: string) => {
  if (!timeStr) return '-';
  const date = new Date(timeStr);
  return date.toLocaleString();
};

const formatDuration = (ms: number) => {
  if (ms === null || ms === undefined) return '-';
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
};

const handleDownload = (id: number) => {
  const token = localStorage.getItem('token');
  const url = `/api/database-ops/export/download/${id}`;
  
  // Use a temporary link to download
  const link = document.createElement('a');
  link.href = url;
  if (token) {
    // If we need to pass token in header, we'd need to fetch first.
    // But for simple download, we can use a query param or just hope it's in a cookie.
    // Actually, let's use axios to get a blob if needed, but simple link is easier.
    // If the download endpoint is protected, we need axios.
  }
  
  axios({
    url: url,
    method: 'GET',
    responseType: 'blob',
    headers: {
      'Authorization': `Bearer ${token}`
    }
  }).then((response) => {
    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    
    // Get filename from header
    const contentDisposition = response.headers['content-disposition'];
    let fileName = 'export_file';
    if (contentDisposition) {
      const fileNameMatch = contentDisposition.match(/filename="(.+)"/);
      if (fileNameMatch && fileNameMatch.length > 1) {
        fileName = fileNameMatch[1];
      }
    }
    
    link.setAttribute('download', fileName);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }).catch(() => {
    ElMessage.error('下载失败');
  });
};

const getStatusType = (status: string) => {
  switch (status) {
    case 'COMPLETED': return 'success';
    case 'FAILED': return 'danger';
    case 'PROCESSING': return 'warning';
    default: return 'info';
  }
};

const getStatusLabel = (status: string) => {
  switch (status) {
    case 'COMPLETED': return '已完成';
    case 'FAILED': return '失败';
    case 'PROCESSING': return '进行中';
    case 'PENDING': return '等待中';
    default: return status;
  }
};

onMounted(() => {
  fetchRecords();
});
</script>

<template>
  <div class="export-records">
    <div class="header">
      <el-button :icon="Refresh" @click="fetchRecords">刷新</el-button>
    </div>

    <el-table :data="records" v-loading="loading" border stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="tableName" label="表名" min-width="150" />
      <el-table-column prop="format" label="格式" width="100">
        <template #default="{ row }">
          <el-tag>{{ row.format }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" class="status-tag">
            <div class="status-tag">
              <el-icon v-if="row.status === 'PROCESSING'" class="is-loading"><Loading /></el-icon>
              <el-icon v-else-if="row.status === 'COMPLETED'"><CircleCheck /></el-icon>
              <el-icon v-else-if="row.status === 'FAILED'"><CircleClose /></el-icon>
              <span class="status-text">{{ getStatusLabel(row.status) }}</span>
            </div>
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="completedAt" label="完成时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.completedAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="duration" label="耗时" width="100">
        <template #default="{ row }">
          <el-tag type="info" effect="plain">{{ formatDuration(row.duration) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="errorMessage" label="错误信息" show-overflow-tooltip />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button 
            v-if="row.status === 'COMPLETED'" 
            type="primary" 
            :icon="Download" 
            circle 
            @click="handleDownload(row.id)" 
          />
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.export-records {
  padding: 20px;
  background-color: #fff;
  border-radius: 8px;
}

.header {
  margin-bottom: 20px;
  display: flex;
  justify-content: flex-end;
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.status-text {
  margin-left: 2px;
}
</style>
