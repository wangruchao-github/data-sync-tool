<script setup lang="ts">
import { ref, onMounted } from 'vue';
import axios from 'axios';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Connection, Delete, Edit } from '@element-plus/icons-vue';

interface DataSource {
  id?: number;
  name: string;
  type: string;
  host: string;
  port: number;
  databaseName: string;
  username: string;
  password?: string;
}

const dataSources = ref<DataSource[]>([]);
const showDialog = ref(false);
const loading = ref(false);
const testing = ref(false);
const isEdit = ref(false);

const form = ref<DataSource>({
  name: '',
  type: 'MYSQL',
  host: 'localhost',
  port: 3306,
  databaseName: '',
  username: 'root',
  password: ''
});

const fetchDS = async () => {
  loading.value = true;
  try {
    const res = await axios.get('/api/datasources');
    dataSources.value = res.data;
  } catch (e) {
    ElMessage.error('获取数据源失败');
  } finally {
    loading.value = false;
  }
};

const handleAdd = () => {
  isEdit.value = false;
  form.value = {
    name: '',
    type: 'MYSQL',
    host: 'localhost',
    port: 3306,
    databaseName: '',
    username: 'root',
    password: ''
  };
  showDialog.value = true;
};

const testConn = async () => {
  testing.value = true;
  try {
    const res = await axios.post('/api/datasources/test', form.value);
    if (res.data) {
      ElMessage.success('连接成功');
    } else {
      ElMessage.error('连接失败');
    }
  } catch (e) {
    ElMessage.error('测试连接出错');
  } finally {
    testing.value = false;
  }
};

const saveDS = async () => {
  try {
    await axios.post('/api/datasources', form.value);
    ElMessage.success(isEdit.value ? '修改成功' : '添加成功');
    showDialog.value = false;
    fetchDS();
  } catch (e) {
    ElMessage.error('保存失败');
  }
};

const deleteDS = (row: DataSource) => {
  ElMessageBox.confirm(`确定要删除数据源 "${row.name}" 吗？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await axios.delete(`/api/datasources/${row.id}`);
      ElMessage.success('删除成功');
      fetchDS();
    } catch (e) {
      ElMessage.error('删除失败');
    }
  });
};

onMounted(fetchDS);
</script>

<template>
  <div class="ds-container">
    <div class="action-bar">
      <el-button type="primary" :icon="Plus" @click="handleAdd">新增数据源</el-button>
      <el-button :icon="Connection" @click="fetchDS" :loading="loading">刷新</el-button>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="dataSources" v-loading="loading" style="width: 100%">
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.type === 'MYSQL' ? 'success' : 'info'">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="地址" min-width="200">
          <template #default="{ row }">
            {{ row.host }}:{{ row.port }}
          </template>
        </el-table-column>
        <el-table-column prop="databaseName" label="数据库" min-width="150" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="() => { form = {...row}; isEdit = true; showDialog = true; }">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="deleteDS(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="showDialog"
      :title="isEdit ? '编辑数据源' : '新增数据源'"
      width="500px"
      destroy-on-close
    >
      <el-form :model="form" label-width="100px" label-position="right">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="请输入数据源显示名称" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-radio-group v-model="form.type">
            <el-radio-button label="MYSQL">MySQL</el-radio-button>
            <el-radio-button label="ORACLE">Oracle</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="主机" required>
          <el-input v-model="form.host" placeholder="127.0.0.1" />
        </el-form-item>
        <el-form-item label="端口" required>
          <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>
        <el-form-item label="数据库" required>
          <el-input v-model="form.databaseName" placeholder="请输入数据库名称" />
        </el-form-item>
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" :required="!isEdit">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showDialog = false">取消</el-button>
          <el-button type="success" :loading="testing" @click="testConn">测试连接</el-button>
          <el-button type="primary" @click="saveDS">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ds-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.action-bar {
  display: flex;
  justify-content: flex-start;
  gap: 12px;
}

.table-card {
  border-radius: 8px;
  border: none;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

:deep(.el-button--primary) {
  --el-button-bg-color: var(--wechat-green);
  --el-button-border-color: var(--wechat-green);
  --el-button-hover-bg-color: #06ae56;
  --el-button-hover-border-color: #06ae56;
}

:deep(.el-button--success) {
  --el-button-bg-color: #f0f9eb;
  --el-button-text-color: var(--wechat-green);
  --el-button-border-color: #c2e7b0;
}
</style>
