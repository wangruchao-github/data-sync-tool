<template>
  <div class="api-docs">
    <div class="doc-header">
      <h2>{{ doc.info?.title }} <small>v{{ doc.info?.version }}</small></h2>
      <el-tag :type="apiType === 'PUBLIC' ? 'success' : 'warning'">{{ apiType }}</el-tag>
      <p class="description">{{ doc.info?.description }}</p>
    </div>

    <el-card class="endpoint-card" v-for="(pathItem, path) in doc.paths" :key="path">
      <div class="doc-section" v-for="(operation, method) in pathItem" :key="method">
        <div class="operation-header">
          <el-tag :type="getMethodTagType(method as string)" effect="dark">{{ (method as string).toUpperCase() }}</el-tag>
          <span class="path">{{ path }}</span>
        </div>
        
        <h4>接口描述</h4>
        <p>{{ operation.description }}</p>

        <h4>响应示例 (200 OK)</h4>
        <div class="response-content">
          <pre class="json-code">{{ 
            JSON.stringify(
              operation.responses['200']?.content?.['application/json']?.schema?.example || 
              operation.responses['200']?.content?.['application/json']?.schema, 
              null, 2
            ) 
          }}</pre>
        </div>
        
        <div class="doc-footer">
          <el-button type="primary" link @click="exportJson">导出 OpenAPI JSON</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import axios from 'axios';
import { ElMessage } from 'element-plus';

const props = defineProps<{
  apiId: string;
  apiType: string;
}>();

const doc = ref<any>({});

const getMethodTagType = (method: string) => {
  const types: Record<string, string> = {
    get: 'success',
    post: 'warning',
    put: 'info',
    delete: 'danger'
  };
  return types[method.toLowerCase()] || 'info';
};

const fetchDoc = async () => {
  try {
    const response = await axios.get(`/api/api-definitions/${props.apiId}/docs`);
    doc.value = response.data;
  } catch (error) {
    ElMessage.error('获取文档失败');
  }
};

const exportJson = () => {
  const data = JSON.stringify(doc.value, null, 2);
  const blob = new Blob([data], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `${doc.value.info?.title || 'api'}-docs.json`;
  link.click();
  URL.revokeObjectURL(url);
};

onMounted(fetchDoc);
</script>

<style scoped>
.api-docs {
  padding: 10px;
}
.doc-header {
  margin-bottom: 20px;
  border-bottom: 1px solid #eee;
  padding-bottom: 15px;
}
.doc-header h2 {
  margin: 0 0 10px 0;
}
.doc-header h2 small {
  font-weight: normal;
  color: #999;
  font-size: 14px;
}
.description {
  color: #666;
  margin-top: 10px;
}
.endpoint-card {
  margin-bottom: 20px;
}
.endpoint-header {
  display: flex;
  align-items: center;
  gap: 10px;
}
.path {
  font-family: monospace;
  font-weight: bold;
}
.doc-section h4 {
  margin: 15px 0 10px 0;
  border-left: 4px solid #409eff;
  padding-left: 10px;
}
.response-content {
  background: #f8f8f8;
  padding: 15px;
  border-radius: 4px;
}
.json-code {
  margin: 0;
  font-family: monospace;
}
.doc-footer {
  margin-top: 20px;
  text-align: right;
}
</style>
