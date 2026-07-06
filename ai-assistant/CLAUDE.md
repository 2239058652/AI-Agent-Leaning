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

## 当前能力（第2阶段）

- `POST /api/chat` — 非流式聊天，一次请求拿到完整回复

## 后续扩展计划

| 阶段 | 新增能力 | 新增文件 |
|---|---|---|
| 第2阶段 | SSE 流式输出 | ChatController 新增 stream 端点 |
| 第3阶段 | Tool Use + Agent Loop | ToolService + 工具定义 |
| 第4阶段 | MCP Server | MCP 相关配置 |
| 第5阶段 | 对话历史 + 状态管理 | MessageService + Redis 集成 |
| 第6阶段 | React 前端 | 前端项目 |

## 构建与运行

```bash
cd ai-assistant
mvn spring-boot:run
```

启动后访问 http://localhost:8080/api/chat 测试。

## 外部依赖

- LongCat API（通过 HTTP 调用，不需要本地部署）
