<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Plus, Edit, VideoPlay, CopyDocument, Delete, More, Connection, Promotion, Document } from '@element-plus/icons-vue';
import axios from 'axios';
import { ElMessage, ElMessageBox } from 'element-plus';
import ApiOrchestration from './ApiOrchestration.vue';
import ApiTest from './ApiTest.vue';
import ApiDocs from './ApiDocs.vue';

interface ApiDefinition {
  id?: string;
  name: string;
  path: string;
  method: string;
  description: string;
  apiType: string;
  status: string;
  version: string;
  content: string;
  responseExample?: string;
}

const apiList = ref<ApiDefinition[]>([]);
const loading = ref(false);
const dialogVisible = ref(false);
const orchestrationVisible = ref(false);
const testVisible = ref(false);
const docsVisible = ref(false);
const currentApi = ref<ApiDefinition>({
  name: '',
  path: '',
  method: 'GET',
  description: '',
  apiType: 'PRIVATE',
  status: 'DRAFT',
  version: 'v1',
  content: JSON.stringify({ nodes: [], edges: [] }),
  responseExample: ''
});

const fetchApis = async () => {
  loading.value = true;
  try {
    const res = await axios.get('/api/api-definitions');
    apiList.value = res.data;
  } catch (e) {
    console.error('Failed to fetch APIs:', e);
    ElMessage.error('获取API列表失败');
  } finally {
    loading.value = false;
  }
};

const handleCreate = () => {
  currentApi.value = {
    name: '',
    path: '',
    method: 'GET',
    description: '',
    apiType: 'PRIVATE',
    status: 'DRAFT',
    version: 'v1',
    content: JSON.stringify({ nodes: [], edges: [] }),
    responseExample: ''
  };
  dialogVisible.value = true;
};

const handleEdit = (api: ApiDefinition) => {
  currentApi.value = { ...api };
  dialogVisible.value = true;
};

const handleOrchestrate = (api: ApiDefinition) => {
  currentApi.value = { ...api };
  orchestrationVisible.value = true;
};

const handleTest = (api: ApiDefinition) => {
  currentApi.value = { ...api };
  testVisible.value = true;
};

const handleViewDocs = (api: ApiDefinition) => {
  currentApi.value = { ...api };
  docsVisible.value = true;
};

const handleSetExample = async (example: string) => {
  try {
    currentApi.value.responseExample = example;
    await axios.post('/api/api-definitions', currentApi.value);
    ElMessage.success('响应示例已更新');
    fetchApis();
  } catch (e) {
    console.error('Failed to update response example:', e);
    ElMessage.error('更新响应示例失败');
  }
};

const handleSave = async () => {
  try {
    if (!currentApi.value.name || !currentApi.value.path) {
      ElMessage.warning('请填写必填项');
      return;
    }
    await axios.post('/api/api-definitions', currentApi.value);
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    orchestrationVisible.value = false;
    fetchApis();
  } catch (e) {
    console.error('Failed to save API:', e);
    ElMessage.error('保存失败');
  }
};

const handleDelete = (id: string) => {
  ElMessageBox.confirm('确定要删除这个API定义吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await axios.delete(`/api/api-definitions/${id}`);
      ElMessage.success('删除成功');
      fetchApis();
    } catch (e) {
      console.error('Failed to delete API:', e);
      ElMessage.error('删除失败');
    }
  });
};

const handleToggleStatus = async (api: ApiDefinition) => {
  const newStatus = api.status === 'ONLINE' ? 'OFFLINE' : 'ONLINE';
  try {
    await axios.post('/api/api-definitions', { ...api, status: newStatus });
    ElMessage.success(`${newStatus === 'ONLINE' ? '上线' : '下线'}成功`);
    fetchApis();
  } catch (e) {
    console.error('Failed to update status:', e);
    ElMessage.error('更新状态失败');
  }
};

onMounted(() => {
  fetchApis();
});
</script>

<template>
  <div class="api-management">
    <el-card class="list-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title">API服务管理</span>
            <span class="subtitle">将数据流发布为标准的 HTTP API 接口</span>
          </div>
          <el-button type="primary" :icon="Plus" @click="handleCreate">新建API</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="apiList" style="width: 100%">
        <el-table-column prop="name" label="API名称" min-width="150" />
        <el-table-column prop="path" label="路径" min-width="200">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" style="margin-right: 8px">{{ row.method }}</el-tag>
            <code class="path-code">{{ row.path }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="apiType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.apiType === 'PUBLIC' ? 'success' : 'info'" size="small">
              {{ row.apiType === 'PUBLIC' ? '公开' : '私有' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ONLINE' ? 'success' : 'warning'" size="small">
              {{ row.status === 'ONLINE' ? '已上线' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column label="操作" width="420" fixed="right">
          <template #default="{ row }">
            <el-button-group>
              <el-button size="small" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
              <el-button size="small" :icon="Connection" type="primary" plain @click="handleOrchestrate(row)">编排</el-button>
              <el-button size="small" :icon="Promotion" type="warning" plain @click="handleTest(row)">调试</el-button>
              <el-button size="small" :icon="Document" type="info" plain @click="handleViewDocs(row)">文档</el-button>
              <el-button size="small" :icon="VideoPlay" type="success" plain @click="handleToggleStatus(row)">
                {{ row.status === 'ONLINE' ? '下线' : '上线' }}
              </el-button>
              <el-dropdown trigger="click">
                <el-button size="small" :icon="More" style="margin-left: 8px" />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item :icon="CopyDocument">复制API</el-dropdown-item>
                    <el-dropdown-item :icon="Delete" @click="handleDelete(row.id)" divided style="color: #f56c6c">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </el-button-group>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="currentApi.id ? '编辑API' : '新建API'"
      width="600px"
    >
      <el-form :model="currentApi" label-width="100px">
        <el-form-item label="API名称" required>
          <el-input v-model="currentApi.name" placeholder="请输入API名称" />
        </el-form-item>
        <el-form-item label="请求路径" required>
          <el-input v-model="currentApi.path" placeholder="例如: /api/v1/users">
            <template #prepend>
              <el-select v-model="currentApi.method" style="width: 100px">
                <el-option label="GET" value="GET" />
                <el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" />
                <el-option label="DELETE" value="DELETE" />
              </el-select>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="API类型">
          <el-radio-group v-model="currentApi.apiType">
            <el-radio label="PRIVATE">私有接口 (需鉴权)</el-radio>
            <el-radio label="PUBLIC">公开接口 (免鉴权)</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="currentApi.description" type="textarea" rows="3" />
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="currentApi.version" />
        </el-form-item>
        <el-form-item label="响应示例">
          <el-input 
            v-model="currentApi.responseExample" 
            type="textarea" 
            rows="5" 
            placeholder="调试后可自动填充，或在此手动输入 JSON 响应示例"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="orchestrationVisible"
      :title="`API流程编排 - ${currentApi.name}`"
      size="80%"
      destroy-on-close
    >
      <ApiOrchestration 
        v-if="orchestrationVisible"
        :api-id="currentApi.id || ''" 
        v-model="currentApi.content"
        @save="handleSave"
      />
    </el-drawer>

    <el-drawer
      v-model="testVisible"
      :title="`API 在线调试 - ${currentApi.name}`"
      size="600px"
      destroy-on-close
    >
      <ApiTest
        v-if="testVisible"
        :api-id="currentApi.id || ''"
        :api-name="currentApi.name"
        :api-method="currentApi.method"
        :api-path="currentApi.path"
        @set-example="handleSetExample"
      />
    </el-drawer>

    <el-drawer
      v-model="docsVisible"
      :title="`API 接口文档 - ${currentApi.name}`"
      size="700px"
      destroy-on-close
    >
      <ApiDocs
        v-if="docsVisible"
        :api-id="currentApi.id || ''"
        :api-type="currentApi.apiType"
      />
    </el-drawer>
  </div>
</template>

<style scoped>
.api-management {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  flex-direction: column;
}

.title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.subtitle {
  font-size: 13px;
  color: #999;
  margin-top: 4px;
}

.path-code {
  background-color: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  color: #409eff;
}

.list-card {
  border-radius: 8px;
  border: none;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05);
}
</style>
