import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // 如果后续不想在前端代码里写死 http://localhost:8080，可以在这里配置代理解决跨域
  },
});
