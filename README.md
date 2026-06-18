<p align="center">
  <h1 align="center">CodeRAG</h1>
  <p align="center"><b>多平台代码智能学习平台</b> / <i>Multi-Platform Code Intelligence Learning Platform</i></p>
  <p align="center">
    <img src="https://img.shields.io/badge/Java-17-orange?logo=openjdk" alt="Java 17">
    <img src="https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen?logo=springboot" alt="Spring Boot 3.2.5">
    <img src="https://img.shields.io/badge/Vue-3.4-blue?logo=vuedotjs" alt="Vue 3.4">
    <img src="https://img.shields.io/badge/Vite-5.2-purple?logo=vite" alt="Vite 5.2">
    <img src="https://img.shields.io/badge/TypeScript-5.4-blue?logo=typescript" alt="TypeScript 5.4">
    <img src="https://img.shields.io/badge/PostgreSQL-pgvector-336791?logo=postgresql" alt="PostgreSQL + pgvector">
    <img src="https://img.shields.io/badge/AI-火山方舟%20|%20百炼-red" alt="AI">
    <img src="https://img.shields.io/badge/License-MIT-yellow" alt="MIT License">
  </p>
</p>

---

## 📖 目录 / Table of Contents

- [项目简介 / Overview](#-项目简介--overview)
- [核心功能 / Core Features](#-核心功能--core-features)
- [技术架构 / Architecture](#-技术架构--architecture)
- [技术栈 / Tech Stack](#-技术栈--tech-stack)
- [项目结构 / Project Structure](#-项目结构--project-structure)
- [快速开始 / Quick Start](#-快速开始--quick-start)
- [API 概览 / API Overview](#-api-概览--api-overview)
- [许可证 / License](#-许可证--license)

---

## 🌟 项目简介 / Overview

**CodeRAG** 是一款面向开发者与计算机专业学生的**开源代码智能学习平台**。它基于 **RAG（检索增强生成）** 技术，将 GitHub / Gitee 仓库代码进行向量化索引，结合大语言模型（LLM）提供智能代码问答、项目架构解析、版本差异对比、代码翻译及 AI 刷题等全方位学习体验。

**CodeRAG** is an **open-source code intelligence learning platform** designed for developers and CS students. Powered by **RAG (Retrieval-Augmented Generation)** technology, it vectorizes GitHub/Gitee repository code and integrates with LLMs to deliver intelligent code Q&A, architecture analysis, version diff interpretation, code translation, and AI-powered quiz generation — all in one place.

> 🎯 **轻量 · 高效 · 免费** — 无需复杂配置，导入仓库即可开始 AI 驱动的代码学习之旅。
>
> 🎯 **Lightweight · Efficient · Free** — No complex setup required. Import a repo and start your AI-powered code learning journey.

---

## 🚀 核心功能 / Core Features

| 功能 / Feature | 说明 / Description |
|:--|:--|
| 🔗 **双平台仓库导入** | 支持 GitHub / Gitee 仓库一键导入，适配器模式统一接口 |
| 🤖 **RAG 智能问答** | 基于整仓代码上下文的 AI 问答，答案附带文件溯源 |
| 🏗️ **架构自动解析** | AI 自动生成项目架构说明、模块关系与依赖图 |
| 🔍 **版本智能对比** | 分支 / Commit 差异的 AI 解读，快速理解代码变更 |
| 📝 **代码难点刷题** | 自动识别代码难点，生成练习题，AI 批改并讲解 |
| 🌐 **代码翻译** | 支持代码片段 / 文件跨语言翻译 |
| 🕸️ **代码图谱** | 构建代码调用关系图，可视化项目结构 |
| 👤 **个人管理中心** | 仓库管理、配额查看、角色权限控制 |
| 🛡️ **全局限流 & 配额** | 基于角色的配额系统，支持管理员自定义用户配额 |
| 🔐 **JWT 认证鉴权** | 无状态安全认证，BCrypt 密码加密 |

---

## 🏛️ 技术架构 / Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                         Frontend (Vue 3 + Vite)                   │
│  ┌─────────┐  ┌──────────┐  ┌──────────┐  ┌───────────────────┐ │
│  │ Vue 3   │  │ Pinia    │  │ Vue      │  │ markdown-it       │ │
│  │ (SFC)   │  │ (State)  │  │ Router 4 │  │ + highlight.js    │ │
│  └─────────┘  └──────────┘  └──────────┘  └───────────────────┘ │
└──────────────────────────┬───────────────────────────────────────┘
                           │ HTTP (Axios + JWT)
┌──────────────────────────▼───────────────────────────────────────┐
│                     Backend (Spring Boot 3.2.5)                   │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────────────┐ │
│  │ Spring      │  │ Spring       │  │ Adapter Pattern          │ │
│  │ Security    │  │ Data JPA     │  │ (GitHub / Gitee)         │ │
│  │ + JWT       │  │              │  │                          │ │
│  └─────────────┘  └──────────────┘  └──────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │                    RAG Pipeline                               │ │
│  │  Code Chunking → Vector Embedding → pgvector Store → Search  │ │
│  └──────────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │                    AI Services                                │ │
│  │  火山方舟 (Chat) · 百炼 DashScope (Embedding)                │ │
│  └──────────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │  Quota System · Async Task Pipeline · Multi-level Cache       │ │
│  └──────────────────────────────────────────────────────────────┘ │
└──────────────────────────┬───────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────────┐
│                    Data & Deployment                              │
│  Neon PostgreSQL + pgvector · Docker · 阿里云 FC                  │
└──────────────────────────────────────────────────────────────────┘
```

### RAG 工作流程 / RAG Workflow

```
用户提问 → 向量检索(Top-5 代码片段) → 构建 Prompt(代码上下文+溯源)
         → LLM 生成回答 → 缓存结果 → 返回(含文件引用)
```

---

## 🛠️ 技术栈 / Tech Stack

### 后端 / Backend

| 技术 / Technology | 版本 / Version | 用途 / Purpose |
|:--|:--|:--|
| **Java** | 17 | 编程语言 |
| **Spring Boot** | 3.2.5 | 应用框架 |
| **Spring Security** | 6.x | 认证与授权 |
| **Spring Data JPA** | 3.x | ORM 持久层 |
| **PostgreSQL** | 16+ | 关系型数据库 |
| **pgvector** | 0.1.6 | 向量存储与相似度检索 |
| **H2 Database** | — | 本地开发数据库 |
| **JJWT** | 0.12.5 | JWT 令牌管理 |
| **OkHttp** | 4.12.0 | HTTP 客户端 |
| **Lombok** | — | 代码简化 |
| **Maven** | 3.x | 构建管理 |

### 前端 / Frontend

| 技术 / Technology | 版本 / Version | 用途 / Purpose |
|:--|:--|:--|
| **Vue** | 3.4.21 | 前端框架 |
| **Vite** | 5.2.8 | 构建工具 |
| **TypeScript** | 5.4.5 | 类型安全 |
| **Vue Router** | 4.3.0 | 路由管理 |
| **Pinia** | 2.1.7 | 状态管理 |
| **Axios** | 1.6.8 | HTTP 客户端 |
| **markdown-it** | 14.1.0 | Markdown 渲染 |
| **highlight.js** | 11.9.0 | 代码语法高亮 |

### AI 服务 / AI Services

| 服务 / Service | 模型 / Model | 用途 / Purpose |
|:--|:--|:--|
| **火山方舟 (Ark)** | doubao-seed-2-0-mini-260428 | 对话生成 |
| **百炼 DashScope** | text-embedding-v4 (1024维) | 代码向量化 |

### 部署 / Deployment

| 技术 / Technology | 用途 / Purpose |
|:--|:--|
| **Docker** | 容器化部署 |
| **阿里云函数计算 FC** | 后端 Serverless 部署 |
| **Neon PostgreSQL** | 云数据库（含 pgvector） |

---

## 📁 项目结构 / Project Structure

```
CodeRAG/
├── README.md                         # 项目文档
├── Dockerfile                        # Docker 构建文件
├── .gitignore                        # Git 忽略规则
│
├── backend/                          # 后端 Spring Boot 项目
│   ├── pom.xml                       # Maven 依赖配置
│   └── src/main/
│       ├── java/com/coderag/
│       │   ├── CodeRagApplication.java       # 应用启动类
│       │   ├── adapter/                      # 平台适配器 (GitHub/Gitee)
│       │   ├── config/                       # 配置类 (Security/CORS/AI/Quota)
│       │   ├── controller/                   # REST 控制器 (10个)
│       │   ├── service/                      # 业务服务层 (16个)
│       │   ├── entity/                       # JPA 实体类 (13个)
│       │   ├── repository/                   # 数据访问层
│       │   ├── rag/                          # RAG 核心 (分块/向量化/AI调用)
│       │   ├── common/                       # 公共类 (缓存/异常/常量)
│       │   └── dto/                          # 数据传输对象
│       └── resources/
│           ├── application.yml               # 生产配置 (PostgreSQL)
│           ├── application-dev.yml           # 开发配置 (H2)
│           └── db/init.sql                   # 数据库初始化脚本
│
└── frontend/                         # 前端 Vue 3 项目
    ├── package.json                  # NPM 依赖配置
    ├── vite.config.ts                # Vite 构建配置
    ├── tsconfig.json                 # TypeScript 配置
    ├── index.html                    # HTML 入口
    └── src/
        ├── main.ts                   # 应用入口
        ├── App.vue                   # 根组件
        ├── router/index.ts           # 路由配置 (14个路由)
        ├── store/auth.ts             # Pinia 认证状态
        ├── api/index.ts              # API 封装 (Axios + JWT)
        ├── assets/main.css           # 全局样式 (极简设计)
        ├── components/               # 公共组件
        └── views/                    # 页面组件 (15个)
            ├── Home.vue              # 首页
            ├── Login.vue             # 登录
            ├── Register.vue          # 注册
            ├── RepoImport.vue        # 仓库导入
            ├── RepoPreview.vue       # 仓库预览
            ├── Chat.vue              # AI 问答
            ├── Architecture.vue      # 架构分析
            ├── VersionCompare.vue    # 版本对比
            ├── Quiz.vue              # 代码刷题
            ├── Translate.vue         # 代码翻译
            ├── Graph.vue             # 代码图谱
            ├── Profile.vue           # 个人中心
            └── Admin.vue             # 管理后台
```

---

## ⚡ 快速开始 / Quick Start

### 前置要求 / Prerequisites

- **JDK 17+**
- **Node.js 20+** & npm
- **Maven 3.8+**
- **PostgreSQL 16+** (需安装 [pgvector](https://github.com/pgvector/pgvector) 扩展)

### 本地开发 / Local Development

```bash
# 1. 克隆项目
git clone https://github.com/zzuli-ljz/CodeRAG.git
cd CodeRAG

# 2. 启动后端 (使用 H2 内存数据库，无需 PostgreSQL)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. 启动前端 (新终端)
cd frontend
npm install
npm run dev

# 4. 访问
# 前端: http://localhost:5173
# 后端: http://localhost:8080
# H2 控制台: http://localhost:8080/h2-console
```

### 生产部署 / Production Deployment

```bash
# 1. 构建前端
cd frontend && npm install && npm run build

# 2. 复制前端产物到后端
xcopy /E /Y frontend\dist\* backend\src\main\resources\static\

# 3. 打包后端 (含前端静态资源)
cd backend && mvn clean package -DskipTests

# 4. 运行
java -jar target/coderag-backend-1.0.0.jar
```

### Docker 部署 / Docker Deployment

```bash
# 构建镜像
docker build -t coderag .

# 运行容器
docker run -d -p 8080:8080 \
  -e DB_HOST=your_db_host \
  -e DB_USERNAME=your_db_user \
  -e DB_PASSWORD=your_db_password \
  -e DB_NAME=your_db_name \
  -e JWT_SECRET=your_jwt_secret \
  -e ARK_API_KEY=your_ark_api_key \
  -e DASHSCOPE_API_KEY=your_dashscope_api_key \
  coderag
```

### 环境变量 / Environment Variables

| 变量 / Variable | 必填 / Required | 说明 / Description |
|:--|:--|:--|
| `DB_HOST` | ✅ | PostgreSQL 主机地址 |
| `DB_USERNAME` | ✅ | 数据库用户名 |
| `DB_PASSWORD` | ✅ | 数据库密码 |
| `DB_NAME` | ✅ | 数据库名称 |
| `JWT_SECRET` | ✅ | JWT 签名密钥 |
| `ARK_API_KEY` | ✅ | 火山方舟 API Key |
| `DASHSCOPE_API_KEY` | ✅ | 百炼 DashScope API Key |
| `GITHUB_TOKEN` | ❌ | GitHub Personal Access Token |
| `GITEE_TOKEN` | ❌ | Gitee Personal Access Token |

---

## 📡 API 概览 / API Overview

| 模块 / Module | 端点 / Endpoint | 方法 / Method | 说明 / Description |
|:--|:--|:--|:--|
| **认证** | `/api/auth/login` | POST | 用户登录 |
| | `/api/auth/register` | POST | 用户注册 |
| **仓库** | `/api/repos/import` | POST | 导入仓库 |
| | `/api/repos` | GET | 仓库列表 |
| | `/api/repos/{id}` | GET | 仓库详情 |
| | `/api/repos/{id}` | DELETE | 删除仓库 |
| | `/api/repos/{id}/files` | GET | 仓库文件列表 |
| **问答** | `/api/chat` | POST | AI 代码问答 |
| | `/api/chat/history/{repoId}` | GET | 问答历史 |
| **架构** | `/api/architecture/analyze/{repoId}` | POST | 架构分析 |
| | `/api/architecture/latest/{repoId}` | GET | 最新架构 |
| **版本** | `/api/version/compare` | POST | 版本对比 |
| **刷题** | `/api/quiz/generate/{repoId}` | POST | 生成题目 |
| | `/api/quiz/answer` | POST | 提交答案 |
| **翻译** | `/api/translate/file` | POST | 翻译文件 |
| **图谱** | `/api/graph/build/{repoId}` | POST | 构建代码图谱 |
| **用户** | `/api/user/profile` | GET | 个人中心 |
| **管理** | `/api/admin/users` | GET | 用户列表 |
| | `/api/admin/users/{id}/role` | PUT | 修改角色 |
| | `/api/admin/users/{id}/quota` | PUT | 设置配额 |

---

## 👤 作者 / Author

**LI JIAZHE** — [@zzuli-ljz](https://github.com/zzuli-ljz)

- 独立设计与开发 / Independently designed and developed
- 全栈项目 / Full-stack project

---

## 📄 许可证 / License

本项目基于 **MIT License** 开源，详见 [LICENSE](LICENSE) 文件。

This project is open-sourced under the **MIT License**. See the [LICENSE](LICENSE) file for details.

---

<p align="center">
  <sub>Made with ❤️ by LI JIAZHE | 2025</sub>
</p>
