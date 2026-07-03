import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Builds into frontend/dist with stable filenames; Maven copies dist/ into
// target/classes/static at process-resources so the bundle never lives in src/.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist',
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
