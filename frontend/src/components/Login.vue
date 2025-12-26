<script setup lang="ts">
import { ref } from 'vue';
import axios from 'axios';
import { ElMessage } from 'element-plus';
import { User, Lock } from '@element-plus/icons-vue';

interface SystemConfigs {
  'sys.name': string;
  'sys.logo': string;
  'sys.footer': string;
}

const props = defineProps<{
  systemConfigs: SystemConfigs;
}>();

const emit = defineEmits(['login-success']);

const loading = ref(false);
const loginForm = ref({
  username: '',
  password: ''
});

const handleLogin = async () => {
  if (!loginForm.value.username || !loginForm.value.password) {
    ElMessage.warning('请输入用户名和密码');
    return;
  }
  
  loading.value = true;
  try {
    const res = await axios.post('/api/auth/login', loginForm.value);
    const { token, username } = res.data;
    localStorage.setItem('token', token);
    localStorage.setItem('username', username);
    ElMessage.success('登录成功');
    emit('login-success');
  } catch (e: any) {
    console.error('Login failed:', e);
    ElMessage.error(e.response?.data?.message || '登录失败，请检查用户名和密码');
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <img :src="systemConfigs['sys.logo'] || '/vite.svg'" alt="logo" class="login-logo" />
        <h2 class="login-title">{{ systemConfigs['sys.name'] }}</h2>
      </div>
      <el-form :model="loginForm" class="login-form">
        <el-form-item>
          <el-input 
            v-model="loginForm.username" 
            placeholder="用户名" 
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item>
          <el-input 
            v-model="loginForm.password" 
            type="password" 
            placeholder="密码" 
            :prefix-icon="Lock"
            show-password
            size="large"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button 
          type="primary" 
          class="login-button" 
          :loading="loading" 
          size="large"
          @click="handleLogin"
        >
          登 录
        </el-button>
      </el-form>
      <div class="login-footer">
        默认账号: admin / admin123
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  height: 100vh;
  width: 100vw;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #f0f2f5;
  background-image: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
}

.login-box {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.login-header {
  text-align: center;
  margin-bottom: 40px;
}

.login-logo {
  width: 64px;
  height: 64px;
  margin-bottom: 16px;
}

.login-title {
  margin: 0;
  color: #333;
  font-size: 24px;
}

.login-form {
  margin-bottom: 24px;
}

.login-button {
  width: 100%;
  margin-top: 10px;
  background-color: var(--wechat-green);
  border-color: var(--wechat-green);
}

.login-button:hover {
  opacity: 0.9;
}

.login-footer {
  text-align: center;
  color: #999;
  font-size: 14px;
}
</style>
