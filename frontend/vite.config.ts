import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd());
  const baseUrl = env.VITE_API_BASE_URL || '';

  return {
    plugins: [vue()],
    base: baseUrl,
    server: {
      proxy: baseUrl ? {
        [`${baseUrl}/api`]: {
          target: 'http://112.124.3.174:8088/ilido-data-sync-tool',
          changeOrigin: true,
          rewrite: (path) => path.replace(new RegExp(`^${baseUrl}`), '')
        }
      } : {
        '/api': {
          target: 'http://112.124.3.174:8088/ilido-data-sync-tool',
          changeOrigin: true,
        }
      }
    }
  }
})
