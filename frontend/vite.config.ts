// frontend/vite.config.ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq, req) => {
            console.log('→ Proxying:', req.method, req.url)
          })
          proxy.on('proxyRes', (proxyRes, req) => {
            console.log('← Response:', proxyRes.statusCode)
          })
          proxy.on('error', (err) => {
            console.error('✗ Proxy error:', err.message)
          })
        }
      }
    }
  }
})