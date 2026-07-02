import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Builds straight into the Spring Boot static resources folder with stable
// filenames so the Thymeleaf shell template can reference them.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    rollupOptions: {
      output: {
        entryFileNames: 'assets/app.js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/app.[ext]',
      },
    },
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
