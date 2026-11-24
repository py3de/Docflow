import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    open: true,
    // With VITE_API_URL=/api/v1, API calls go to the Spring Boot backend on :8080 (no CORS needed).
    proxy: { '/api': 'http://localhost:8080' },
  },
})
