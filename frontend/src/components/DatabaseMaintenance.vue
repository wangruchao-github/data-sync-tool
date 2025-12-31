<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { Search, Download, Refresh } from '@element-plus/icons-vue';
import axios from 'axios';
import { ElMessage } from 'element-plus';

interface DataSource {
  id: number;
  name: string;
  type: string;
}

const dataSources = ref<DataSource[]>([]);
const selectedDataSourceId = ref<number | null>(null);
const tables = ref<string[]>([]);
const filterTableText = ref('');
const selectedTable = ref<string | null>(null);
const activeTab = ref('data');
const tableStructure = ref<any[]>([]);
const tableData = ref<any[]>([]);
const tableColumns = ref<string[]>([]);
const totalData = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);
const loading = ref(false);
const sortField = ref('');
const sortOrder = ref('');
const filterMode = ref<'field' | 'sql'>('field');
const filterField = ref('');
const filterValue = ref('');
const customWhere = ref('');

const fetchDataSources = async () => {
  try {
    const res = await axios.get('/api/datasources');
    dataSources.value = res.data;
    if (dataSources.value && dataSources.value.length > 0) {
      const firstDs = dataSources.value[0];
      if (firstDs) {
        selectedDataSourceId.value = firstDs.id;
        fetchTables();
      }
    }
  } catch (e) {
    ElMessage.error('获取数据源失败');
  }
};

const fetchTables = async () => {
  if (!selectedDataSourceId.value) return;
  try {
    const res = await axios.get(`/api/database-ops/tables?dataSourceId=${selectedDataSourceId.value}`);
    tables.value = res.data;
  } catch (e) {
    ElMessage.error('获取表列表失败');
  }
};

const fetchTableStructure = async () => {
  if (!selectedDataSourceId.value || !selectedTable.value) return;
  try {
    const res = await axios.get(`/api/database-ops/structure?dataSourceId=${selectedDataSourceId.value}&tableName=${selectedTable.value}`);
    tableStructure.value = res.data;
  } catch (e) {
    ElMessage.error('获取表结构失败');
  }
};

const fetchTableData = async () => {
  if (!selectedDataSourceId.value || !selectedTable.value) return;
  loading.value = true;
  try {
    const params: any = {
      dataSourceId: selectedDataSourceId.value,
      tableName: selectedTable.value,
      page: currentPage.value,
      size: pageSize.value,
      sortField: sortField.value,
      sortOrder: sortOrder.value,
    };

    if (filterMode.value === 'field') {
      params.filterField = filterField.value;
      params.filterValue = filterValue.value;
    } else {
      params.customWhere = customWhere.value;
    }

    const res = await axios.get(`/api/database-ops/data`, { params });
    tableData.value = res.data.data;
    totalData.value = res.data.total;
    if (tableData.value.length > 0) {
      tableColumns.value = Object.keys(tableData.value[0]);
    } else {
      tableColumns.value = [];
    }
  } catch (e) {
    ElMessage.error('获取表数据失败');
  } finally {
    loading.value = false;
  }
};

const handleDataSourceChange = () => {
  selectedTable.value = null;
  tables.value = [];
  fetchTables();
};

const handleTableSelect = (tableName: string) => {
  selectedTable.value = tableName;
  currentPage.value = 1;
  sortField.value = '';
  sortOrder.value = '';
  filterField.value = '';
  filterValue.value = '';
  customWhere.value = '';
  fetchTableStructure();
  fetchTableData();
};

const handleSortChange = ({ prop, order }: { prop: string, order: string }) => {
  sortField.value = prop;
  sortOrder.value = order;
  currentPage.value = 1;
  fetchTableData();
};

const handleSearch = () => {
  currentPage.value = 1;
  fetchTableData();
};

const handleExport = async (format: string) => {
  if (!selectedDataSourceId.value || !selectedTable.value) return;
  try {
    await axios.post(`/api/database-ops/export`, null, {
      params: {
        dataSourceId: selectedDataSourceId.value,
        tableName: selectedTable.value,
        format: format
      }
    });
    ElMessage.success('导出任务已提交，请在导出记录中查看');
  } catch (e) {
    ElMessage.error('导出失败');
  }
};

const filteredTables = () => {
  if (!filterTableText.value) return tables.value;
  return tables.value.filter(t => t.toLowerCase().includes(filterTableText.value.toLowerCase()));
};

onMounted(() => {
  fetchDataSources();
});

watch([currentPage, pageSize], () => {
  fetchTableData();
});
</script>

<template>
  <div class="db-maintenance">
    <el-container class="inner-container">
      <el-aside width="250px" class="table-aside">
        <div class="aside-header">
          <el-select v-model="selectedDataSourceId" placeholder="选择数据源" @change="handleDataSourceChange" style="width: 100%">
            <el-option v-for="ds in dataSources" :key="ds.id" :label="ds.name" :value="ds.id">
              <span style="float: left">{{ ds.name }}</span>
              <span style="float: right; color: #8492a6; font-size: 13px">{{ ds.type }}</span>
            </el-option>
          </el-select>
          <el-input
            v-model="filterTableText"
            placeholder="搜索表名"
            :prefix-icon="Search"
            style="margin-top: 10px"
          />
        </div>
        <el-scrollbar>
          <ul class="table-list">
            <li 
              v-for="table in filteredTables()" 
              :key="table" 
              :class="{ active: selectedTable === table }"
              @click="handleTableSelect(table)"
            >
              {{ table }}
            </li>
          </ul>
        </el-scrollbar>
      </el-aside>

      <el-main class="table-main">
        <div v-if="selectedTable" class="table-content">
          <div class="content-header">
            <div class="table-info">
              <h3>{{ selectedTable }}</h3>
              <el-tag size="small">{{ selectedDataSourceId ? dataSources.find(d => d.id === selectedDataSourceId)?.name : '' }}</el-tag>
            </div>
            <div class="actions">
              <div class="filter-bar">
                <el-radio-group v-model="filterMode" size="small" @change="handleSearch">
                  <el-radio-button label="field">字段过滤</el-radio-button>
                  <el-radio-button label="sql">SQL过滤</el-radio-button>
                </el-radio-group>

                <template v-if="filterMode === 'field'">
                  <el-select v-model="filterField" placeholder="选择字段" clearable size="small" style="width: 120px; margin-left: 10px">
                    <el-option v-for="col in tableColumns" :key="col" :label="col" :value="col" />
                  </el-select>
                  <el-input
                    v-model="filterValue"
                    placeholder="过滤值..."
                    size="small"
                    clearable
                    @clear="handleSearch"
                    @keyup.enter="handleSearch"
                    style="width: 150px; margin-left: 5px"
                  />
                </template>
                <template v-else>
                  <el-input
                    v-model="customWhere"
                    placeholder="WHERE 条件 (如: id > 10 AND name = 'test')"
                    size="small"
                    clearable
                    @clear="handleSearch"
                    @keyup.enter="handleSearch"
                    style="width: 300px; margin-left: 10px"
                  />
                </template>
                <el-button :icon="Search" size="small" type="primary" @click="handleSearch" style="margin-left: 5px" />
              </div>

              <el-dropdown @command="handleExport">
                <el-button type="primary" :icon="Download">
                  导出数据<el-icon class="el-icon--right"><arrow-down /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="SQL">导出为 SQL</el-dropdown-item>
                    <el-dropdown-item command="JSON">导出为 JSON</el-dropdown-item>
                    <el-dropdown-item command="EXCEL">导出为 EXCEL</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button :icon="Refresh" @click="fetchTableData">刷新</el-button>
            </div>
          </div>

          <el-tabs v-model="activeTab" class="table-tabs">
            <el-tab-pane label="表数据" name="data">
              <el-table 
                :data="tableData" 
                v-loading="loading" 
                border 
                stripe 
                style="width: 100%" 
                height="calc(100vh - 320px)"
                @sort-change="handleSortChange"
              >
                <el-table-column 
                  v-for="col in tableColumns" 
                  :key="col" 
                  :prop="col" 
                  :label="col" 
                  min-width="150" 
                  show-overflow-tooltip 
                  sortable="custom"
                />
                <template #empty>
                  <el-empty description="暂无数据" />
                </template>
              </el-table>
              <div class="pagination-container">
                <el-pagination
                  v-model:current-page="currentPage"
                  v-model:page-size="pageSize"
                  :page-sizes="[10, 20, 50, 100]"
                  layout="total, sizes, prev, pager, next, jumper"
                  :total="totalData"
                />
              </div>
            </el-tab-pane>
            <el-tab-pane label="表结构" name="structure">
              <el-table :data="tableStructure" border stripe style="width: 100%">
                <el-table-column prop="name" label="列名" />
                <el-table-column prop="type" label="类型" />
                <el-table-column prop="size" label="长度" />
                <el-table-column prop="nullable" label="允许空" />
                <el-table-column prop="defaultValue" label="默认值" />
                <el-table-column prop="comment" label="注释" />
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </div>
        <div v-else class="empty-state">
          <el-empty description="请从左侧选择一个表" />
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<style scoped>
.db-maintenance {
  height: 100%;
  background-color: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.inner-container {
  height: 100%;
}

.table-aside {
  border-right: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px); /* Constrain height to enable scrolling */
}

.aside-header {
  padding: 15px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.table-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.table-list li {
  padding: 12px 20px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.3s;
  color: #606266;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.table-list li:hover {
  background-color: #f5f7fa;
  color: var(--el-color-primary);
}

.table-list li.active {
  background-color: #ecf5ff;
  color: var(--el-color-primary);
  border-right: 2px solid var(--el-color-primary);
}

.table-main {
  padding: 0;
}

.table-content {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.content-header {
  padding: 15px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #f0f0f0;
}

.table-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.table-info h3 {
  margin: 0;
  font-size: 18px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 15px;
}

.filter-bar {
  display: flex;
  align-items: center;
  background-color: #f5f7fa;
  padding: 4px 8px;
  border-radius: 4px;
}

.table-tabs {
  padding: 0 20px;
  flex: 1;
}

.pagination-container {
  padding: 15px 0;
  display: flex;
  justify-content: flex-end;
}

.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
