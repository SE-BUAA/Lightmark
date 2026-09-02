const { defineConfig } = require("@vue/cli-service");
module.exports = defineConfig({
  transpileDependencies: true,
  lintOnSave: false,
  devServer: {
    port: 8081,
    proxy: {
      // 内容域在微服务阶段独立运行于 8084，开发环境按路径转发。
      "/api/chat": {
        target: "http://localhost:8084",
        changeOrigin: true,
      },
      "/api/itinerary": {
        target: "http://localhost:8084",
        changeOrigin: true,
      },
      "/api/posts": {
        target: "http://localhost:8084",
        changeOrigin: true,
      },
      "/api/questions": {
        target: "http://localhost:8084",
        changeOrigin: true,
      },
      "/api/community": {
        target: "http://localhost:8084",
        changeOrigin: true,
      },
      "/api/ai": {
        target: "http://localhost:8084",
        changeOrigin: true,
      },
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
