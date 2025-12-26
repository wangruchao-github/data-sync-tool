<script setup lang="ts">
import { ref, onMounted } from 'vue';
import axios from 'axios';
import { ElMessage } from 'element-plus';
import { Setting, Document, Picture } from '@element-plus/icons-vue';

interface SystemConfig {
  configKey: string;
  configValue: string;
  configName: string;
  description: string;
}

const configs = ref<SystemConfig[]>([]);
const loading = ref(false);
const saving = ref(false);

const fetchConfigs = async () => {
  loading.value = true;
  try {
    const res = await axios.get('/api/config/all');
    configs.value = res.data;
  } catch (e) {
    ElMessage.error('获取系统配置失败');
  } finally {
    loading.value = false;
  }
};

const handleSave = async () => {
  saving.value = true;
  try {
    await axios.post('/api/config/batch', configs.value);
    ElMessage.success('保存成功');
    // 通知父组件刷新配置
    window.location.reload();
  } catch (e) {
    ElMessage.error('保存失败');
  } finally {
    saving.value = false;
  }
};

onMounted(fetchConfigs);
</script>

<template>
  <div class="settings-container">
    <el-card class="settings-card">
      <template #header>
        <div class="card-header">
          <el-icon><Setting /></el-icon>
          <span class="header-title">系统配置</span>
        </div>
      </template>
      
      <el-form label-width="120px" v-loading="loading">
        <div v-for="config in configs" :key="config.configKey" class="config-item">
          <el-form-item :label="config.configName">
            <template v-if="config.configKey === 'sys.logo'">
              <el-input v-model="config.configValue" placeholder="图片URL或Base64">
                <template #prefix>
                  <el-icon><Picture /></el-icon>
                </template>
              </el-input>
              <div v-if="config.configValue" class="logo-preview">
                <img :src="config.configValue" alt="logo preview" />
              </div>
            </template>
            <template v-else>
              <el-input v-model="config.configValue" :placeholder="config.description" />
            </template>
            <div class="config-desc">{{ config.description }}</div>
          </el-form-item>
        </div>
        
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
          <el-button @click="fetchConfigs">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.settings-container {
  max-width: 800px;
  margin: 0 auto;
}

.settings-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-title {
  font-weight: bold;
}

.config-item {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f2f5;
}

.config-item:last-child {
  border-bottom: none;
}

.config-desc {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
  line-height: 1.4;
}

.logo-preview {
  margin-top: 10px;
  padding: 8px;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  display: inline-block;
}

.logo-preview img {
  max-height: 50px;
  display: block;
}
</style>
