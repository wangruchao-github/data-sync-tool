# Data Sync Tool (数据集成工具)

这是一个轻量级、易用的数据集成与定时任务管理工具，旨在实现异构数据源之间的自动化数据流转。

## 🚀 快速开始

### 1. 环境准备
- Java 17+
- Node.js 18+
- MySQL 8.0+
- Docker (可选，用于快速启动数据库)

### 2. 启动数据库 (推荐使用 Docker)
```bash
docker-compose up -d
```

### 3. 启动后端 (Spring Boot)
```bash
cd backend
# 如果你有 Maven
mvn spring-boot:run
# 或者直接在 IDE 中运行 DataSyncApplication.java
```

### 4. 启动前端 (Vue 3)
```bash
cd frontend
npm install
npm run dev
```

## 🛠️ 技术栈
- **后端**: Spring Boot 3, Spring Data JPA, Quartz, MySQL
- **前端**: Vue 3, TypeScript, Vite, Vant UI (WeUI 风格)

## 📅 开发计划
- [x] MVP: 数据源管理 (MySQL)
- [ ] 任务编排画布 (可视化)
- [ ] 定时任务调度 (Quartz)
- [ ] 运行日志与监控
- [ ] 更多数据源支持 (Oracle, MongoDB, ES)

## 🎨 设计规范
本项目遵循微信小程序设计指南，强调 **友好礼貌、清晰明确、便捷优雅、统一稳定** 的用户体验。
