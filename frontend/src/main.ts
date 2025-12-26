import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import './style.css'

/* Vue Flow Styles */
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/minimap/dist/style.css'
import '@vue-flow/controls/dist/style.css'

import App from './App.vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

// Global configuration for API base path
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

// Axios interceptors
axios.interceptors.request.use(config => {
  // Handle custom base URL for relative paths starting with /api
  if (API_BASE_URL && config.url && config.url.startsWith('/api')) {
    config.url = API_BASE_URL + config.url;
  }

  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
}, error => {
  return Promise.reject(error);
});

axios.interceptors.response.use(response => {
  return response;
}, error => {
  if (error.response && error.response.status === 401) {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    // If not on login page, we can't easily redirect without router, 
    // but App.vue will handle the switch based on token absence
    window.location.reload();
  } else if (error.response && error.response.status === 403) {
    ElMessage.error('权限不足或登录已过期');
  }
  return Promise.reject(error);
});

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(ElementPlus)
app.mount('#app')
