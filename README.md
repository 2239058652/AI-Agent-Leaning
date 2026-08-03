# Agent Learning

从零学 Agent 开发（Spring Boot + React + MCP）。  
**目的：教会人，不是堆功能。**

---

## 读什么（先看这里）

| 谁 | 读什么 |
|---|---|
| **你** | `HANDOFF.md`（到哪了） |
| **大模型** | `CLAUDE.md` → 再读 `HANDOFF.md` |
| **大纲** | `agent-dev-learning-plan-v3.md`（阶段目标，不是每日进度） |
| **历史** | `docs/archive/`（旁观者说、旧审查等，**勿当现状**） |

新开对话可复制：

```
先读 HANDOFF.md，按恢复点续接，不要直接写大段代码。
```

---

## 项目结构

```
Agent-Learning/
├── HANDOFF.md                 # 进度权威
├── CLAUDE.md                  # AI 开场
├── agent-dev-learning-plan-v3.md
├── docs/archive/              # 历史文档
├── ai-assistant/              # 后端 3180
├── mcp-server/                # MCP 3190
├── frontend/                  # 前端 3100
└── practice/                  # 可选巩固练习
```

---

## 快速开始

### 1. MCP Server（3190）

```bash
cd mcp-server
mvn spring-boot:run
```

### 2. AI Assistant（3180）

```bash
cd ai-assistant
# 配置 application.yaml 中的 API Key / 数据库
mvn spring-boot:run
```

### 3. 前端（3100）

```bash
cd frontend
pnpm install
pnpm dev
```

打开 http://localhost:3100

---

## 技术栈

- 后端：Spring Boot 3.5 / Java 17 / Spring AI / MyBatis / MySQL  
- MCP：Java SDK（Streamable HTTP）  
- 前端：React 19 / TypeScript / Vite / pnpm  
- LLM：LongCat（OpenAI 兼容）

---

## 学习进度（摘要）

详情以 **`HANDOFF.md`** 为准。

| 阶段 | 状态 |
|---|---|
| 0–5 | 功能核心 ✅ |
| 6 核心 | Spring AI + 记忆落库 + 清空会话 ✅ |
| L3 巩固 | 已完成 ✅ |
| **阶段7** | **准备开始：鉴权与权限边界** |
| 8–10 | 待做 |

---

## 当前能力（摘要）

- 聊天：非流式 / SSE / 工具流  
- 本地+业务+MCP 工具；敏感取消订单需确认  
- 对话按 `conversationId` 落 MySQL；支持清空会话  
