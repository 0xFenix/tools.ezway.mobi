import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Build output goes straight into the Maven module's static resources
// (web/target/classes/web) so merger-web.jar stays a single fat jar.
export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: '../target/classes/web',
    emptyOutDir: true,
    target: 'es2019',
    assetsInlineLimit: 4096
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
})
