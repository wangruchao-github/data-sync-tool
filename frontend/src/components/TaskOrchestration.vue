<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue';
import { VueFlow, useVueFlow, MarkerType } from '@vue-flow/core';
import { Background } from '@vue-flow/background';
import { Controls } from '@vue-flow/controls';
import { MiniMap } from '@vue-flow/minimap';
import { ElMessage, ElMessageBox } from 'element-plus';
import { VideoPlay, CircleCheck, Plus, Edit, Delete, Check, Close, Right, DocumentCopy, Download, Upload, MoreFilled, Folder, FolderOpened, Document } from '@element-plus/icons-vue';
import axios from 'axios';
import CronConfig from './CronConfig.vue';

const props = defineProps<{
  initialTaskId?: number | null
}>();

const emit = defineEmits(['clear-initial-task']);

const { 
  onConnect, 
  addEdges, 
  toObject, 
  addNodes, 
  project, 
  onNodeClick, 
  onEdgeClick,
  nodes, 
  edges,
  setNodes,
  setEdges 
} = useVueFlow();

const quickConnectVisible = ref(false);
const quickConnectPos = ref({ x: 0, y: 0 });
const currentEdge = ref<any>(null);

// 任务列表相关
const taskList = ref<any[]>([]);
const taskTree = ref<any[]>([]);
const taskId = ref<number | null>(null);
const taskName = ref('');
const taskStatus = ref('DISABLED');
const taskCron = ref('0 0 * * * ?');
const taskDescription = ref('');
const currentParentId = ref<number | null>(null);

// 状态控制
const isEditingTopName = ref(false);
const taskSettingsVisible = ref(false);
const showDrawer = ref(false);
const previewVisible = ref(false);
const editingTaskId = ref<number | null>(null);
const editingTaskName = ref('');

// 节点与数据源
const selectedNode = ref<any>(null);
const dataSources = ref<any[]>([]);
const upstreamFields = ref<string[]>([]);
const previewData = ref<any[]>([]);
const previewColumns = ref<string[]>([]);
const latestLog = ref<any>(null);

// 树形结构转换逻辑
const buildTree = (list: any[]) => {
  const map: any = {};
  const tree: any[] = [];
  
  list.forEach(item => {
    map[item.id] = { ...item, children: [] };
  });
  
  list.forEach(item => {
    if (item.parentId && map[item.parentId]) {
      map[item.parentId].children.push(map[item.id]);
    } else {
      tree.push(map[item.id]);
    }
  });
  
  return tree;
};

const fetchTasks = async () => {
  try {
    const res = await axios.get('/api/tasks');
    taskList.value = res.data || [];
    taskTree.value = buildTree(taskList.value);
  } catch (e) {
    ElMessage.error('加载任务列表失败');
  }
};

const fetchTask = async (id: number) => {
  try {
    const res = await axios.get(`/api/tasks/${id}`);
    const task = res.data;
    taskId.value = task.id;
    taskName.value = task.name;
    taskStatus.value = task.status;
    taskCron.value = task.cron;
    taskDescription.value = task.description;
    currentParentId.value = task.parentId || null;
    
    if (task.content) {
      const flowData = JSON.parse(task.content);
      setNodes(flowData.nodes || []);
      setEdges(flowData.edges || []);
    } else {
      setNodes([]);
      setEdges([]);
    }
    
    // 获取最新日志
    fetchLatestLog(id);
  } catch (e) {
    ElMessage.error('加载任务详情失败');
  }
};

const fetchLatestLog = async (id: number) => {
  try {
    const res = await axios.get(`/api/tasks/${id}/latest-log`);
    latestLog.value = res.data;
  } catch (e) {
    console.error('Failed to fetch latest log');
  }
};

const createNewFolder = async (parentId: number | null = null) => {
  try {
    const { value: folderName } = await ElMessageBox.prompt('请输入文件夹名称', '新建文件夹', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '名称不能为空'
    });
    
    await axios.post('/api/tasks', {
      name: folderName,
      type: 'FOLDER',
      parentId: parentId,
      status: 'DISABLED'
    });
    
    fetchTasks();
    ElMessage.success('文件夹已创建');
  } catch (e) {
    // Cancelled
  }
};

const createNewTask = (parentId: number | null = null) => {
  taskId.value = null;
  taskName.value = `新任务_${Date.now().toString().slice(-4)}`;
  taskStatus.value = 'DISABLED';
  taskCron.value = '0 0 * * * ?';
  taskDescription.value = '';
  setNodes([]);
  setEdges([]);
  currentParentId.value = parentId;
  latestLog.value = null;
};

const onSave = async () => {
  if (!taskName.value) {
    ElMessage.warning('任务名称不能为空');
    return;
  }
  const flowData = toObject();
  try {
    const payload = {
      id: taskId.value,
      name: taskName.value,
      content: JSON.stringify(flowData),
      status: taskStatus.value,
      cron: taskCron.value,
      description: taskDescription.value,
      type: 'TASK',
      parentId: currentParentId.value || undefined
    };
    const res = await axios.post('/api/tasks', payload);
    taskId.value = res.data.id;
    ElMessage.success('任务配置已保存');
    fetchTasks(); // 刷新列表
  } catch (e) {
    ElMessage.error('保存失败');
  }
};

const onExecute = async () => {
  if (!taskId.value) {
    ElMessage.warning('请先保存任务后再执行');
    return;
  }
  
  try {
    ElMessage.info('任务启动中...');
    const res = await axios.post(`/api/tasks/${taskId.value}/execute`);
    ElMessage.success('任务启动成功: ' + res.data);
    
    // 轮询获取最新日志
    fetchLatestLog(taskId.value!);
  } catch (e: any) {
    ElMessage.error('执行失败: ' + (e.response?.data?.message || e.message));
  }
};

const startEditTaskName = (task: any) => {
  editingTaskId.value = task.id;
  editingTaskName.value = task.name;
};

const saveTaskName = async (task: any) => {
  if (!editingTaskName.value || editingTaskName.value === task.name) {
    editingTaskId.value = null;
    return;
  }
  
  try {
    await axios.post('/api/tasks', {
      ...task,
      name: editingTaskName.value
    });
    editingTaskId.value = null;
    fetchTasks();
    if (taskId.value === task.id) {
      taskName.value = editingTaskName.value;
    }
  } catch (e) {
    ElMessage.error('修改失败');
  }
};

const deleteTask = (task: any) => {
  ElMessageBox.confirm(`确定要删除 ${task.type === 'FOLDER' ? '文件夹' : '任务'} "${task.name}" 吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await axios.delete(`/api/tasks/${task.id}`);
      ElMessage.success('删除成功');
      if (taskId.value === task.id) {
        createNewTask();
      }
      fetchTasks();
    } catch (e) {
      ElMessage.error('删除失败');
    }
  });
};

const copyTask = async (task: any) => {
  try {
    await axios.post(`/api/tasks/${task.id}/copy`);
    ElMessage.success('复制成功');
    fetchTasks();
  } catch (e) {
    ElMessage.error('复制失败');
  }
};

const exportTask = (task: any) => {
  const data = JSON.stringify(task, null, 2);
  const blob = new Blob([data], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `${task.name}.json`;
  link.click();
  URL.revokeObjectURL(url);
};

const handleImport = (file: any) => {
  const reader = new FileReader();
  reader.onload = async (e: any) => {
    try {
      const taskData = JSON.parse(e.target.result);
      delete taskData.id;
      taskData.name = taskData.name + '_导入';
      await axios.post('/api/tasks', taskData);
      ElMessage.success('导入成功');
      fetchTasks();
    } catch (error) {
      ElMessage.error('解析文件失败');
    }
  };
  reader.readAsText(file.raw);
};

const startEditTopName = () => {
  isEditingTopName.value = true;
};

const saveTopName = () => {
  isEditingTopName.value = false;
  if (taskId.value) {
    onSave();
  }
};

const fetchDataSources = async () => {
  try {
    const res = await axios.get('/api/datasources');
    dataSources.value = res.data || [];
  } catch (e) {
    console.error('Failed to fetch data sources');
  }
};

const loadUpstreamFields = async () => {
  if (!selectedNode.value) return;
  
  // 查找指向当前节点的所有边
  const incomingEdges = edges.value.filter(e => e.target === selectedNode.value.id);
  if (incomingEdges.length === 0) {
    ElMessage.warning('请先连接上游节点');
    return;
  }
  
  const fields = new Set<string>();
  
  for (const edge of incomingEdges) {
    const sourceNode = nodes.value.find(n => n.id === edge.source);
    if (!sourceNode) continue;
    
    // 情况1: 上游是输入节点
    if (sourceNode.type === 'input') {
      if (sourceNode.data.dataSourceId && sourceNode.data.sql) {
        try {
          const res = await axios.post(`/api/datasources/${sourceNode.data.dataSourceId}/preview`, {
            sql: sourceNode.data.sql
          });
          if (res.data && res.data.length > 0) {
            Object.keys(res.data[0]).forEach(k => fields.add(k));
          }
        } catch (e) {
          console.error('Failed to fetch upstream fields from input node', e);
        }
      }
    } 
    // 情况2: 上游是字段映射节点
    else if (sourceNode.label === '字段映射' || sourceNode.label === 'Mapping' || sourceNode.data.mappings?.length > 0) {
      if (sourceNode.data.mappings?.length > 0) {
        sourceNode.data.mappings.forEach((m: any) => {
          if (m.target) fields.add(m.target);
        });
      } else {
        // 如果映射节点没有配置映射，递归尝试寻找映射节点的上游
        // 为了简单，这里只提示
        ElMessage.warning(`上游节点 "${sourceNode.label}" 未配置映射关系，无法自动同步`);
      }
    }
    // 情况3: 上游节点本身有 fields (如其它输出节点或转换节点)
    else if (sourceNode.data.fields?.length > 0) {
      sourceNode.data.fields.forEach((f: any) => {
        if (f.name) fields.add(f.name);
      });
    }
  }
  
  if (fields.size > 0) {
    upstreamFields.value = Array.from(fields);
    ElMessage.success(`已成功同步上游 ${fields.size} 个字段`);
  } else {
    // 降级方案：如果没有找到动态字段，尝试提示用户
    ElMessage.info('未检测到上游字段，请确保上游输入节点已正确配置 SQL 并通过测试');
  }
};

const previewSql = async () => {
  if (!selectedNode.value?.data.dataSourceId || !selectedNode.value?.data.sql) {
    ElMessage.warning('请先配置数据源和 SQL');
    return;
  }
  try {
    const res = await axios.post(`/api/datasources/${selectedNode.value.data.dataSourceId}/preview`, {
      sql: selectedNode.value.data.sql
    });
    
    if (res.data && res.data.length > 0) {
      previewColumns.value = Object.keys(res.data[0]);
      previewData.value = res.data;
    } else {
      previewColumns.value = [];
      previewData.value = [];
      ElMessage.info('查询结果为空');
    }
    previewVisible.value = true;
  } catch (e: any) {
    console.error('SQL preview failed:', e);
    ElMessage.error('预览失败: ' + (e.response?.data?.message || e.message));
  }
};

const deleteNode = () => {
  if (selectedNode.value) {
    nodes.value = nodes.value.filter(n => n.id !== selectedNode.value.id);
    edges.value = edges.value.filter(e => e.source !== selectedNode.value.id && e.target !== selectedNode.value.id);
    showDrawer.value = false;
    selectedNode.value = null;
  }
};

const onDragStart = (event: DragEvent, type: string, label: string) => {
  if (event.dataTransfer) {
    event.dataTransfer.setData('application/vueflow', JSON.stringify({ type, label }));
    event.dataTransfer.effectAllowed = 'move';
  }
};

const onDragOver = (event: DragEvent) => {
  event.preventDefault();
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move';
  }
};

const createDefaultNodeData = () => ({
  dataSourceId: null,
  sql: '',
  batchSize: 1000,
  mappings: [],
  fields: [],
  tableName: '',
  writeMode: 'APPEND',
  conflictStrategy: 'UPDATE',
  deleteAfterSync: false,
  sourceTableName: '',
  sourcePrimaryKey: 'id'
});

const onDrop = (event: DragEvent) => {
  const dataStr = event.dataTransfer?.getData('application/vueflow');
  if (!dataStr) return;
  
  const { type, label } = JSON.parse(dataStr);
  const position = project({ x: event.clientX - 250, y: event.clientY - 100 });
  
  const newNode = {
    id: `node_${Date.now()}`,
    type,
    position,
    label,
    data: createDefaultNodeData()
  };
  
  addNodes([newNode]);
};

const handleQuickAddNode = (type: string, label: string) => {
  if (!currentEdge.value) return;
  
  const sourceNode = nodes.value.find(n => n.id === currentEdge.value.source);
  const targetNode = nodes.value.find(n => n.id === currentEdge.value.target);
  
  if (!sourceNode || !targetNode) return;
  
  const position = {
    x: (sourceNode.position.x + targetNode.position.x) / 2,
    y: (sourceNode.position.y + targetNode.position.y) / 2
  };
  
  const newNodeId = `node_${Date.now()}`;
  const newNode = {
    id: newNodeId,
    type,
    position,
    label,
    data: createDefaultNodeData()
  };
  
  addNodes([newNode]);
  
  // 删除旧边，添加两条新边
  edges.value = edges.value.filter(e => e.id !== currentEdge.value.id);
  addEdges([
    { id: `e_${sourceNode.id}-${newNodeId}`, source: sourceNode.id, target: newNodeId, markerEnd: MarkerType.ArrowClosed },
    { id: `e_${newNodeId}-${targetNode.id}`, source: newNodeId, target: targetNode.id, markerEnd: MarkerType.ArrowClosed }
  ]);
  
  quickConnectVisible.value = false;
};

const handleNodeDrop = async (draggingNode: any, dropNode: any, dropType: string) => {
  const draggingData = draggingNode.data;
  const dropData = dropNode.data;
  
  let newParentId: number | null = null;
  
  if (dropType === 'inner') {
    // 拖入文件夹内部
    if (dropData.type !== 'FOLDER') {
      ElMessage.warning('只能将项移动到文件夹中');
      fetchTasks(); // 恢复原状
      return;
    }
    newParentId = dropData.id;
  } else {
    // 拖到同级
    newParentId = dropData.parentId || null;
  }
  
  try {
    await axios.post('/api/tasks', {
      ...draggingData,
      parentId: newParentId
    });
    ElMessage.success('移动成功');
    fetchTasks();
  } catch (e) {
    ElMessage.error('移动失败');
    fetchTasks();
  }
};

onMounted(() => {
  fetchTasks();
  fetchDataSources();
});

onNodeClick(({ node }) => {
  selectedNode.value = node;
  showDrawer.value = true;
});

const nodeColor = (node: any) => {
  switch (node.type) {
    case 'input': return '#409eff';
    case 'output': return '#f56c6c';
    default: return '#67c23a';
  }
};

const loadTargetTableSchema = async () => {
  if (!selectedNode.value?.data.dataSourceId || !selectedNode.value?.data.tableName) {
    ElMessage.warning('请先选择数据源和填写目标表名');
    return;
  }
  
  // 如果上游字段为空，尝试先加载一下
  if (upstreamFields.value.length === 0) {
    await loadUpstreamFields();
  }
  
  try {
    const res = await axios.get(`/api/datasources/${selectedNode.value.data.dataSourceId}/table-columns`, {
      params: { tableName: selectedNode.value.data.tableName }
    });
    
    if (res.data && res.data.length > 0) {
      if (selectedNode.value.data.fields.length === 0) {
        selectedNode.value.data.fields = res.data.map((col: any) => {
          const matchedUpstream = upstreamFields.value.find(uf => uf.toLowerCase() === col.name.toLowerCase());
          return {
            sourceName: matchedUpstream || '', 
            name: col.name,
            type: col.type,
            isPk: col.isPk,
            comment: col.comment || ''
          };
        });
        
        const pkField = res.data.find((col: any) => col.isPk);
        if (pkField) {
          selectedNode.value.data.primaryKey = pkField.name;
        }
        
        ElMessage.success('已从目标表加载字段配置');
      } else {
        ElMessageBox.confirm(
          '当前已存在字段配置，是否覆盖？',
          '提示',
          {
            confirmButtonText: '覆盖',
            cancelButtonText: '取消',
            type: 'warning',
          }
        ).then(() => {
          selectedNode.value.data.fields = res.data.map((col: any) => {
            const matchedUpstream = upstreamFields.value.find(uf => uf.toLowerCase() === col.name.toLowerCase());
            return {
              sourceName: matchedUpstream || '',
              name: col.name,
              type: col.type,
              isPk: col.isPk,
              comment: col.comment || ''
            };
          });
          const pkField = res.data.find((col: any) => col.isPk);
          if (pkField) {
            selectedNode.value.data.primaryKey = pkField.name;
          }
          ElMessage.success('已从目标表加载字段配置');
        }).catch(() => {});
      }
    } else {
      ElMessage.info('目标表暂无字段或表不存在');
    }
  } catch (error: any) {
    console.error('Failed to load table schema:', error);
    ElMessage.error('加载目标表结构失败: ' + (error.response?.data?.message || error.message));
  }
};

const handlePkChange = (field: any) => {
  if (field.isPk) {
    selectedNode.value.data.fields.forEach((f: any) => {
      if (f !== field) f.isPk = false;
    });
    selectedNode.value.data.primaryKey = field.name;
  } else if (selectedNode.value.data.primaryKey === field.name) {
    selectedNode.value.data.primaryKey = '';
  }
};

onEdgeClick(({ event, edge }) => {
  currentEdge.value = edge;
  quickConnectVisible.value = true;
  
  // 处理 MouseEvent 和 TouchEvent 的坐标差异
   let x = 0, y = 0;
    if ('clientX' in event) {
      const mouseEvent = event as MouseEvent;
      x = mouseEvent.clientX;
      y = mouseEvent.clientY;
    } else if ('touches' in event) {
      const touchEvent = event as TouchEvent;
      const touch = touchEvent.touches?.[0];
      if (touch) {
        x = touch.clientX;
        y = touch.clientY;
      }
    }
  
  quickConnectPos.value = { x, y };
});

onConnect((params) => {
  addEdges([{ ...params, markerEnd: MarkerType.ArrowClosed }]);
});

watch(() => props.initialTaskId, (newId) => {
  if (newId) {
    fetchTask(newId);
  }
}, { immediate: true });
</script>

<template>
  <div class="orchestration-container">
    <div class="top-bar">
      <div class="task-info">
        <el-input 
          v-if="isEditingTopName" 
          v-model="taskName" 
          size="small" 
          @blur="saveTopName" 
          @keyup.enter="saveTopName"
          style="width: 200px"
          ref="topNameInput"
        />
        <div v-else class="task-name-display" @click="startEditTopName">
          <span class="name-text">{{ taskName || '未命名任务' }}</span>
          <el-icon class="edit-icon"><Edit /></el-icon>
        </div>
        <el-tag v-if="taskId" type="info" class="task-id-tag">ID: {{ taskId }}</el-tag>
        
        <!-- 最近执行状态 -->
        <div v-if="latestLog" class="latest-log-badge" style="margin-left: 12px;">
          <el-tag :type="latestLog.result === 'SUCCESS' ? 'success' : (latestLog.result === 'RUNNING' ? 'warning' : 'danger')" size="small" effect="plain">
            {{ latestLog.result === 'RUNNING' ? '正在执行' : '上次执行' }}: {{ latestLog.result === 'SUCCESS' ? '成功' : (latestLog.result === 'RUNNING' ? '进行中' : '失败') }} 
            ({{ new Date(latestLog.startTime).toLocaleString() }})
          </el-tag>
          <div v-if="latestLog.result === 'RUNNING'" class="running-progress">
            <el-progress 
              :percentage="latestLog.totalCount > 0 ? Math.floor((latestLog.processedCount / latestLog.totalCount) * 100) : 0" 
              :stroke-width="12"
              :indeterminate="latestLog.totalCount <= 0"
              style="width: 150px"
            />
            <span class="progress-text">{{ latestLog.processedCount || 0 }} / {{ latestLog.totalCount > 0 ? latestLog.totalCount : '?' }}</span>
          </div>
        </div>
      </div>
      <div class="task-actions">
        <el-button :icon="Plus" @click="createNewTask">新建任务</el-button>
        <el-button @click="taskSettingsVisible = true">任务设置</el-button>
        <el-button type="primary" :icon="CircleCheck" @click="onSave">保存配置</el-button>
        <el-button type="success" :icon="VideoPlay" @click="onExecute">立即执行</el-button>
      </div>
    </div>

    <div class="main-content">
      <!-- 任务列表侧边栏 -->
      <div class="task-sidebar">
        <div class="sidebar-header">
          <span class="sidebar-title">已保存任务</span>
          <div class="header-actions">
            <el-upload
              action=""
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleImport"
              style="display: inline-block; margin-right: 8px;"
            >
              <el-button link :icon="Upload" title="导入任务"></el-button>
            </el-upload>
            <el-dropdown trigger="click">
              <el-button link :icon="Plus" title="新建"></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="createNewTask(null)">
                    <el-icon><Document /></el-icon> 新建任务
                  </el-dropdown-item>
                  <el-dropdown-item @click="createNewFolder(null)">
                    <el-icon><Folder /></el-icon> 新建文件夹
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
        <div class="task-list">
          <el-tree
            :data="taskTree"
            node-key="id"
            :expand-on-click-node="false"
            highlight-current
            draggable
            @node-click="(data: any) => data.type === 'TASK' && fetchTask(data.id)"
            @node-drop="handleNodeDrop"
          >
            <template #default="{ node, data }">
              <div class="custom-tree-node" :class="{ active: taskId === data.id }">
                <div v-if="editingTaskId === data.id" class="task-edit-wrapper" @click.stop>
                  <el-input 
                    v-model="editingTaskName" 
                    size="small"
                    @keyup.enter="saveTaskName(data)"
                    @blur="saveTaskName(data)"
                    ref="nameInput"
                    auto-focus
                  />
                </div>
                <template v-else>
                  <el-icon class="node-icon">
                    <Folder v-if="data.type === 'FOLDER' && !node.expanded" />
                    <FolderOpened v-else-if="data.type === 'FOLDER' && node.expanded" />
                    <Document v-else />
                  </el-icon>
                  <span class="node-label" :title="data.name">{{ data.name }}</span>
                  <div class="node-actions">
                    <el-dropdown trigger="click" @click.stop>
                      <el-button link :icon="MoreFilled"></el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item v-if="data.type === 'FOLDER'" @click="createNewTask(data.id)">
                            <el-icon><Plus /></el-icon> 新建子任务
                          </el-dropdown-item>
                          <el-dropdown-item v-if="data.type === 'FOLDER'" @click="createNewFolder(data.id)">
                            <el-icon><Folder /></el-icon> 新建子文件夹
                          </el-dropdown-item>
                          <el-dropdown-item divided @click="startEditTaskName(data)">
                            <el-icon><Edit /></el-icon> 重命名
                          </el-dropdown-item>
                          <el-dropdown-item v-if="data.type === 'TASK'" @click="copyTask(data)">
                            <el-icon><DocumentCopy /></el-icon> 复制任务
                          </el-dropdown-item>
                          <el-dropdown-item v-if="data.type === 'TASK'" @click="exportTask(data)">
                            <el-icon><Download /></el-icon> 导出任务
                          </el-dropdown-item>
                          <el-dropdown-item @click="deleteTask(data)" style="color: #f56c6c">
                            <el-icon><Delete /></el-icon> 删除{{ data.type === 'FOLDER' ? '文件夹' : '任务' }}
                          </el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </template>
              </div>
            </template>
          </el-tree>
          <el-empty v-if="taskList.length === 0" description="暂无任务" :image-size="40" />
        </div>
      </div>

      <!-- 节点侧边栏 -->
      <div class="node-sidebar">
        <div class="sidebar-section">
          <div class="section-title">输入节点</div>
          <div class="node-item" draggable="true" @dragstart="onDragStart($event, 'input', 'MySQL 输入')">MySQL 输入</div>
          <div class="node-item" draggable="true" @dragstart="onDragStart($event, 'input', 'Oracle 输入')">Oracle 输入</div>
        </div>
        <div class="sidebar-section">
          <div class="section-title">处理节点</div>
          <div class="node-item" draggable="true" @dragstart="onDragStart($event, 'default', '字段映射')">字段映射</div>
          <div class="node-item" draggable="true" @dragstart="onDragStart($event, 'default', '数据过滤')">数据过滤</div>
        </div>
        <div class="sidebar-section">
          <div class="section-title">输出节点</div>
          <div class="node-item" draggable="true" @dragstart="onDragStart($event, 'output', 'MySQL 输出')">MySQL 输出</div>
          <div class="node-item" draggable="true" @dragstart="onDragStart($event, 'output', 'ES 输出')">ES 输出</div>
        </div>
      </div>
      
      <div class="canvas-area" @drop="onDrop" @dragover="onDragOver">
        <VueFlow 
          fit-view-on-init 
        >
          <Background />
          <Controls />
          <MiniMap :node-color="nodeColor" />
        </VueFlow>

        <!-- 快捷连线菜单 -->
        <div 
          v-if="quickConnectVisible" 
          class="quick-connect-menu" 
          :style="{ left: quickConnectPos.x + 'px', top: quickConnectPos.y + 'px' }"
          @mouseleave="quickConnectVisible = false"
        >
          <div class="menu-header">在连线中间插入节点</div>
          <div class="menu-item" @click="handleQuickAddNode('default', '字段映射')">
            <el-icon><Edit /></el-icon> 字段映射
          </div>
          <div class="menu-item" @click="handleQuickAddNode('default', '数据过滤')">
            <el-icon><Check /></el-icon> 数据过滤
          </div>
          <div class="menu-item" @click="handleQuickAddNode('output', '数据输出')">
            <el-icon><Download /></el-icon> 数据输出
          </div>
          <div class="menu-divider"></div>
          <div class="menu-item cancel" @click="quickConnectVisible = false">
            <el-icon><Close /></el-icon> 取消
          </div>
        </div>
      </div>
    </div>

    <!-- 节点属性配置 -->
    <el-drawer
      v-model="showDrawer"
      :title="selectedNode ? `配置节点: ${selectedNode.label}` : '节点属性配置'"
      size="650px"
      destroy-on-close
    >
      <div v-if="selectedNode" class="node-config">
        <el-form label-position="top">
          <el-form-item label="节点名称">
            <el-input v-model="selectedNode.label" />
          </el-form-item>

          <!-- 输入节点配置 -->
          <template v-if="selectedNode.type === 'input'">
            <el-form-item label="数据源">
              <el-select v-model="selectedNode.data.dataSourceId" placeholder="选择数据源" style="width: 100%">
                <el-option 
                  v-for="ds in dataSources" 
                  :key="ds.id" 
                  :label="`${ds.name} (${ds.type})`" 
                  :value="ds.id" 
                />
              </el-select>
            </el-form-item>
            <el-form-item label="SQL 语句">
              <el-input type="textarea" v-model="selectedNode.data.sql" :rows="8" placeholder="SELECT * FROM table" />
              <div style="margin-top: 10px; display: flex; justify-content: space-between; align-items: center;">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <span style="font-size: 12px; color: #606266;">每批条数:</span>
                  <el-input-number v-model="selectedNode.data.batchSize" :min="100" :max="10000" :step="100" size="small" />
                </div>
                <el-button type="primary" link @click="previewSql">预览结果</el-button>
              </div>
            </el-form-item>
          </template>

          <!-- 字段映射节点配置 -->
          <template v-else-if="selectedNode.label === '字段映射' || selectedNode.label === 'Mapping'">
            <div class="section-header">
              <el-divider content-position="left">字段映射</el-divider>
              <el-button type="primary" link :icon="Check" @click="loadUpstreamFields">同步上游字段</el-button>
            </div>
            <div class="mapping-table">
              <div class="mapping-header">
                <span class="col-source">源字段</span>
                <span class="col-arrow-placeholder"></span>
                <span class="col-target">目标字段</span>
                <span class="col-action-placeholder"></span>
              </div>
              <div v-for="(m, idx) in selectedNode.data.mappings" :key="idx" class="mapping-row">
                <el-select v-model="m.source" size="small" placeholder="选择源字段" class="col-source" filterable clearable>
                  <el-option v-for="uf in upstreamFields" :key="uf" :label="uf" :value="uf" />
                </el-select>
                <el-icon class="col-arrow"><Right /></el-icon>
                <el-input v-model="m.target" size="small" class="col-target" />
                <el-button link type="danger" :icon="Delete" @click="selectedNode.data.mappings.splice(idx, 1)" style="margin-left: 8px;"></el-button>
              </div>
              <el-button type="primary" link :icon="Plus" @click="selectedNode.data.mappings.push({ source: '', target: '' })">添加映射</el-button>
              <el-empty v-if="!selectedNode.data.mappings?.length" description="未检测到上游字段，请确保已连接输入节点并配置 SQL" :image-size="40" />
            </div>
          </template>

          <!-- 输出节点配置 -->
          <template v-else-if="selectedNode.type === 'output'">
            <el-form-item label="数据源">
              <el-select v-model="selectedNode.data.dataSourceId" placeholder="选择数据源" style="width: 100%">
                <el-option 
                  v-for="ds in dataSources" 
                  :key="ds.id" 
                  :label="`${ds.name} (${ds.type})`" 
                  :value="ds.id" 
                />
              </el-select>
            </el-form-item>
            <el-form-item label="目标表名">
              <el-input v-model="selectedNode.data.tableName" placeholder="target_table" />
            </el-form-item>
            
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="写入方式">
                  <el-select v-model="selectedNode.data.writeMode" style="width: 100%">
                    <el-option label="追加写入" value="APPEND" />
                    <el-option label="覆盖写入(清空表)" value="OVERWRITE" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="冲突策略">
                  <el-select v-model="selectedNode.data.conflictStrategy" style="width: 100%">
                    <el-option label="覆盖目标数据" value="UPDATE" />
                    <el-option label="忽略源数据" value="IGNORE" />
                    <el-option label="报错并停止" value="ERROR" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="主键字段">
              <el-select v-model="selectedNode.data.primaryKey" placeholder="选择主键字段（用于冲突检测）" style="width: 100%" clearable>
                <el-option 
                  v-for="f in selectedNode.data.fields" 
                  :key="f.name" 
                  :label="f.name" 
                  :value="f.name" 
                />
              </el-select>
            </el-form-item>

            <el-divider border-style="dashed" />

            <el-form-item>
              <template #label>
                <div style="display: flex; align-items: center; gap: 8px;">
                  <span>数据清理策略</span>
                  <el-tooltip content="同步成功后，根据主键从源表中删除已同步的数据。请谨慎使用！" placement="top">
                    <el-icon style="font-size: 14px; color: #909399;"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
              </template>
              <el-checkbox v-model="selectedNode.data.deleteAfterSync" label="同步成功后删除源数据" size="small" />
            </el-form-item>

            <el-row v-if="selectedNode.data.deleteAfterSync" :gutter="20">
              <el-col :span="12">
                <el-form-item label="源表名">
                  <el-input v-model="selectedNode.data.sourceTableName" placeholder="source_table" size="small" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="源表主键">
                  <el-input v-model="selectedNode.data.sourcePrimaryKey" placeholder="id" size="small" />
                </el-form-item>
              </el-col>
            </el-row>

            <div class="section-header">
              <el-divider content-position="left">输出字段映射</el-divider>
              <div class="header-actions">
                <el-button type="primary" link :icon="Check" @click="loadUpstreamFields">从上游读取</el-button>
                <el-button type="success" link :icon="Right" @click="loadTargetTableSchema">从目标表读取</el-button>
              </div>
            </div>
            <div class="fields-table">
              <div class="fields-header">
                <span class="col-source">源字段</span>
                <span class="col-name">目标字段</span>
                <span class="col-type">类型</span>
                <span class="col-pk">主键</span>
                <span class="col-comment">备注</span>
                <span class="col-action"></span>
              </div>
              <div v-for="(f, idx) in selectedNode.data.fields" :key="idx" class="field-row">
                <el-select v-model="f.sourceName" size="small" placeholder="选择源字段" class="col-source" filterable clearable>
                  <el-option v-for="uf in upstreamFields" :key="uf" :label="uf" :value="uf" />
                </el-select>
                <el-input v-model="f.name" size="small" placeholder="目标字段" class="col-name" />
                <el-select v-model="f.type" size="small" class="col-type">
                  <el-option label="VARCHAR(255)" value="VARCHAR(255)" />
                  <el-option label="VARCHAR(500)" value="VARCHAR(500)" />
                  <el-option label="TEXT" value="TEXT" />
                  <el-option label="LONGTEXT" value="LONGTEXT" />
                  <el-option label="INT" value="INT" />
                  <el-option label="BIGINT" value="BIGINT" />
                  <el-option label="DECIMAL(19,4)" value="DECIMAL(19,4)" />
                  <el-option label="DATETIME" value="DATETIME" />
                  <el-option label="DATE" value="DATE" />
                  <el-option label="BOOLEAN" value="BOOLEAN" />
                  <el-option label="JSON" value="JSON" />
                </el-select>
                <el-checkbox v-model="f.isPk" class="col-pk" @change="handlePkChange(f)" />
                <el-input v-model="f.comment" size="small" placeholder="备注" class="col-comment" />
                <el-button link type="danger" :icon="Delete" @click="selectedNode.data.fields.splice(idx, 1)"></el-button>
              </div>
              <el-button type="primary" link :icon="Plus" @click="selectedNode.data.fields.push({ sourceName: '', name: '', type: 'VARCHAR(255)' })">添加字段</el-button>
              <el-empty v-if="!selectedNode.data.fields?.length" description="未配置字段" :image-size="40" />
            </div>
          </template>
        </el-form>

        <div class="drawer-footer">
          <el-button type="danger" plain :icon="Delete" @click="deleteNode" style="width: 100%">删除节点</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- SQL 预览对话框 -->
    <el-dialog v-model="previewVisible" title="SQL 预览结果" width="80%">
      <el-table :data="previewData" border height="400">
        <el-table-column v-for="col in previewColumns" :key="col" :prop="col" :label="col" />
      </el-table>
    </el-dialog>

    <!-- 任务设置对话框 -->
    <el-dialog v-model="taskSettingsVisible" title="任务全局设置" width="500px">
      <el-form label-position="top">
        <el-form-item label="任务名称">
          <el-input v-model="taskName" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="任务描述">
          <el-input v-model="taskDescription" type="textarea" :rows="3" placeholder="任务详细描述信息" />
        </el-form-item>
        <el-form-item label="调度状态">
          <el-radio-group v-model="taskStatus">
            <el-radio-button label="ENABLED">开启调度</el-radio-button>
            <el-radio-button label="DISABLED">关闭调度</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="Cron 表达式">
          <CronConfig v-model="taskCron" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskSettingsVisible = false">取消</el-button>
        <el-button type="primary" @click="taskSettingsVisible = false; onSave()">保存并应用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.orchestration-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05);
}

.top-bar {
  height: 56px;
  padding: 0 20px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
}

.task-name-display {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background-color 0.2s;
  max-width: 300px;
}

.task-name-display:hover {
  background-color: #f5f7fa;
}

.name-text {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.edit-icon {
  font-size: 14px;
  color: #909399;
}

.task-info {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  white-space: nowrap;
}

.latest-log-badge {
  display: flex;
  align-items: center;
  gap: 12px;
}

.running-progress {
  display: flex;
  align-items: center;
  gap: 8px;
}

.progress-text {
  font-size: 12px;
  color: #909399;
  min-width: 60px;
}

.task-actions {
  display: flex;
  gap: 12px;
}

.main-content {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.task-sidebar {
  width: 280px;
  border-right: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #f5f5f5;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sidebar-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.task-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  padding-right: 8px;
  overflow: hidden;
}

.node-icon {
  margin-right: 6px;
  font-size: 14px;
  color: var(--wechat-green);
}

.node-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-actions {
  display: none;
}

.custom-tree-node:hover .node-actions {
  display: block;
}

.custom-tree-node.active {
  color: var(--wechat-green);
  font-weight: 500;
}

.task-item {
  padding: 10px 12px;
  margin-bottom: 4px;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  transition: all 0.2s;
  font-size: 13px;
  color: #606266;
}

.task-item:hover {
  background-color: #f5f7fa;
}

.task-item.active {
  background-color: #f0f9eb;
  color: var(--wechat-green);
  font-weight: 500;
}

.task-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 8px;
}

.task-item-actions {
  display: none;
  gap: 4px;
}

.popover-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.popover-actions :deep(.el-button) {
  margin-left: 0 !important;
  width: 100%;
  justify-content: flex-start;
  padding: 8px 12px;
}

.popover-actions :deep(.el-button):hover {
  background-color: #f5f7fa;
}

.task-item:hover .task-item-actions {
  display: flex;
}

.task-edit-wrapper {
  width: 100%;
}

.node-sidebar {
  width: 160px;
  border-right: 1px solid #f0f0f0;
  padding: 16px;
  background: #fafafa;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.fields-header, .field-row {
  display: grid;
  grid-template-columns: 3fr 2fr 150px 60px 2fr 40px;
  gap: 12px;
  align-items: center;
  padding: 8px 0;
  width: 100%;
}

.fields-header span {
  font-size: 13px;
  color: #909399;
  font-weight: bold;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.field-row > * {
  min-width: 0;
}

.field-row :deep(.el-select),
.field-row :deep(.el-input) {
  width: 100% !important;
}

.col-pk {
  display: flex;
  justify-content: center;
}

/* 字段映射节点的样式 */
.mapping-header, .mapping-row {
  display: grid;
  grid-template-columns: 3fr 40px 3fr 40px;
  gap: 12px;
  align-items: center;
  padding: 8px 0;
}

.mapping-header span {
  font-size: 13px;
  color: #909399;
  font-weight: bold;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mapping-row > * {
  min-width: 0;
}

.mapping-row :deep(.el-select),
.mapping-row :deep(.el-input) {
  width: 100% !important;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20px;
  margin-bottom: 10px;
}

.clickable-tag {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
}

.clickable-tag:hover {
  opacity: 0.9;
}

.edit-icon {
  font-size: 12px;
}

.section-header :deep(.el-divider) {
  margin: 0;
  flex: 1;
}

.header-actions {
  display: flex;
  gap: 8px;
  margin-left: 10px;
}

.drawer-footer {
  margin-top: 30px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}

.section-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 10px;
  font-weight: bold;
}

.mapping-table {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.col-target {
  flex: 1;
}

.col-arrow {
  color: #c0c4cc;
  font-size: 16px;
}

.fields-table {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.node-item {
  padding: 10px;
  margin-bottom: 8px;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: grab;
  font-size: 13px;
  text-align: center;
  transition: all 0.3s;
}

.node-item:hover {
  border-color: var(--wechat-green);
  color: var(--wechat-green);
  background-color: #f0f9eb;
}

.canvas-area {
  flex: 1;
  height: 100%;
  position: relative;
}

/* Vue Flow Styles */

:deep(.vue-flow__node) {
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  background: #fff;
  border: 1px solid #dcdfe6;
  padding: 10px;
  min-width: 120px;
  text-align: center;
}

:deep(.vue-flow__node.selected) {
  border-color: var(--wechat-green);
  box-shadow: 0 0 0 2px rgba(7, 193, 96, 0.2);
}

:deep(.vue-flow__handle) {
  width: 8px;
  height: 8px;
  background: var(--wechat-green);
}

:deep(.vue-flow__edge-path) {
  stroke-width: 2;
}

:deep(.vue-flow__minimap) {
  background-color: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1);
}

:deep(.el-button--primary) {
  --el-button-bg-color: var(--wechat-green);
  --el-button-border-color: var(--wechat-green);
}

/* 快捷连线菜单样式 */
.quick-connect-menu {
  position: fixed;
  z-index: 1000;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  border: 1px solid #ebeef5;
  padding: 6px 0;
  min-width: 140px;
}

.menu-header {
  padding: 8px 16px;
  font-size: 12px;
  color: #909399;
  border-bottom: 1px solid #f2f6fc;
  margin-bottom: 4px;
}

.menu-item {
  padding: 8px 16px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: all 0.2s;
}

.menu-item:hover {
  background-color: #f5f7fa;
  color: var(--wechat-green);
}

.menu-item.cancel {
  color: #f56c6c;
}

.menu-item.cancel:hover {
  background-color: #fef0f0;
}

.menu-divider {
  height: 1px;
  background-color: #f2f6fc;
  margin: 4px 0;
}
</style>
