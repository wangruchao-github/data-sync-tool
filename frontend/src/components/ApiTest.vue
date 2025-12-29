<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import axios from 'axios';
import { ElMessage } from 'element-plus';
import { Delete, Plus } from '@element-plus/icons-vue';

const props = defineProps<{
  apiId: string;
  apiName: string;
  apiMethod: string;
  apiPath: string;
}>();

const emit = defineEmits<{
  (e: 'set-example', example: string): void;
}>();

const loading = ref(false);
const activeTab = ref('params');

const testForm = reactive({
  params: [] as { key: string; value: string }[],
  headers: [] as { key: string; value: string }[],
  body: '{}'
});

const response = ref<any>(null);
const responseTime = ref(0);

const addParam = () => testForm.params.push({ key: '', value: '' });
const removeParam = (index: number) => testForm.params.splice(index, 1);
const addHeader = () => testForm.headers.push({ key: '', value: '' });
const removeHeader = (index: number) => testForm.headers.splice(index, 1);

const handleTest = async () => {
  loading.value = true;
  response.value = null;
  const startTime = Date.now();
  
  try {
    const params: Record<string, any> = {};
    testForm.params.forEach(p => {
      if (p.key) params[p.key] = p.value;
    });

    // Merge body params if it's JSON
    if (testForm.body && testForm.body !== '{}') {
      try {
        const bodyObj = JSON.parse(testForm.body);
        Object.assign(params, bodyObj);
      } catch (e) {
        ElMessage.error('请求体 JSON 格式错误');
        loading.value = false;
        return;
      }
    }

    const res = await axios.post(`/api/api-definitions/${props.apiId}/debug`, params);
    response.value = res.data;
    responseTime.value = Date.now() - startTime;
    ElMessage.success('测试完成');
  } catch (e: any) {
    console.error('Test failed:', e);
    response.value = e.response?.data || { message: e.message };
    responseTime.value = Date.now() - startTime;
    ElMessage.error('测试执行失败');
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  if (testForm.params.length === 0) addParam();
  if (testForm.headers.length === 0) addHeader();
});
</script>

<template>
  <div class="api-test">
    <div class="test-header">
      <el-tag :type="apiMethod === 'GET' ? 'success' : 'warning'" effect="dark">{{ apiMethod }}</el-tag>
      <span class="path">{{ apiPath }}</span>
      <div class="flex-spacer"></div>
      <el-button type="primary" :loading="loading" @click="handleTest">发送请求</el-button>
    </div>

    <el-tabs v-model="activeTab" class="test-tabs">
      <el-tab-pane label="查询参数" name="params">
        <div v-for="(item, index) in testForm.params" :key="index" class="param-row">
          <el-input v-model="item.key" placeholder="参数名" size="small" />
          <el-input v-model="item.value" placeholder="参数值" size="small" />
          <el-button :icon="Delete" type="danger" link @click="removeParam(index)" />
        </div>
        <el-button :icon="Plus" size="small" link @click="addParam">添加参数</el-button>
      </el-tab-pane>

      <el-tab-pane label="请求体 (JSON)" name="body">
        <el-input
          v-model="testForm.body"
          type="textarea"
          :rows="10"
          placeholder='{"key": "value"}'
        />
      </el-tab-pane>

      <el-tab-pane label="请求头" name="headers">
        <div v-for="(item, index) in testForm.headers" :key="index" class="param-row">
          <el-input v-model="item.key" placeholder="Header名" size="small" />
          <el-input v-model="item.value" placeholder="Header值" size="small" />
          <el-button :icon="Delete" type="danger" link @click="removeHeader(index)" />
        </div>
        <el-button :icon="Plus" size="small" link @click="addHeader">添加Header</el-button>
      </el-tab-pane>
    </el-tabs>

    <div class="response-section" v-if="response">
      <div class="response-header">
        <div class="left">
          <span>响应结果</span>
          <el-tag size="small" type="info" style="margin-left: 10px">耗时: {{ responseTime }}ms</el-tag>
        </div>
        <el-button type="primary" link @click="emit('set-example', JSON.stringify(response, null, 2))">设置为响应示例</el-button>
      </div>
      <pre class="response-body">{{ JSON.stringify(response, null, 2) }}</pre>
    </div>
  </div>
</template>

<style scoped>
.api-test {
  padding: 10px;
}
.test-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 1px solid #eee;
}
.path {
  font-family: monospace;
  font-weight: bold;
}
.flex-spacer {
  flex: 1;
}
.param-row {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
}
.test-tabs {
  margin-bottom: 20px;
}
.response-section {
  border-top: 1px solid #eee;
  padding-top: 15px;
}
.response-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-weight: bold;
}
.response-body {
  background: #2d2d2d;
  color: #ccc;
  padding: 15px;
  border-radius: 4px;
  max-height: 400px;
  overflow-y: auto;
  font-size: 13px;
  line-height: 1.5;
}
</style>
