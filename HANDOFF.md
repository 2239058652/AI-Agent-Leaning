# Agent-Learning 交接文档（权威进度）

> **更新日期**：2026-08-21  
> **用途**：新开对话 / 换工具 / 换模型时 **先读本文件**。  
> **项目唯一目的**：教会用户开发 Agent，不是代写完整产品。  
> **冲突裁决**：`HANDOFF.md` + 代码 + `git log` > 其它 md（含审查报告、旁观者说、旧 README）。

---

## ★ 当前恢复点（2026-08-21）

| 项 | 状态 |
|---|---|
| **阶段** | 阶段6 与 L3 巩固已完成；阶段7.1–7.4 服务级鉴权闭环已完成 |
| **git** | 核心已提交 `d75a010`（Spring AI + 记忆落库 + 工具桥接 + 清空会话） |
| **刚完成** | 会话绑定认证用户；新增 Authorization Server；MCP JWT 验签、scope 授权及客户端自动取/刷新 Token 已打通 |
| **理解状态** | L3 M1–M5 及总调用图口述已过关 |
| **当前主线** | **阶段7：把 MCP 服务身份继续扩展为最终用户身份与角色** |
| **阶段6 还剩（可选/非阻塞进7）** | 书面框架对比；官方 JDBC starter 对比（动 pom 先问） |

开场 AI 应：读本文件 → 复述恢复点 → **问用户**从阶段7哪项开始，不擅自开写。

### 工作区注意（可能未提交）

- 修改中：阶段7会话隔离、Authorization Server、MCP JWT 鉴权与 OAuth 客户端
- 以 `git status` 为准
- 用户明确要求暂缓任务时，保留当前恢复点，不主动继续写代码、跑构建或跑测试。
- 当前已知无关改动，不要误提交：
  - `ai-assistant/.mvn/wrapper/maven-wrapper.properties`
  - `ai-assistant/mvnw.cmd`
  - 这是 Maven Wrapper 自动产生的改动。

### 本次暂停点（2026-08-21）

本轮已完成：

- `ChatController` 将 `authentication.getName()` 传给普通聊天、普通流式和删除会话 Service。
- `ChatService` 用 `userId + ":" + conversationId` 作为内部记忆键；普通聊天、工具流、敏感确认写回和删除使用同一绑定键。旧的无前缀记忆数据未迁移。
- `ai-assistant` 与 `mcp-server` 的 MCP Java SDK 已统一升级到 `1.1.3`，并完成 1.1.3 API 迁移。
- 新增 `authorization-server` Maven 模块，端口 `3170`；使用 Spring Authorization Server、RSA JWK 和 `client_credentials` 签发 JWT。
- `mcp-server` 作为 Resource Server，使用 issuer/JWK 验签；`/mcp` 要求 `SCOPE_mcp.weather`。
- IDEA HTTP Client 已验证：无 Token 为 `401`、有效但无 scope 为 `403`、带 `mcp.weather` scope 可完成 MCP initialize。
- `McpClientService` 会申请、缓存并携带服务 Token；根据 `expires_in` 提前 30 秒刷新。首次连接和工具发现已验证成功。

下一次恢复时，严格从这里开始：

1. 先解释当前 JWT 代表 `ai-assistant` 服务身份（`sub=ai-assistant`），不是 `user1/user2` 最终用户。
2. 设计最终用户身份传递方案，再决定是把现有 Basic 登录迁移到 Authorization Code/OIDC，还是先做受信任的 Token Exchange/代理签发桥接；这是实现方向选择，先和用户讲清权衡。
3. 用户确认方向后，继续手把手实现用户 claims、角色/scope 映射及 MCP 工具级授权。
4. 补齐跨用户记忆读取/删除、跨用户确认和 MCP `401/403` 的自动化边界测试；未经用户明确要求，不主动运行构建或测试。
5. Token 自动刷新逻辑已接入但未等待 5 分钟做实际过期刷新验证；并发过期时可能重复申请 Token，当前不影响正确性。

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
| authorization-server | 3170 |
| ai-assistant | 3180 |
| mcp-server | 3190 |

```
Agent-Learning/
├── HANDOFF.md                 ← 本文件（进度权威）
├── CLAUDE.md                  ← 给 AI 的开场
├── agent-dev-learning-plan-v3.md
├── docs/archive/              ← 历史文档（勿当进度）
├── authorization-server/ / ai-assistant/ / mcp-server/ / frontend/
└── practice/                  ← 可选巩固练习
```

---

## 学习进度总表

| 阶段 | 内容 | 功能状态 | 理解状态（用户自评语境） |
|---|---|---|---|
| 0–5 | 工程化、LLM、Tool、安全、前端闭环、MCP、订单业务 | ✅ 能跑 | 部分模糊（尤其 MCP） |
| **6 核心** | Spring AI + 对话记忆落库 + 清空会话 | ✅ 已验证/已提交 | **半懂，需 L3 巩固** |
| 6 收尾 | 书面对比 / JDBC starter | 可选未做 | — |
| **7.1–7.3** | 身份、角色、工具权限、订单与会话数据范围 | ✅ 第一轮已验证 | 继续补边界测试 |
| **7.4 服务级** | Authorization Server、MCP JWT 验签、scope、客户端 Token | ✅ 手工闭环已验证 | 已理解服务身份与用户身份的区别 |
| 7.4 用户级 | 最终用户身份/角色传递到 MCP | 未开始 | 下一主线 |
| 8–10 | 可观测 / 限流 / 演示 | 未开始 | — |

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
- `authorization-server/AuthorizationServerConfig`
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
- 阶段7尚未完成：MCP 最终用户身份与角色传递、完整权限自动化测试待做
- Authorization Server RSA 密钥启动时临时生成，重启后旧 Token 失效；生产环境需持久化密钥
- OAuth 客户端密钥当前有开发默认值；生产环境必须只从安全配置注入
- MCP Token 自动刷新尚未做实际过期等待验证；并发刷新可能重复申请 Token

---

## 教学协作约定（当前有效）

- 本项目唯一目的：教会用户开发 Agent，不是代写完整产品。
- AI 负责按学习主线决定下一步、拆成足够小的练习、解释原因、review 用户代码并指出错误。
- 用户默认亲自编写后端；只有用户明确说“你直接写”时，AI 才代写核心后端代码。
- 教学节奏必须是“AI 给出一小段背景和明确代码改动 → 用户动手 → AI review → 再进入下一小步”，不要一次要求用户设计完整方案。
- 用户说“继续”时，先复述当前恢复点，再从恢复点的下一小步开始。
- 用户说暂停时，更新本文件的暂停点，确保新对话可以从具体文件、方法和下一动作继续。
- 不主动运行构建或测试，除非用户明确要求。
- 新依赖、数据库结构变更、破坏性操作先征求用户同意。

## 下一步

1. 完善阶段7.4：把最终用户身份和角色安全传递到 MCP，并做工具级授权  
2. 补齐跨用户记忆读取/删除、跨用户确认及 MCP `401/403` 自动化测试  
3. 验证 Token 实际过期刷新，再进入阶段8可观测性  

**一句话**：功能 0–6 核心与 L3 已完成；阶段7会话隔离和 MCP 服务级 JWT/scope 鉴权已打通，下一步把最终用户身份与角色传到 MCP。
