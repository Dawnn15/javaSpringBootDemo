import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],

  server: {
    port: 5173,
    open: true,

    // 代理：解决跨域的第二种方案（后端也配了 CORS，双保险）
    //
    // 前端代码里写 fetch('/api/todos')，请求发到 5173 端口，
    // Vite 发现路径以 /api 开头，就转发给 http://localhost:8080。
    // 对浏览器来说，请求始终在 5173 同一个源内，压根不存在跨域问题。
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
