# Agent-Learning 交接文档（权威进度）

> **更新日期**：2026-08-10  
> **用途**：新开对话 / 换工具 / 换模型时 **先读本文件**。  
> **项目唯一目的**：教会用户开发 Agent，不是代写完整产品。  
> **冲突裁决**：`HANDOFF.md` + 代码 + `git log` > 其它 md（含审查报告、旁观者说、旧 README）。

---

## ★ 当前恢复点（2026-08-10）

| 项 | 状态 |
|---|---|
| **阶段** | 阶段6 与 L3 巩固已完成；阶段7.1–7.3 第一轮已完成 |
| **git** | 核心已提交 `d75a010`（Spring AI + 记忆落库 + 工具桥接 + 清空会话） |
| **刚完成** | Spring Security Basic、USER/ADMIN、订单 `user_id` 隔离、确认身份绑定；测试通过 |
| **理解状态** | L3 M1–M5 及总调用图口述已过关 |
| **当前主线** | **阶段7：继续完善权限边界与鉴权测试** |
| **阶段6 还剩（可选/非阻塞进7）** | 书面框架对比；官方 JDBC starter 对比（动 pom 先问） |

开场 AI 应：读本文件 → 复述恢复点 → **问用户**从阶段7哪项开始，不擅自开写。

### 工作区注意（可能未提交）

- 修改中：阶段7安全配置、订单权限与测试
- 以 `git status` 为准

---

## 协作规矩

| 规则 | 说明 |
|---|---|
| 教学优先 | 先讲为什么 |
| 用户执笔 | 默认用户写后端；明确「你直接写」才代写；前端可代写 |
| 新依赖 / 破坏性操作 | 先问 |
| 不主动跑构建/测试 | 除非用户要求 |
| 前端包管理 | pnpm |
| 计划文件 | `.claude/plans/`（项目内） |

---

## 端口与模块

| 服务 | 端口 |
|---|---|
| frontend | 3100 |
| ai-assistant | 3180 |
| mcp-server | 3190 |

```
Agent-Learning/
├── HANDOFF.md                 ← 本文件（进度权威）
├── CLAUDE.md                  ← 给 AI 的开场
├── agent-dev-learning-plan-v3.md
├── docs/archive/              ← 历史文档（勿当进度）
├── ai-assistant/ / mcp-server/ / frontend/
└── practice/                  ← 可选巩固练习
```

---

## 学习进度总表

| 阶段 | 内容 | 功能状态 | 理解状态（用户自评语境） |
|---|---|---|---|
| 0–5 | 工程化、LLM、Tool、安全、前端闭环、MCP、订单业务 | ✅ 能跑 | 部分模糊（尤其 MCP） |
| **6 核心** | Spring AI + 对话记忆落库 + 清空会话 | ✅ 已验证/已提交 | **半懂，需 L3 巩固** |
| 6 收尾 | 书面对比 / JDBC starter | 可选未做 | — |
| **7.1–7.3** | 身份、角色、工具权限、订单数据范围 | ✅ 第一轮已验证 | 继续补边界测试 |
| 7.4–10 | MCP 鉴权 / 可观测 / 限流 / 演示 | 未开始 | — |

### 阶段6「还剩一半」指什么（避免误解）

| 已完成（核心） | 未做（收尾/可选） |
|---|---|
| Spring AI 迁移、窗口记忆、MySQL 落库、清空会话、工具桥+敏感确认链路 | 书面框架对比；官方 `jdbc` starter 对比；严格步骤级 Checkpoint |

**不是**「核心功能只做了一半」。

---

## 0–6 图谱与衔接（简）

| 阶段 | 重点 |
|---|---|
| 0 | 业务载体：只读 / 聚合 / 敏感写（订单） |
| 1 | 分层、异常、校验 |
| 2 | HTTP + SSE 流式 |
| 3 | Tool + Agent Loop |
| 3.5 | 白名单、校验、敏感标记 |
| 4 | Timeline、中断、确认弹窗 |
| 5 | MCP Server + Client |
| 6 | 框架 + 记忆落库 |

衔接：`2→3` 工具环 → `3.5→4` 前端确认 → `5` 远程工具 → `6` 框架+记忆。

```
前端 → Controller → ChatService → ChatClient
                      ├─ advisors(记忆) → ChatMemory → MybatisRepo → DB
                      └─ toolCallbacks → ToolService → 本地/MCP
```

---

## API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/chat` | 非流式 |
| POST | `/api/chat/stream` | SSE |
| POST | `/api/chat/tool-stream` | SSE + 工具 |
| POST | `/api/chat/execute-confirmed` | 敏感确认后执行 |
| DELETE | `/api/conversations/{id}` | 清空会话记忆 |

聊天 body：`{ message, conversationId }`

---

## 关键源码

- `ChatService` / `ChatController`
- `ChatMemoryConfig` / `MybatisChatMemoryRepository` / `ChatMemoryMapper.xml`
- `ToolCallbackProvider` / `ToolService` / `ToolRegistry`
- `McpClientService` + `mcp-server`
- `frontend/src/App.tsx`

---

## 文档怎么读（人 vs AI）

| 谁 | 读什么 |
|---|---|
| **你** | `HANDOFF.md` |
| **AI** | `CLAUDE.md` → `HANDOFF.md` |
| **大纲** | `agent-dev-learning-plan-v3.md` |
| **历史** | 全部在 `docs/archive/`，**勿当现状** |

---

## 技术债（简）

- 确认文案硬编码在 `ToolCallbackProvider`
- MCP Resources/Prompts 未接入
- 严格 Agent Checkpoint 未做
- 部分测试可能与现构造不一致
- 阶段7尚未完成：MCP Server 鉴权、会话与 `userId` 绑定、完整权限测试待做

---

## 下一步

1. 完善阶段7：会话绑定 `userId`，避免客户端伪造 `conversationId` 访问他人记忆  
2. 补齐 `401/403`、跨用户确认和订单权限测试  
3. 再做 MCP Server 鉴权（7.4）  

**一句话**：功能 0–6 核心与确认闭环已通，L3 已完成；阶段7.1–7.3 第一轮已验证，下一步完善会话归属并继续鉴权。
