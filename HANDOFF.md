# Agent-Learning 交接文档（任意新对话从此续接）

> **更新日期**：2026-07-27  
> **用途**：新开对话 / 换模型时，先读本文件 + `agent-dev-learning-plan-v3.md` + 本文件列出的关键源码，即可无缝续作。  
> **项目唯一目的**：教会用户开发 Agent，不是代写完整产品。

---

## 0. 开场必做（给 AI）

1. 读本文件全文  
2. 读根目录 `CLAUDE.md`、`ai-assistant/CLAUDE.md`  
3. 读学习计划 `agent-dev-learning-plan-v3.md` 中「阶段6 / 阶段7」相关段  
4. 用 `git status` 确认未提交改动是否仍在  
5. **不要**默认代写业务代码；默认用户执笔，AI 出需求/验收/讲解/review（见下文协作规矩）  
6. **不要**主动跑 `mvn test` / 构建验证，除非用户明确要求  

### ★ 当前恢复点（2026-07-27）

| 项 | 状态 |
|---|---|
| **阶段** | 阶段6 进行中（Spring AI + 对话历史 + 手写 Checkpoint） |
| **刚完成** | 「清空会话 / 新对话」全链路，**用户已手动验证通过** |
| **下一步建议** | ① git 提交本轮未提交改动（需用户明确说 commit） ② 阶段6 收尾（框架对比书面总结 / 可选 JDBC starter 对比） ③ 进入阶段7 鉴权 |

开场可问用户：要先 **commit**、继续 **阶段6 收尾**，还是直接进 **阶段7**？

---

## 1. 协作规矩（必须遵守）

| 规则 | 说明 |
|---|---|
| 教学优先 | 先讲清「为什么」，再动代码；用户是前端转后端学习者 |
| 用户执笔 | 默认用户写实现；AI 给需求、验收、提示、review。用户明确说「你直接写」才代写 |
| 前端例外 | 用户是五年前端，可主动要求 AI 写前端（已发生：新对话按钮由 AI 写） |
| 只改必须改的 | 不顺手重构邻近代码 |
| 新依赖/全局安装/破坏性命令 | 先问用户 |
| 验证 | 不主动跑构建/测试；列出用户手动验证步骤 |
| 计划文件 | 写到 `Agent-Learning/.claude/plans/`，不要写到全局 `~/.claude/plans/` |
| 终端 | PowerShell 7；命令示例用 pwsh 语法 |
| 包管理 | 前端用 pnpm |

---

## 2. 仓库与模块

```
Agent-Learning/
├── pom.xml                 # 父工程
├── ai-assistant/           # 主后端 Spring Boot，端口 3180
├── mcp-server/             # MCP Server，端口 3190
├── frontend/               # React 19 + Vite，端口 3100
├── agent-dev-learning-plan-v3.md   # 主学习计划
├── HANDOFF.md              # 本文件
├── 阶段0-5审查报告.md       # 历史审查（部分已补做，以本文件为准）
└── 旁观者说.md
```

### 端口约定（31xx）

| 服务 | 端口 |
|---|---|
| frontend | 3100 |
| ai-assistant | 3180 |
| mcp-server | 3190 |

### 技术栈

- Java 17 / Spring Boot 3.5  
- **Spring AI 1.0.0 GA**（ChatClient、MessageWindowChatMemory、FunctionToolCallback）  
- MyBatis + **MySQL**（库名 `ai_assistant`，连接见 `ai-assistant/src/main/resources/application.yaml`）  
- MCP Java SDK（Streamable HTTP）  
- 前端：React 19 / TypeScript / Vite 8 / pnpm  
- LLM：LongCat API（OpenAI 兼容）

### 启动

```powershell
# 终端1：MCP
cd mcp-server; mvn spring-boot:run

# 终端2：后端
cd ai-assistant; mvn spring-boot:run

# 终端3：前端
cd frontend; pnpm install; pnpm dev
```

前端：http://localhost:3100  

---

## 3. 学习进度总表

| 阶段 | 内容 | 状态 |
|---|---|---|
| 0 | 主线业务边界 | ✅（后补订单业务） |
| 1 | Spring Boot 工程化 | ✅ |
| 2 | LLM HTTP + SSE | ✅（后迁到 Spring AI） |
| 3 | Tool Use + Agent Loop | ✅ |
| 3.5 | 工具安全边界 | ✅ |
| 4 | 前端闭环 Timeline/中断/重试/确认弹窗 | ✅ |
| 5 | MCP Server + ai-assistant 作 MCP 客户端 | ✅ |
| **6** | **框架选型 + 对话历史 + Checkpoint** | **进行中（核心已通）** |
| 7 | 鉴权与权限边界 | 待做 |
| 8 | 可观测性与审计 | 待做 |
| 9 | 限流、成本、多租户（可降级） | 待做 |
| 10 | 整合演示 | 待做 |

### 阶段6 子项

| 子项 | 状态 | 说明 |
|---|---|---|
| 6.1–6.3 框架对比/选型/重写 | 实践完成，书面总结可选 | 已选 Spring AI，ChatService 已迁 |
| 6.4 对话历史 | ✅ 用户验证 | conversationId + MessageWindowChatMemory(20) |
| 6.5 Checkpoint 手写版 | ✅ 用户验证 | MySQL `chat_memory` + MybatisChatMemoryRepository |
| 6.5 官方 JDBC starter 对比 | 推迟 | 动 pom 前必须问用户 |
| 清空会话 / 新对话 | ✅ 用户验证 | 见 §5 |

---

## 4. 架构要点（续作必懂）

### 4.1 请求主路径

```
前端 POST { message, conversationId }
  → ChatController（开 SseEmitter）
  → ChatService（线程池后台）
      → MessageChatMemoryAdvisor（按 conversationId 读写历史）
      → toolCallbacks + toolContext(emitter)（仅 tool-stream）
      → Spring AI ChatClient.stream()
  → SSE: chunk / confirmation_required / done
  → 前端 reader 解析，更新最后一条 assistant 消息
```

### 4.2 角色分工

| 组件 | 职责 |
|---|---|
| **Spring AI ChatClient** | Agent Loop（模型↔工具循环）、流式输出 |
| **ToolCallbackProvider** | 桥：ToolDefinition → FunctionToolCallback；执行仍进 ToolService |
| **ToolService** | 白名单、参数校验、敏感拦截、真正执行 |
| **ChatMemory** | 窗口策略（最多 20 条） |
| **MybatisChatMemoryRepository** | 落库；`saveAll` 是**整体替换**（先删后插 + @Transactional） |
| **conversationId** | **会话**隔离（不是 userId）；前端 `conversationIdRef` 存 UUID |

### 4.3 敏感操作两段式

1. **Agent 循环内**：`cancel_order` → needsConfirmation → SSE `confirmation_required` → 弹窗；工具返回字符串约束模型勿让用户打字确认  
2. **用户点确认后**：`POST /api/chat/execute-confirmed` → `toolService.executeConfirmed`，**不再问模型**

### 4.4 业务工具（订单）

| 工具 | 类型 | 说明 |
|---|---|---|
| query_orders | 只读 | 查订单 |
| analyze_orders | 聚合 | 统计 |
| cancel_order | **敏感写** | 仅 PENDING 可取消；需确认 |
| get_current_date / calculate / get_ip | 本地只读 | |
| get_weather | MCP 远程 | mcp-server 提供 |

### 4.5 对话记忆表 `chat_memory`

列：`conversation_id`, `message_index`, `message_type` (USER/ASSISTANT/SYSTEM/…), `content`  
索引：`(conversation_id, message_index)`  
TOOL 类型还原不完整时跳过（已知简化）。

---

## 5. 刚完成：清空会话

### 后端（用户写）

- `DELETE /api/conversations/{id}` — `ChatController.deleteById`  
- `ChatService.deleteByConversationId` → **`chatMemory.clear(id)`**（禁止直接 Mapper）  
- 返回 `ChatResponse.ok("会话已清空", 0, 0)`  
- 空 id：Controller 抛 `IllegalArgumentException`

### 前端（用户要求 AI 代写）

- Header「新对话」按钮  
- `handleNewConversation`：`DELETE` 旧 id → `conversationIdRef = crypto.randomUUID()` → `setMessages([])`  
- 文件：`frontend/src/App.tsx`、`frontend/src/App.css`

### 验收（已通过）

- DataGrip 对应 conversation 行删除  
- 点「新对话」后模型失忆  
- 其它会话不受影响  

---

## 6. API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/chat` | 非流式 |
| POST | `/api/chat/stream` | SSE 纯聊天 |
| POST | `/api/chat/tool-stream` | SSE + 工具 |
| POST | `/api/chat/execute-confirmed` | 敏感操作确认后执行 |
| DELETE | `/api/conversations/{id}` | 清空该会话记忆 |

请求体聊天类：`{ "message": "...", "conversationId": "uuid", "systemPrompt": 可选 }`

SSE 事件名：`chunk` | `tool_call` | `tool_result` | `confirmation_required` | `done`

---

## 7. 关键源码路径

```
ai-assistant/src/main/java/com/assistant/ai/
├── controller/ChatController.java
├── service/ChatService.java          # Spring AI 主逻辑 + clear
├── service/OrderService.java
├── tool/ToolService.java
├── tool/ToolRegistry.java
├── tool/ToolCallbackProvider.java    # 桥接 + confirmation_required
├── config/ChatMemoryConfig.java
├── memory/MybatisChatMemoryRepository.java
├── mapper/ChatMemoryMapper.java
├── entity/ChatMemoryEntry.java
├── mcp/McpClientService.java
└── dto/ChatRequest.java              # 含 conversationId

ai-assistant/src/main/resources/
├── application.yaml
└── mapper/ChatMemoryMapper.xml

frontend/src/App.tsx                  # SSE 解析、conversationId、新对话
frontend/src/App.css
```

---

## 8. Git 状态（交接时）

**分支**：`main`（与 origin 同步，但工作区有大量未提交）

**已修改（modified）**

- `ai-assistant/.../ChatController.java` — 含 DELETE 清空  
- `ai-assistant/.../ChatRequest.java` — conversationId  
- `ai-assistant/.../ChatService.java` — Spring AI + memory + clear  
- `ai-assistant/.../ToolRegistry.java` — cancel_order 描述去「请确认」  
- `frontend/src/App.tsx` — conversationId、SSE eventName、新对话  
- `frontend/src/App.css` — 新对话按钮样式  

**未跟踪（untracked，阶段6 新增）**

- `config/ChatMemoryConfig.java`  
- `entity/ChatMemoryEntry.java`  
- `mapper/ChatMemoryMapper.java`  
- `memory/MybatisChatMemoryRepository.java`  
- `tool/ToolCallbackProvider.java`  
- `resources/mapper/ChatMemoryMapper.xml`  

**建议提交说明（用户确认后再 commit）**  
`feat(stage6): Spring AI 对话记忆落库 + 工具桥接 + 清空会话`

**最近已提交相关**

- `e25b082` 接入 Spring AI 非流式  
- `f4f9a25` 环境变量 key  
- 更早：订单业务、MCP  

---

## 9. 遗留与技术债

| 优先级 | 项 |
|---|---|
| 中 | 确认文案硬编码在 `ToolCallbackProvider`（「取消订单」），宜上移 `ToolDefinition` |
| 中 | 阶段6 书面：为何选 Spring AI vs LangGraph4j / SK（可选） |
| 低 | 6.5 官方 JDBC starter 对比（用户推迟，动 pom 先问） |
| 低 | MCP Resources / Prompts 未接入；`ConfigResource` 曾为死代码类风险 |
| 低 | mcp-server SDK 协议版本 WARN，无实际影响 |
| 低 | 部分单测构造参数可能与现构造不一致（历史审查提过） |
| 低 | 严格「Agent 步骤级 Checkpoint」≠ 当前「对话历史落库」；概念上勿混 |

---

## 10. 用户近期理解状态（教学上下文）

- 已建立：`conversationId` = 会话桌号，不是 userId  
- 已建立：主路径 点发送 → Controller → Service → SSE → 前端  
- 已建立：敏感操作两段式；`chatMemory.clear` 分层  
- 曾卡：SSE `eventName` 作用域、空 assistant 占位消息 — 已讲通  
- 反馈过：参与太浅则无法形成理解 → 强化「用户执笔」  
- 后端作业自己写；前端可代写  

---

## 11. 建议的下一步任务卡

### A. 提交代码（需用户明确说 commit）

- 暂存 §8 所列文件，写清晰 commit message  
- 勿提交密钥；检查 `application.yaml` / env  

### B. 阶段6 收尾（可选）

- 用自己的话写半页：为什么选 Spring AI、对话历史 vs Checkpoint 区别  
- （可选）官方 JDBC ChatMemory 对比手写 MyBatis  

### C. 阶段7 鉴权（计划主线）

- 每次 Agent 绑定 userId  
- 工具层权限校验  
- 敏感确认不能替代权限  
- 参考 `agent-dev-learning-plan-v3.md` 阶段7  

### D. 小债清理

- 确认文案配置化  
- 修过时测试  

---

## 12. 相关文档索引

| 文件 | 用途 |
|---|---|
| `HANDOFF.md` | **本文件：续接权威进度** |
| `agent-dev-learning-plan-v3.md` | 阶段目标与退出标准 |
| `ai-assistant/CLAUDE.md` | 模块能力与结构（应与本文件同步） |
| `CLAUDE.md` | 项目级：教学目的 |
| `阶段0-5审查报告.md` | 历史缺口；部分已补，以 HANDOFF 为准 |
| `旁观者说.md` | 外部视角提醒（业务载体等） |
| `~/.claude/projects/.../memory/` | Claude Code 本地记忆（换模型时可能读不到，以本仓库 HANDOFF 为准） |

---

**一句话现状**：阶段 0–5 完成；阶段 6 核心（Spring AI + 窗口记忆 + MySQL 落库 + 清空会话）已通并验证；大量改动未 git 提交；下一主线是阶段 6 收尾或阶段 7 鉴权。
