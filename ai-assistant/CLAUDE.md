# AI Assistant — 贯穿项目

Spring Boot 3.5 / Java 17，从零搭建的 AI 助手，随着学习阶段逐步扩展能力。

## 项目结构

```
ai-assistant/
├── pom.xml
├── src/main/java/com/assistant/ai/
│   ├── AiAssistantApplication.java    ← 启动类
│   ├── config/
│   │   └── LlmProperties.java         ← LLM API 配置
│   ├── controller/
│   │   └── ChatController.java        ← REST 接口
│   ├── dto/
│   │   ├── ChatRequest.java           ← 请求体
│   │   └── ChatResponse.java          ← 响应体
│   └── service/
│       └── ChatService.java           ← 核心逻辑（调 LLM API）
└── src/main/resources/
    └── application.yaml               ← 配置文件
```

## 当前能力（第5阶段）

### ai-assistant（主项目）
- `POST /api/chat` — 非流式聊天
- `POST /api/chat/stream` — SSE 流式聊天
- `POST /api/chat/tool-stream` — SSE 流式 + 工具调用 Agent Loop（最多10轮）
- 全局异常处理 + 参数校验
- 工具安全边界：白名单 + 参数校验 + 敏感操作标记
- 4个本地工具：查天气、查日期、计算器、查IP
- 前端：流式输出 + 工具调用链路 Timeline + 中断 + 重试

### mcp-server（独立模块）
- MCP Server（Streamable HTTP 传输，端口 8090）
- 1个 MCP Tool：get_weather
- 测试客户端验证通过

## 跳过/待做事项

- [ ] **4.3 敏感操作确认弹窗** — 当前4个工具全部只读，无敏感操作。待加入敏感写操作后补做。
- [ ] **@Transactional** — 无数据库层，暂不需要。待引入 JPA/JDBC 后评估。
- [ ] **Spring Security** — 阶段7再做。
- [ ] **ai-assistant 集成 MCP** — 让 ai-assistant 变成 MCP 客户端，连接 mcp-server。

## 后续扩展计划

| 阶段 | 新增能力 | 状态 |
|---|---|---|
| 第1阶段 | 工程化补强：异常处理、校验、测试 | ✅ 完成 |
| 第3阶段(3.5) | 工具安全边界：白名单、参数校验、敏感操作待确认 | ✅ 完成 |
| 第4阶段 | 前端Agent闭环：Timeline、中断、重试 | ✅ 完成 |
| 第5阶段 | MCP Server（stdio + Streamable HTTP） | ✅ 完成 |
| 第5阶段后续 | ai-assistant 变成 MCP 客户端（连接 MCP Server） | 待做 |
| 第6阶段 | 框架选型 + 对话历史 + Checkpoint | 待做 |
| 第7阶段 | 鉴权与权限边界 | 待做 |

## MCP 学习路线

```
阶段5（当前）：先建最小 MCP Server，理解协议本身
  ↓
后续阶段：让 ai-assistant 成为 MCP 客户端，连接 MCP Server
  ↓
最终效果：ai-assistant 的工具可以来自本地代码，也可以来自远程 MCP Server
```

**关键区分**：
- MCP Server = 工具提供方（独立模块 `mcp-server/`）
- ai-assistant = MCP 客户端（后续改造，当前不动）

## 构建与运行

```bash
cd ai-assistant
mvn spring-boot:run
```

启动后访问 http://localhost:8080/api/chat 测试。

## 外部依赖

- LongCat API（通过 HTTP 调用，不需要本地部署）
