import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3100,
    proxy: {
      '/auth': {
        target: 'http://localhost:3170',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/auth/, ''),
      },
      '/api': {
        target: 'http://localhost:3180',
        changeOrigin: true,
      },
    },
  },
})
