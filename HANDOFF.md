# Agent-Learning 交接文档（权威进度）

> **更新日期**：2026-10-08  
> **用途**：新开对话 / 换工具 / 换模型时 **先读本文件**。  
> **项目唯一目的**：教会用户开发 Agent，不是代写完整产品。  
> **冲突裁决**：`HANDOFF.md` + 代码 + `git log` > 其它 md（含审查报告、旁观者说、旧 README）。

---

## ★ 当前恢复点（2026-10-08）

| 项 | 状态 |
|---|---|
| **阶段** | 阶段9 已完成并已实地核对（2026-10-08 17:04 双执行）。数据库审计和本地 OpenTelemetry 都已接入。span 输出到应用日志，没有外部追踪平台 |
| **git** | 最近提交 `3384cd3`（阶段8）。阶段9 未提交 |
| **刚完成** | `span_index` 语义修正为「该层第几个 span」（同一 span 的事件共号）；`application.yaml` 加 `logging.file.name`；工具模式问日期、问天气两次执行的库表与 trace 均已核对通过 |
| **理解状态** | 一次 Agent 执行用 `execution_id` 串起来；`span_type` 区分执行、模型、工具、MCP；同一执行的所有 span 共享一个 traceId |
| **当前主线** | 阶段9 结束。下一步进入阶段10 前先讲限流、成本和多租户，不直接写代码 |
| **阶段6 还剩（可选/非阻塞）** | 书面框架对比；官方 JDBC starter 对比（动 pom 先问） |

开场 AI 应：读本文件 → 复述恢复点 → 阶段9 已完成并核对。不要再改审计表，不重新导入。阶段10 先讲再动手。

### 阶段9 实地核对结果（2026-10-08，已通过）

验证方式：重启 ai-assistant 后跑两次工具模式提问，同时读 `logs/ai-assistant.log` 和 `agent_audit_event` 表。

- **span_index 新语义已生效**（id ≥ 13 为新语义，id ≤ 12 为旧语义，分界线在 12/13 之间）：
  - `tool_called`=1 与 `tool_succeeded`=1 同号（旧数据是 1/2）
  - `execution_started`=1 与 `execution_completed`=1 同号（旧数据是 1/2）
  - 两次 `model_completed` 仍是 1、2，因为它们是两个独立 span
- **OTel trace 已验证**：同一 `execution.id` 下 `execution` / `model` / `tool`（或 `mcp`）span 共享同一 traceId；`execution` span 最后结束（流式响应关闭时才关）。日志形如 `'tool' : <traceId> <spanId> INTERNAL [tracer: ai-assistant:] AttributesMap{...}`
- **日志落盘**：`logging.file.name: logs/ai-assistant.log` 即可，**不需要加任何依赖**。原因是 Spring Boot 的 `LogbackLoggingSystem.beforeInitialize()` 会自动调 `configureJdkLoggingBridgeHandler()`，条件为 `jul-to-slf4j` 在 classpath（Boot 自带）且 JUL 只有 1 个 ConsoleHandler（默认）。`LoggingSpanExporter` 用的是 **java.util.logging，不是 slf4j**，靠这个桥才进 Logback。
- **日志路径注意**：相对路径按**启动时工作目录**解析。从仓库根启动时文件落在 `Agent-Learning/logs/ai-assistant.log`，不是 `ai-assistant/logs/`。
- **MCP 分层已覆盖**：`get_weather` 记为 `span_type=mcp`，`get_current_date` 记为 `tool`。

### 已知非缺陷现象（避免下次误判为 bug）

工具模式下问「北京今天天气怎么样」，模型会先复述一段"`get_weather` 要求 ROLE_ADMIN，普通用户即使有 scope 也会权限不足"，然后才给天气。**这是模型在复述检索材料，不是权限报告**：该段文字逐字来自 `assistant-policy.md:32`（「天气工具」一节，偏移 438-696 正好对应），由 `RagPromptAssembler` 拼进 user 消息。实际调用是成功的（日志 `MCP 工具返回: 北京今天: 晴 13.0~25.2°C`，库表 id 21 `tool_succeeded`）。模型说"我无法确认您当前的权限身份"是它自己加的。属于阶段8 的 RAG 措辞调优范围，不是阶段9 缺陷。

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

### 本次暂停点（2026-10-06）

阶段8 做到这里，**代码未提交**。用户明天新开会话，从手工验证接着做。

已完成：

- 样本文档 `ai-assistant/src/main/resources/knowledge/assistant-policy.md`。
- 入库：`MarkdownDocumentLoader` → `TextChunker.splitByHeadingThenWindow`（节内窗口偏移要加回原文 `startOffset`）→ `ChunkEmbedder` → `KnowledgeImporter`。文档先 `save` 拿到自增 id，再存块。
- 表（用户已在 `ai_assistant` 库建好，代码里没有迁移脚本）：
  - `knowledge_document`：`title/source_name/source_type/version/content/owner_user_id`
  - `knowledge_chunk`：`document_id/chunk_index/content/start_offset/end_offset/embedding_json`，外键指向文档
- 向量是 JSON 文本，不是 `float[]` 直接入库。读回在 `KnowledgeChunkRepository.toChunk`。
- 提问：`KnowledgeQuery.ask(userId, question, topK)` 只调 `documents.listOwned` 和 `chunks.listOwned`。SQL 都有 `WHERE owner_user_id = ?`，块查询是 `JOIN knowledge_document`。没有 `findAll`。
- `RagPromptAssembler`：系统规则写死；检索正文、来源、标题、偏移放用户消息；不写相似度分数。
- `KnowledgeAccess` 仍在，只管内存规则。持久化路径不再调用它。
- 真 Embedding：`ZhiPuAiEmbeddingModel(api, MetadataMode.NONE)`，默认 `Embedding-2`。`ZhiPuEmbeddingTest` 已通过（同义比无关更近）。不加新 Maven 依赖。
- `KnowledgeConfig` 把 Repository、Retriever、Assembler、`KnowledgeQuery` 做成 Bean。`ChunkEmbedder` 和 `ChunkRetriever` 注入同一个自动配置的 `EmbeddingModel`。`KnowledgeImporter` **故意不是 Bean**。
- `ChatService.chat` 和 `chatStream` 都调用 `knowledgeQuery.ask(userId, request.getMessage(), 3)`，`.system(ragPrompt.systemRules())`，`.user(ragPrompt.userMessage())`。这两条路径忽略前端 `systemPrompt`。
- `chatStreamWithTools` 已接检索，并写入审计事件。`RagPromptAssembler` 增加了工具例外。未验证。
- `ConversationIsolationTest` 的 `new ChatService(...)` 已补上 `mock(KnowledgeQuery)`。
- 样本文档已导入一次，`owner_user_id=admin`，向量是真模型算的，事务已提交。`ImportPolicyDocumentTest` 用了 `@Commit` + `@Transactional(NOT_SUPPORTED)`，因为 `@JdbcTest` 外层事务会把普通 `@Commit` 也回滚掉。
- Spring AI 的 `org.springframework.ai.document.Document` 和 `com.assistant.ai.knowledge.Document` 不是同一个类。测试里实现 `embed(Document)` 必须写全限定名，不能 `import` 前者。

已通过的测试（PowerShell 的 `-Dtest=` 必须加引号）：

- 内存：`MarkdownDocumentLoaderTest`、`TextChunkerTest`、`ChunkEmbedderTest`、`ChunkRetrieverTest`、`RagPromptAssemblerTest`、`KnowledgeAccessTest`
- 持久化：`KnowledgeDocumentRepositoryTest`、`KnowledgeChunkRepositoryTest`、`KnowledgePersistenceTest`（回滚）、`KnowledgeQueryTest`、`KnowledgeImporterTest`
- 打网：`ZhiPuEmbeddingTest`、`ImportPolicyDocumentTest`（后者会再插入，不要再跑）
- 阶段9：`AuditRecorderTest`（2026-10-08 通过。注意该测试在 `com.assistant.ai` 包，`AuditRecorder.digest` 与 `DIGEST_LIMIT` 是包私有，测试里不能直接调，只能走 `record()` 后抓 `argsDigest` 验证截断）

下一次恢复时，严格从这里开始：

1. **不要再跑 `ImportPolicyDocumentTest`**，会再插入一整份政策。
2. 非流式已验证：admin 引用 `assistant-policy.md`；user1 材料为空。
3. 普通流式已验证：admin 在页面（工具模式关闭）问同一句，引用 `assistant-policy.md` 偏移 231-438。
4. 前端工具模式走 `/api/chat/tool-stream`，仍不检索。这是当前缺口，不是故障。
5. 接 `chatStreamWithTools` 前先讲。它不能照搬 `systemRules`，那套规则要求只根据材料回答，会和工具调用冲突。
6. `security.http` 仍是无关缩进，不要带上。
7. 前端退出/切账号、Token 过期刷新、阶段6 可选收尾、向量库，仍不做。`McpClientTest` 仍不要跑。

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
| **8 已接非流式、普通流式和工具模式** | 导入、切分、智谱 Embedding、MySQL、SQL 按主人过滤、带引用拼 prompt、三条聊天路径都调用 `knowledgeQuery.ask` | 非流式和普通流式已验证；工具模式检索未验证 | 材料 ≠ 系统指令；工具数据另走工具，不算检索材料 |
| 8 未做 | 向量库、评估、重复导入清理 | 未开始 | — |
| **9** | 审计事件、执行链路、模型/工具/MCP 分层、本地 OpenTelemetry | ✅ 已完成 | 一次执行同时有 `execution_id` 和 trace |
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
- 阶段8 已接 `ChatService.chat` 和 `chatStream`；`chatStreamWithTools` 不检索。不要重跑 `ImportPolicyDocumentTest`
- Authorization Server RSA 密钥启动时临时生成，重启后旧 Token 失效；生产环境需持久化密钥
- OAuth 客户端密钥当前有开发默认值；生产环境必须只从安全配置注入
- MCP Token 自动刷新尚未做实际过期等待验证；并发刷新可能重复申请 Token
- `ThreadLocal + 全局 McpSyncClient` 并发串号已手工通过，保留；若以后再现串号再改请求级/用户级 client
- 前端 OIDC 退出/切换账号流程未稳定验证，当前暂缓；当前实现仅清理前端 Token 并尝试调用 `/connect/logout`，不能视为已验证可用
- 思考内容读取依赖智谱消息类型 `ZhiPuAiAssistantMessage`（已收进 `ChatService.forwardChatResponseEvents` 单点隔离）；将来换厂商需改该处或关闭思考显示
- 阶段6 收尾的「官方 starter 对比」已部分落地（LLM 官方 zhipuai starter）；官方 JDBC starter 对比仍未做
- `span_index` 语义已于 2026-10-08 修正（同一 span 的事件共号）。表里 id ≤ 12 的行是旧语义（事件序号），id ≥ 13 是新语义，跨新旧数据统计时不能用同一个含义解释
- 阶段9 允许工具与 RAG 同时生效，模型会复述 `assistant-policy.md` 里的规则原文（见过「get_weather 要求 ROLE_ADMIN」那段）。这是材料复述，不是实际权限判定；调优方向在 `RagPromptAssembler` 的系统规则措辞（`:14-20`），不是审计或权限代码

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

1. 阶段9 已完成并已实地核对，不再改 `agent_audit_event`
2. 下一步是阶段10：限流、Token 成本、多租户。先讲方案，不直接写代码
3. 不要再跑 `ImportPolicyDocumentTest`
4. `security.http` 是无关缩进，不要带上
5. Token 过期刷新、前端退出/切账号、阶段6 可选收尾、向量库仍不做。OpenTelemetry 只写本地日志，没有 Jaeger 或 Zipkin

**一句话**：阶段0–9 已完成；阶段9 用 `execution_id` 串起一次 Agent 执行，实地核对（库表 + 本地 trace）已通过。下一步是阶段10。
