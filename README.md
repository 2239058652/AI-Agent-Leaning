# Agent Learning

Agent 开发学习项目，从零搭建 AI 助手，逐步扩展能力。

## 项目结构

```
Agent-Learning/
├── pom.xml                    # 父 Maven 项目（管理子模块）
├── ai-assistant/              # 贯穿项目 — AI 助手
│   ├── src/                   # Spring Boot 后端
│   └── frontend/              # React 前端
├── agent-dev-learning-plan-v2.md  # 19周学习计划
└── .claude/
    └── evaluation.md          # 计划评估报告
```

## 快速开始

```bash
# 后端
cd ai-assistant
mvn spring-boot:run

# 前端
cd frontend
pnpm install
pnpm dev
```

打开 http://localhost:3100 使用。

## 技术栈

- Spring Boot 3.5 / Java 17
- React 19 / TypeScript / Vite
- LongCat API（OpenAI 兼容格式）
