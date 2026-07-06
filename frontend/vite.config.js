import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Builds into frontend/dist with content-hashed filenames + a manifest. The Spring
// HomeController reads the manifest to point the Thymeleaf shell at the current
// hashed files, so a new build = a new URL (never stale), and hashed assets can be
// cached long-term. Maven copies dist/ into target/classes/static at build time.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    manifest: true, // writes dist/.vite/manifest.json
    rollupOptions: {
      output: {
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash].[ext]',
      },
    },
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
