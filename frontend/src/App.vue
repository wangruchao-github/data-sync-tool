<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { Coin, Operation, Document, Monitor, Setting, SwitchButton } from '@element-plus/icons-vue';
import axios from 'axios';
import DataSourceList from './components/DataSourceList.vue';
import TaskOrchestration from './components/TaskOrchestration.vue';
import ExecutionLogs from './components/ExecutionLogs.vue';
import MonitorDashboard from './components/MonitorDashboard.vue';
import Login from './components/Login.vue';
import SystemSettings from './components/SystemSettings.vue';

const activeIndex = ref('0');
const targetTaskId = ref<number | null>(null);
const isLoggedIn = ref(!!localStorage.getItem('token'));
const username = ref(localStorage.getItem('username') || '管理员');

const systemConfigs = ref<Record<string, string>>({
  'sys.name': '数据集成工具',
  'sys.logo': `${import.meta.env.BASE_URL || ''}vite.svg`.replace(/\/+/g, '/'),
  'sys.footer': '© 2025 Data Sync Tool. All rights reserved.'
});

const fetchSystemConfigs = async () => {
  try {
    const res = await axios.get('/api/config/public');
    if (res.data) {
      systemConfigs.value = { ...systemConfigs.value, ...res.data };
      updateBrowserInfo();
    }
  } catch (e) {
    console.error('Failed to fetch system configs:', e);
  }
};

const updateBrowserInfo = () => {
  // 更新标题
  const name = systemConfigs.value['sys.name'];
  if (name) {
    document.title = name;
  }
  
  // 更新 favicon
  const logo = systemConfigs.value['sys.logo'];
  if (logo) {
    let link: HTMLLinkElement | null = document.querySelector("link[rel*='icon']");
    if (!link) {
      link = document.createElement('link');
      link.rel = 'icon';
      document.head.appendChild(link);
    }
    link.href = logo;
  }
};

const handleSelect = (key: string) => {
  activeIndex.value = key;
};

const handleJumpToTask = (taskId: number) => {
  targetTaskId.value = taskId;
  activeIndex.value = '2';
};

const handleLoginSuccess = () => {
  isLoggedIn.value = true;
  username.value = localStorage.getItem('username') || '管理员';
  fetchSystemConfigs();
};

const handleLogout = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('username');
  isLoggedIn.value = false;
  activeIndex.value = '0';
};

onMounted(() => {
  fetchSystemConfigs();
});
</script>

<template>
  <div v-if="!isLoggedIn">
    <Login :system-configs="(systemConfigs as any)" @login-success="handleLoginSuccess" />
  </div>
  <el-container v-else class="layout-container">
    <el-aside width="240px" class="aside">
      <div class="logo-container">
        <img :src="systemConfigs['sys.logo']" alt="logo" class="logo" />
        <span class="title">{{ systemConfigs['sys.name'] }}</span>
      </div>
      <el-menu
        :default-active="activeIndex"
        class="el-menu-vertical"
        @select="handleSelect"
      >
        <el-menu-item index="0">
          <el-icon><Monitor /></el-icon>
          <span>监控看板</span>
        </el-menu-item>
        <el-menu-item index="1">
          <el-icon><Coin /></el-icon>
          <span>数据源管理</span>
        </el-menu-item>
        <el-menu-item index="2">
          <el-icon><Operation /></el-icon>
          <span>任务编排</span>
        </el-menu-item>
        <el-menu-item index="3">
          <el-icon><Document /></el-icon>
          <span>运行日志</span>
        </el-menu-item>
        <el-menu-item index="4">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>
      
      <div class="aside-footer">
        <div class="footer-text">{{ systemConfigs['sys.footer'] }}</div>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span v-if="activeIndex === '0'">监控看板</span>
          <span v-else-if="activeIndex === '1'">数据源管理</span>
          <span v-else-if="activeIndex === '2'">任务编排</span>
          <span v-else-if="activeIndex === '3'">运行日志</span>
          <span v-else-if="activeIndex === '4'">系统设置</span>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleLogout">
            <div class="user-info">
              <el-avatar :size="32" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
              <span class="user-name">{{ username }}</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <div v-if="activeIndex === '0'">
          <MonitorDashboard />
        </div>
        <div v-else-if="activeIndex === '1'">
          <DataSourceList />
        </div>
        <div v-else-if="activeIndex === '2'">
          <TaskOrchestration :initial-task-id="targetTaskId" @clear-initial-task="targetTaskId = null" />
        </div>
        <div v-else-if="activeIndex === '3'">
          <ExecutionLogs @jump-to-task="handleJumpToTask" />
        </div>
        <div v-else-if="activeIndex === '4'">
          <SystemSettings />
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<style>
:root {
  --wechat-green: #07c160;
  --wechat-bg: #f7f7f7;
  --aside-bg: #fff;
}

body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  background-color: var(--wechat-bg);
}

.layout-container {
  height: 100vh;
  width: 100vw;
}

.aside {
  background-color: var(--aside-bg);
  border-right: 1px solid #e6e6e6;
  display: flex;
  flex-direction: column;
}

.logo-container {
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.logo {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.el-menu-vertical {
  border-right: none;
  flex: 1;
}

.el-menu-item.is-active {
  color: var(--wechat-green) !important;
  background-color: #f0f9eb !important;
}

.aside-footer {
  padding: 16px;
  border-top: 1px solid #f0f0f0;
}

.footer-text {
  font-size: 12px;
  color: #999;
  text-align: center;
}

.header {
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.header-left {
  font-size: 16px;
  font-weight: 500;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.user-name {
  font-size: 14px;
  color: #666;
}

.main {
  padding: 20px;
  overflow-y: auto;
  background-color: var(--wechat-bg);
  width: 100%;
}

.placeholder-card {
  background: #fff;
  padding: 40px;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05);
}
</style>
