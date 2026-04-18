// frontend/vite.config.js
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',  
        changeOrigin: true,               
        secure: false,                    
        configure: (proxy, options) => {
          proxy.on('error', (err, req, res) => {
            console.log('Proxy error:', err);
          });
          proxy.on('proxyReq', (proxyReq, req, res) => {
            console.log('Proxying:', req.method, req.url);
          });
        }
      }
    }
  },
  
  build: {
    target: 'es2020',
    minify: false,
    sourcemap: false
  }
});