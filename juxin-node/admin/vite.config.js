import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5174,
    proxy: {
      '/api': {
        target: process.env.ORIN_API_PROXY || 'http://127.0.0.1:8090',
        changeOrigin: true,
      },
      '/ws': {
        target: process.env.ORIN_API_PROXY || 'http://127.0.0.1:8090',
        ws: true,
      },
    },
  },
})
