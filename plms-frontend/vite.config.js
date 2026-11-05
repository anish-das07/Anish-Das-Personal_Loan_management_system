import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// npm run dev   -> http://localhost:5173, /api calls are proxied to the Spring Boot backend on :8080
// npm run build -> writes the production UI into the backend project, so Spring Boot serves UI + API
//                  together on http://localhost:8080 (expects ../plms-backend next to this folder)
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": { target: process.env.VITE_PROXY_TARGET || "http://localhost:8080", changeOrigin: true }
    }
  },
  build: {
    outDir: "../plms-backend/src/main/resources/static",
    emptyOutDir: true
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.js"],
    css: false
  }
});
