<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { VueFlow, useVueFlow } from '@vue-flow/core';
import { Background } from '@vue-flow/background';
import { Controls } from '@vue-flow/controls';
import { ElMessage } from 'element-plus';
import { Plus, Delete, VideoPlay, Collection, Setting } from '@element-plus/icons-vue';
import axios from 'axios';

const props = defineProps<{
  apiId: string;
  modelValue: string;
}>();

const emit = defineEmits(['update:modelValue', 'save']);

const { 
  onConnect, 
  addEdges, 
  toObject, 
  addNodes, 
  setNodes,
  setEdges,
  onNodeClick,
  nodes,
  edges
} = useVueFlow();

const selectedNode = ref<any>(null);
const dataSources = ref<any[]>([]);

const fetchDataSources = async () => {
  try {
    const res = await axios.get('/api/datasources');
    dataSources.value = res.data;
  } catch (e) {
    console.error('Failed to fetch data sources');
  }
};

onConnect((params) => {
  addEdges(params);
});

onNodeClick(({ node }) => {
  selectedNode.value = node;
});

const addNode = (type: string) => {
  const id = `${type.toLowerCase()}_${Date.now()}`;
  const newNode = {
    id,
    type: 'default',
    label: type,
    position: { x: Math.random() * 400, y: Math.random() * 400 },
    data: { 
      type: type,
      config: type === 'QUERY' ? { dataSourceId: '', sql: '' } : 
              type === 'GROOVY' ? { script: '// Use db.query(dsId, sql, params) to query database\nreturn db.query(1, "SELECT * FROM users", []);' } :
              type === 'OUTPUT' ? { type: 'JSON', content: '' } : {}
    }
  };
  addNodes([newNode]);
};

const handleSave = () => {
  const flowData = toObject();
  emit('update:modelValue', JSON.stringify(flowData));
  emit('save');
};

watch(() => props.modelValue, (val) => {
  if (val) {
    try {
      const flowData = JSON.parse(val);
      setNodes(flowData.nodes || []);
      setEdges(flowData.edges || []);
    } catch (e) {
      console.error('Failed to parse flow data');
    }
  }
}, { immediate: true });

onMounted(() => {
  fetchDataSources();
});
</script>

<template>
  <div class="api-orchestration">
    <div class="toolbar">
      <el-button-group>
        <el-button size="small" :icon="Plus" @click="addNode('ENTRY')">入口节点</el-button>
        <el-button size="small" :icon="Collection" @click="addNode('QUERY')">查询节点</el-button>
        <el-button size="small" :icon="Setting" @click="addNode('GROOVY')">脚本节点</el-button>
        <el-button size="small" :icon="Setting" @click="addNode('OUTPUT')">输出节点</el-button>
      </el-button-group>
      <div class="flex-spacer"></div>
      <el-button type="primary" size="small" :icon="VideoPlay" @click="handleSave">保存编排</el-button>
    </div>

    <div class="flow-container">
      <div class="canvas">
        <VueFlow>
          <Background />
          <Controls />
        </VueFlow>
      </div>
      
      <div class="properties" v-if="selectedNode">
        <div class="prop-header">
          <span>节点属性: {{ selectedNode.data.type }}</span>
          <el-button type="danger" :icon="Delete" circle size="small" @click="nodes.splice(nodes.indexOf(selectedNode), 1); selectedNode = null" />
        </div>
        
        <el-form label-position="top" size="small">
          <el-form-item label="节点名称">
            <el-input v-model="selectedNode.label" />
          </el-form-item>

          <template v-if="selectedNode.data.type === 'QUERY'">
            <el-form-item label="数据源">
              <el-select v-model="selectedNode.data.config.dataSourceId" style="width: 100%">
                <el-option v-for="ds in dataSources" :key="ds.id" :label="ds.name" :value="ds.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="SQL语句">
              <el-input v-model="selectedNode.data.config.sql" type="textarea" :rows="8" placeholder="支持 ${param} 变量" />
            </el-form-item>
          </template>

          <template v-if="selectedNode.data.type === 'GROOVY'">
            <el-form-item label="Groovy脚本">
              <el-input v-model="selectedNode.data.config.script" type="textarea" :rows="15" placeholder="Groovy script" />
              <div class="help-text">可用变量: db, log, context</div>
            </el-form-item>
          </template>

          <template v-if="selectedNode.data.type === 'OUTPUT'">
            <el-form-item label="输出类型">
              <el-select v-model="selectedNode.data.config.type" style="width: 100%">
                <el-option label="JSON (默认)" value="JSON" />
                <el-option label="静态内容" value="STATIC" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="selectedNode.data.config.type === 'STATIC'" label="内容">
              <el-input v-model="selectedNode.data.config.content" type="textarea" :rows="5" />
            </el-form-item>
          </template>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.api-orchestration {
  display: flex;
  flex-direction: column;
  height: 600px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.toolbar {
  padding: 8px;
  background-color: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
  display: flex;
  align-items: center;
}

.flex-spacer {
  flex: 1;
}

.flow-container {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.canvas {
  flex: 1;
  height: 100%;
}

.properties {
  width: 300px;
  border-left: 1px solid #dcdfe6;
  padding: 16px;
  background-color: #fff;
  overflow-y: auto;
}

.help-text {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.prop-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #eee;
  font-weight: bold;
}
</style>
