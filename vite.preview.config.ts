// Builds a single self-contained HTML file (used for the shareable demo preview).
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { viteSingleFile } from 'vite-plugin-singlefile';

export default defineConfig({
  plugins: [react(), viteSingleFile()],
  define: { 'import.meta.env.VITE_MEMORY_ROUTER': JSON.stringify('1') },
  build: { outDir: 'dist-preview' },
});
