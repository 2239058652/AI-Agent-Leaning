# Agent-Learning 交接文档（权威进度）

> **更新日期**：2026-09-14  
> **用途**：新开对话 / 换工具 / 换模型时 **先读本文件**。  
> **项目唯一目的**：教会用户开发 Agent，不是代写完整产品。  
> **冲突裁决**：`HANDOFF.md` + 代码 + `git log` > 其它 md（含审查报告、旁观者说、旧 README）。

---

## ★ 当前恢复点（2026-09-14）

| 项 | 状态 |
|---|---|
| **阶段** | 阶段7 鉴权与权限边界**已过关**（用户级身份→MCP + 工具级 ROLE_ADMIN + 并发串号实测 + 边界自动化测试）；**当前主线：阶段8 RAG** |
| **git** | 核心已提交 `d75a010`（Spring AI + 记忆落库 + 工具桥接 + 清空会话）；此后改动均未提交 |
| **刚完成** | ① 并发串号测试通过，保留 `ThreadLocal + 全局 McpSyncClient`；`McpConfig` 临时 println 已不在。② 边界自动化：`PendingConfirmationStoreTest`（跨用户确认+一次性消费）、`ConversationIsolationTest`（删除走 `userId:前端id`）、`McpAuthBoundaryTest`（无 Token→401、无 `SCOPE_mcp.weather`→403）、`WeatherRoleAuthTest`（无 `ROLE_ADMIN`→`isError=true`，admin 放行）。③ `McpClientTest` 仅把 import 改成 `json.jackson2` 以便编译，**鉴权逻辑仍旧、不要跑它**。 |
| **理解状态** | L3 M1–M5 及总调用图口述已过关；URL 级 vs 工具级 vs 数据范围三层已用测试钉住 |
| **当前主线** | **阶段8 RAG：先区分「对话记忆 ≠ 知识库」，再从一份 Markdown 文档导入开始，不要先上向量库** |
| **阶段6 还剩（可选/非阻塞）** | 书面框架对比；官方 JDBC starter 对比（动 pom 先问） |

开场 AI 应：读本文件 → 复述恢复点 → 从阶段8 当前小步开始，不擅自开写大段代码。

### 工作区注意（可能未提交）

- 修改中：Authorization Code/OIDC、前端 PKCE、用户 JWT 传播、MCP transport context 与工具级授权
- 以 `git status` 为准
- 用户明确要求暂缓任务时，保留当前恢复点，不主动继续写代码、跑构建或跑测试。
- 当前已知无关改动，不要误提交：
  - `ai-assistant/.mvn/wrapper/maven-wrapper.properties`
  - `ai-assistant/mvnw.cmd`
  - 这是 Maven Wrapper 自动产生的改动。
- `authorization-server/src/main/resources/templates/login.html` 曾创建后删除，但当前 Git 状态为“index 已新增、worktree 已删除”；未经用户要求不要处理暂存区或提交。
- `ai-assistant/requests/security.http` 是未完成的临时文件，不能自动取得 Authorization Code 用户 Token，不要把它当成有效边界测试。
- **LLM 供应商迁移（2026-08-27，未提交，含 Maven Wrapper 之外的改动）**：
  - `ai-assistant/pom.xml`：Spring AI BOM 1.0.0 → 1.1.8；`spring-ai-starter-model-openai` → 官方 `spring-ai-starter-model-zhipuai`
  - `ai-assistant/src/main/resources/application.yaml`：`spring.ai.openai.*` → `spring.ai.zhipuai.*`（`glm-5.3-flash`），删除了手拼的 `completions-path` hack
  - `ai-assistant/.../ChatService.java`：1.1.8 Advisor 会话 ID 上下文化（`ChatMemory.CONVERSATION_ID`）+ 流式新增 `reasoning` SSE 事件（`forwardChatResponseEvents` 为厂商隔离点）
  - `frontend/src/App.tsx` + `App.css`：思考过程折叠展示 + "思考中…"占位气泡
  - `README.md` / `ai-assistant/README.md` / `ai-assistant/CLAUDE.md` / 本文件：LongCat → 智谱 GLM 相关说明
  - **未验证**：1.1.8 升级未构建/未运行（按规矩需用户确认后验证：启动装配、流式思考粒度、工具模式 Agent Loop、记忆落库）
  - **CVE-2026-29062 修复（用户已加）**：`dependencyManagement` 覆盖 `tools.jackson.core:jackson-core/jackson-databind` → 3.1.4（MCP SDK `mcp` 核心传递引入的 Jackson 3.0.3 有嵌套深度绕过 DoS，修复版 >= 3.1.0）；`mvn compile` 已验证通过

### 本次暂停点（2026-09-14）

本轮已完成（阶段7 收尾）：

- 并发串号测试通过（双浏览器 admin / user1 同时问天气）；保留 `ThreadLocal + 全局 McpSyncClient`。`McpConfig` 临时调试 println 已不在。
- `PendingConfirmationStoreTest`：主人一次性消费；他人 `consume` 返回 null 且不能删掉主人记录。
- `ConversationIsolationTest`：`deleteByConversationId` 调 `chatMemory.clear("userId:前端id")`。
- `McpAuthBoundaryTest`：无 Token → 401；有 Token 无 `SCOPE_mcp.weather` → 403（`JwtDecoder` mock，不连 3170）。
- `WeatherRoleAuthTest`：`McpConfig.denyWeatherIfNotAdmin` 无 `ROLE_ADMIN` → `isError=true`，admin 返回 null 放行。
- 验证命令（PowerShell 必须给 `-Dtest=` 加引号）：
  - `ai-assistant`：`mvn "-Dtest=PendingConfirmationStoreTest,ConversationIsolationTest" test` → 3 通过
  - `mcp-server`：`mvn "-Dtest=McpAuthBoundaryTest,WeatherRoleAuthTest" test` → 4 通过
- `McpClientTest` 只改了 `json.jackson` → `json.jackson2` 以便 testCompile；**不要跑它**（无 Token，和现鉴权对不上）。

下一次恢复时，严格从这里开始：

1. **阶段8.1 第一小步**：先讲清「对话记忆 ≠ 知识库」，再准备一份可导入的 Markdown，不要先加向量库 / Embedding 依赖。
2. 前端退出/切换账号问题仍暂缓。
3. Token 实际过期刷新仍未做等待验证（非阻塞进 8）。
4. 阶段6 可选收尾仍非阻塞。

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
| **7.1–7.3** | 身份、角色、工具权限、订单与会话数据范围 | ✅ 已验证 + 自动化 | 会话前缀 / 确认 Store / 订单 userId |
| **7.4 服务级** | Authorization Server、MCP JWT 验签、scope、客户端 Token | ✅ 手工闭环已验证 | 已理解服务身份与用户身份的区别 |
| 7.4 用户级 | 最终用户身份/角色传递到 MCP + 工具级授权 | ✅ 主链实测 + 并发串号通过 + 401/403/角色测试 | 三层权限已钉住 |
| **8** | RAG与知识库 | 未开始（当前主线） | 先文档导入，再切分 / Embedding / 检索 / 引用 / 权限 / 评估 |
| 9 | 可观测性与审计 | 未开始 | — |
| 10 | 限流、成本统计与多租户 | 未开始 | — |
| 11 | 系统整合与演示 | 未开始 | — |

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
- `McpClientTest` 仍按无鉴权时代写的，只修了 jackson2 import；整模块 `mvn test` 会连真 MCP 且无 Token，不要当回归套件跑
- 阶段8尚未开始：RAG文档导入、切分、Embedding、检索、引用、知识库权限与评估待做
- Authorization Server RSA 密钥启动时临时生成，重启后旧 Token 失效；生产环境需持久化密钥
- OAuth 客户端密钥当前有开发默认值；生产环境必须只从安全配置注入
- MCP Token 自动刷新尚未做实际过期等待验证；并发刷新可能重复申请 Token
- `ThreadLocal + 全局 McpSyncClient` 并发串号已手工通过，保留；若以后再现串号再改请求级/用户级 client
- 前端 OIDC 退出/切换账号流程未稳定验证，当前暂缓；当前实现仅清理前端 Token 并尝试调用 `/connect/logout`，不能视为已验证可用
- 思考内容读取依赖智谱消息类型 `ZhiPuAiAssistantMessage`（已收进 `ChatService.forwardChatResponseEvents` 单点隔离）；将来换厂商需改该处或关闭思考显示
- 阶段6 收尾的「官方 starter 对比」已部分落地（LLM 官方 zhipuai starter）；官方 JDBC starter 对比仍未做

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

1. 阶段8.1：区分对话记忆与知识库，准备一份带元数据的 Markdown，再写最小导入（读文件 → 清洗 → 留下源/标题），**先不要加向量库依赖**  
2. 之后才是切分、Embedding、检索、引用、知识库权限、评估  
3. Token 实际过期刷新、前端退出/切账号仍暂缓；阶段6 可选收尾非阻塞

**一句话**：0–7 核心已过关；阶段7 三层权限（URL / 工具 / 数据范围）已有自动化；当前从阶段8 RAG 的文档导入开始，不要先上向量库。
