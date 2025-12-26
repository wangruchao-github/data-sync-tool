<script setup lang="ts">
import { ref, watch, onMounted } from 'vue';
import axios from 'axios';
import { ElMessage } from 'element-plus';
import { Timer, Calendar } from '@element-plus/icons-vue';

const props = defineProps<{
  modelValue: string
}>();

const emit = defineEmits(['update:modelValue']);

const cronStr = ref(props.modelValue || '0 0 * * * ?');
const nextExecutions = ref<string[]>([]);

const presets = [
  { label: '每分钟', value: '0 * * * * ?' },
  { label: '每小时', value: '0 0 * * * ?' },
  { label: '每天 (凌晨0点)', value: '0 0 0 * * ?' },
  { label: '每周 (周一凌晨0点)', value: '0 0 0 ? * MON' },
  { label: '每月 (1号凌晨0点)', value: '0 0 0 1 * ?' }
];

const fetchNextExecutions = async (cron: string) => {
  if (!cron) return;
  try {
    const res = await axios.get('/api/tasks/cron/next-executions', {
      params: { cron, count: 5 }
    });
    nextExecutions.value = res.data.map((d: string) => new Date(d).toLocaleString());
  } catch (e) {
    nextExecutions.value = [];
  }
};

watch(cronStr, (newVal) => {
  emit('update:modelValue', newVal);
  fetchNextExecutions(newVal);
});

watch(() => props.modelValue, (newVal) => {
  if (newVal !== cronStr.value) {
    cronStr.value = newVal;
  }
});

const selectPreset = (val: string) => {
  cronStr.value = val;
};

onMounted(() => {
  fetchNextExecutions(cronStr.value);
});
</script>

<template>
  <div class="cron-config">
    <div class="preset-buttons">
      <el-button 
        v-for="p in presets" 
        :key="p.value" 
        size="small" 
        :type="cronStr === p.value ? 'primary' : 'default'"
        @click="selectPreset(p.value)"
      >
        {{ p.label }}
      </el-button>
    </div>
    
    <div class="cron-input-wrapper">
      <el-input v-model="cronStr" placeholder="Cron 表达式 (如: 0 0 * * * ?)">
        <template #prepend>Cron</template>
      </el-input>
      <div class="help-text">格式: 秒 分 时 日 月 周 [年] (Quartz 标准)</div>
    </div>

    <div class="next-executions">
      <div class="section-title">
        <el-icon><Timer /></el-icon>
        <span>后续 5 次执行时间预估:</span>
      </div>
      <div v-if="nextExecutions.length > 0" class="time-list">
        <div v-for="(time, index) in nextExecutions" :key="index" class="time-item">
          <el-icon><Calendar /></el-icon>
          <span>{{ time }}</span>
        </div>
      </div>
      <el-alert v-else title="无效的 Cron 表达式" type="error" :closable="false" />
    </div>
  </div>
</template>

<style scoped>
.cron-config {
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: #f9fafc;
  padding: 16px;
  border-radius: 4px;
  border: 1px solid #ebeef5;
}

.preset-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cron-input-wrapper {
  margin-top: 8px;
}

.help-text {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.next-executions {
  border-top: 1px dashed #dcdfe6;
  padding-top: 12px;
}

.section-title {
  font-size: 13px;
  font-weight: bold;
  color: #606266;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.time-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.time-item {
  font-size: 13px;
  color: #67c23a;
  display: flex;
  align-items: center;
  gap: 8px;
}

.time-item span {
  font-family: monospace;
}
</style>
