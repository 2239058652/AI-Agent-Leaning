# Agent-Learning 交接文档（权威进度）

> **更新日期**：2026-08-28  
> **用途**：新开对话 / 换工具 / 换模型时 **先读本文件**。  
> **项目唯一目的**：教会用户开发 Agent，不是代写完整产品。  
> **冲突裁决**：`HANDOFF.md` + 代码 + `git log` > 其它 md（含审查报告、旁观者说、旧 README）。

---

## ★ 当前恢复点（2026-08-28）

| 项 | 状态 |
|---|---|
| **阶段** | 阶段7.4 用户级身份→MCP 主链**已打通并实测**（contextExtractor 桥接 + `get_weather` 工具级 ROLE_ADMIN 授权）；剩并发串号测试与边界自动化测试 |
| **git** | 核心已提交 `d75a010`（Spring AI + 记忆落库 + 工具桥接 + 清空会话）；此后改动均未提交 |
| **刚完成** | ① `mcpTransportProvider()` 配 `contextExtractor`（`getUserPrincipal()`），工具回调改读 `exchange.transportContext()`，实测 user/admin 身份与 roles 正确到达 MCP；② `get_weather` 工具级授权：无 `ROLE_ADMIN` → `isError=true` 文本返回，user1 被拒（LLM 向用户转述）、admin 放行，均实测；③ 前端 SSE 帧解析修复（trim 吃空格致英文粘连、多行 data 未按 `\n` 还原）；④ 思考块去掉固定高度滚动条 |
| **理解状态** | L3 M1–M5 及总调用图口述已过关；URL 级 vs 工具级授权粒度已理解 |
| **当前主线** | **并发串号测试 → 据结果决定 `ThreadLocal + 全局 McpSyncClient` 去留 → 边界自动化测试 → 阶段8 RAG** |
| **阶段6 还剩（可选/非阻塞进7）** | 书面框架对比；官方 JDBC starter 对比（动 pom 先问） |

开场 AI 应：读本文件 → 复述恢复点 → **问用户**从阶段7哪项开始，不擅自开写。

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

### 本次暂停点（2026-08-28）

本轮已完成：

- `mcp-server/McpConfig.mcpTransportProvider()`：builder 配 `contextExtractor`，从 `HttpServletRequest.getUserPrincipal()` 提取 `Authentication`，把 `userId`（`auth.getName()`，即 JWT `sub`）与 `roles`（`SCOPE_* + ROLE_*` 字符串列表）放进 `McpTransportContext`；内含两处**临时调试 println**（`[extractor] ...`），并发测试通过后删除。
- `McpConfig` 工具回调：改从 `exchange.transportContext()` 读身份（不再用 `SecurityContextHolder`，Reactor 线程上它是空的）。
- **实测结论**：启动 initialize/listTools 走服务 Token；用户工具调用 `[extractor] admin roles=[SCOPE_openid, SCOPE_profile, SCOPE_mcp.weather, ROLE_ADMIN]` + `MCP 工具调用用户: admin`——证明 ThreadLocal 确实把用户 Token 放进了 HTTP Header，且 context 桥接到回调成功。user1 同样实测通过。
- `get_weather` 工具级授权（回调内，读 roles 后）：无 `ROLE_ADMIN` → `CallToolResult.builder().isError(true).addTextContent("权限不足...")` 提前返回。实测 user1 被 LLM 转述拒绝、admin 正常取天气。已验证 `isError(java.lang.Boolean)` 为 SDK 1.1.3 实际 API。
- 前端 `App.tsx` SSE 解析重写为规范帧解析：空行分帧、`data:` 载荷 `slice(5)` 原样保留不 trim（修复英文单词粘连根因）、多行 data 用 `\n` 连接、兼容 `\r\n`、流结束冲刷残留帧。依赖事实：Spring `SseEmitter` 字节码确认 `data:` 后不带空格。
- `App.css` `.reasoning-content` 删除 `max-height:240px` + `overflow-y:auto`，思考过程展开多高显示多高。
- ai-assistant 已切 `spring-ai-starter-model-zhipuai`(1.1.8) + `glm-5.3-flash`，流式/思考/工具模式/记忆均经实际使用验证。

下一次恢复时，严格从这里开始：

1. **并发串号测试**：双浏览器（普通窗口 admin + 无痕窗口 user1）几乎同时发天气问题，重复 3~5 轮，盯 mcp-server 控制台。通过标准：每个 `[extractor]` 行与发送者一致、user1 始终被拒且 admin 始终拿到天气；忽略周期性 `[extractor] ai-assistant`（keepalive GET 的服务 Token）。失败信号：admin 看到「权限不足」或 user1 拿到天气 = Token 串号。
2. 通过 → 删 `McpConfig` 两处临时 println；失败 → 停用 `ThreadLocal + 全局 McpSyncClient`，改请求级/用户级 MCP Client。
3. 补自动化边界测试：跨用户记忆读取/删除、跨用户确认、MCP `401/403/角色`；未经用户明确要求不主动运行。
4. 前端退出/切换账号问题仍暂缓；Chrome DevTools 探测路径问题可在 Authorization Server `permitAll` 处理。
5. 之后进入阶段8 RAG。

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
| 7.4 用户级 | 最终用户身份/角色传递到 MCP + 工具级授权 | ✅ 主链已实测（user1 拒/admin 通） | 剩并发串号验证，是当前主线 |
| **8** | RAG与知识库 | 未开始 | 文档、Embedding、检索、引用、权限、评估 |
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
- 阶段7尚未完成：并发串号验证、跨用户与 MCP `401/403/角色` 完整权限自动化测试待做
- 阶段8尚未开始：RAG文档导入、切分、Embedding、检索、引用、知识库权限与评估待做
- Authorization Server RSA 密钥启动时临时生成，重启后旧 Token 失效；生产环境需持久化密钥
- OAuth 客户端密钥当前有开发默认值；生产环境必须只从安全配置注入
- MCP Token 自动刷新尚未做实际过期等待验证；并发刷新可能重复申请 Token
- `ThreadLocal + 全局 McpSyncClient` 单用户已验证生效，但并发身份串号风险未实测（下一步专项验证），失败则改请求级/用户级 client
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

1. 并发串号测试：双浏览器 user1/admin 同时发起，验证 `ThreadLocal + 全局 McpSyncClient` 是否串身份；通过则删临时日志，失败则改请求级 MCP Client  
2. 补齐跨用户记忆读取/删除、跨用户确认及 MCP `401/403` 自动化测试  
3. 验证 Token 实际过期刷新，再进入阶段8 RAG 与知识库

**一句话**：功能 0–6 核心与 L3 已完成；阶段7.4 用户级身份传递与 `get_weather` 工具级授权已实测通过，剩并发串号测试与边界自动化测试，之后进阶段8。
