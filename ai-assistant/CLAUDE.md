# AI Assistant — 贯穿项目

Spring Boot 3.5 / Java 17，从零搭建的 AI 助手。  
**完整进度与续接说明见仓库根目录 `HANDOFF.md`（权威）。**

## 当前阶段

**阶段6 核心与 L3 巩固已完成**（Spring AI + 对话记忆落库 + 清空会话 + 确认闭环，已验证）。  
**当前**：阶段7 鉴权与权限边界；进度以根目录 `HANDOFF.md` 为准。  
说明：这里的「Checkpoint」实为**对话记忆落库**，非严格 Agent 步骤恢复。

## 项目结构（核心）

```
ai-assistant/src/main/java/com/assistant/ai/
├── AiAssistantApplication.java
├── config/          # LlmProperties, WebConfig, GlobalExceptionHandler, ChatMemoryConfig, McpProperties
├── controller/      # ChatController
├── dto/             # ChatRequest(含 conversationId), ChatResponse
├── service/         # ChatService(Spring AI), OrderService
├── tool/            # ToolRegistry, ToolService, ToolCallbackProvider, ToolValidator...
├── memory/          # MybatisChatMemoryRepository
├── mapper/          # ChatMemoryMapper, OrderMapper
├── entity/          # ChatMemoryEntry, Order...
├── mcp/             # McpClientService
└── exception/
```

## 能力

### API（端口 3180）

| 接口 | 说明 |
|---|---|
| `POST /api/chat` | 非流式 |
| `POST /api/chat/stream` | SSE 流式 |
| `POST /api/chat/tool-stream` | SSE + 工具（Spring AI Agent Loop） |
| `POST /api/chat/execute-confirmed` | 敏感操作确认后执行 |
| `DELETE /api/conversations/{id}` | 清空会话记忆（`chatMemory.clear`） |

### 工具

- 本地：`get_current_date`, `calculate`, `get_ip`
- 业务：`query_orders`, `analyze_orders`, `cancel_order`（敏感）
- MCP：`get_weather`（连接 mcp-server:3190）

### 记忆

- `MessageWindowChatMemory` 窗口 20
- `MybatisChatMemoryRepository` → 表 `chat_memory`
- 前端 `conversationId` 隔离会话

## 待做 / 债

- [ ] 阶段6 书面框架对比（可选）
- [ ] 6.5 官方 JDBC starter 对比（用户推迟，动 pom 先问）
- [ ] 确认文案从 ToolCallbackProvider 上移到 ToolDefinition
- [ ] 阶段7 鉴权
- [ ] MCP Resources / Prompts

## 协作

- 项目目的是**教会用户**开发 Agent，不是代写产品
- 默认用户写后端；明确说「你写」才代写
- 不主动跑构建/测试

## 运行

```bash
cd ai-assistant
mvn spring-boot:run
# http://localhost:3180
```

需 MySQL（库 ai_assistant）与可选 mcp-server:3190。配置见 `application.yaml`。

## 外部依赖

- 智谱 GLM API（官方 `spring-ai-starter-model-zhipuai`，模型 `glm-5.3-flash`，含 `reasoning_content` 思考内容）
- Spring AI 1.1.8 GA
