# 🤖 Modern-AI  - 综合性大模型应用实战

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-blue.svg)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-Latest-red.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

## 📖 项目简介
Modern-AI 是一个基于 Spring Boot 与 Spring AI 构建的综合性企业级 AI 应用。本项目致力于探索大语言模型（LLM）在实际业务场景中的落地，底层无缝兼容 **阿里云百炼（OpenAI协议）** 。

项目涵盖了通用多模态对话、RAG 个人知识库、Agent 智能客服（Function Calling）、以及纯 Prompt 驱动的文字游戏等丰富场景，全方位展示了大模型技术的业务赋能能力。

## ✨ 核心功能特性

本项目由 7 大核心模块组成：

- **🌐 多模型基建适配**：深度定制底层的流式输出与 Tool Calling 嵌套逻辑，提供针对不同业务场景的专属 `ChatClient` 配置。
- **📚 RAG 知识库问答**：支持用户上传 PDF 文档，系统自动完成文本切分与向量化存储，利用严格的 `QuestionAnswerAdvisor` 实现精准防幻觉问答。
- **🛠️ 智能客服 (Function Calling)**：AI 能够自动获取用户意图，调用底层业务 API（查课、查校区、提交预约单），并具备极强的防 Prompt Injection 安全机制。
- **👁️ 多模态聊天模块**：支持“图文并茂”的多模态输入，动态解析附件 MIME Type 并与文本融合响应。
- **🧠 会话记忆持久化**：解决无状态 AI 的记忆问题，利用反射与序列化技术，在服务重启时实现历史会话与文件映射的无损落盘与恢复。
- **🎮 情景模拟文字游戏**：完全通过高阶 Prompt 规则构建的“哄女友”模拟器，内置严格的数值加减系统与状态机。
- **💾 业务数据全链路**：基于 MyBatis-Plus 构建的底层数据支持，实现自然语言到结构化 SQL 数据的转化桥梁。

## 🛠️ 技术栈

- **核心框架**：Spring Boot 3.x
- **AI 框架**：Spring AI (兼容 OpenAI Protocol & Ollama)
- **持久层**：MyBatis-Plus, MySQL 8.x
- **向量数据库**：SimpleVectorStore (基于内存)
- **JSON 解析**：Jackson
- **构建工具**：Maven 3.x
- **开发语言**：Java 17 

## 🚀 快速开始

### 1. 环境准备
- 安装 JDK 17 及以上版本。
- 安装 MySQL 8.x 并启动服务。
- （可选）安装 Ollama 并拉取本地模型：`ollama run deepseek-r1:7b`。
- 获取 [阿里云百炼 API Key](https://bailian.console.aliyun.com/)。

### 2. 数据库初始化
在 MySQL 中创建数据库 `study`，并执行项目中对应的 SQL 脚本以初始化 `course`、`school`、`course_reservation` 表结构（请参阅 `src/main/resources/mapper`）。

### 3. 修改配置文件
找到 `src/main/resources/application.yaml`，修改以下配置：

```yaml
spring:
  ai:
    openai:
      api-key: 你的阿里云百炼API_KEY # 替换为真实的 Key 或配置环境变量
  datasource:
    url: jdbc:mysql://localhost:3306/study?serverTimezone=Asia/Shanghai&useSSL=false&characterEncoding=utf-8
    username: root
    password: 你的数据库密码
