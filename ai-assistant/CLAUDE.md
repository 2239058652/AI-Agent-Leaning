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

## 当前能力（第4阶段）

- `POST /api/chat` — 非流式聊天
- `POST /api/chat/stream` — SSE 流式聊天
- `POST /api/chat/tool-stream` — SSE 流式 + 工具调用 Agent Loop（最多10轮）
- 全局异常处理 + 参数校验
- 4个本地工具：查天气、查日期、计算器、查IP
- 前端：流式输出 + 工具调用链路 Timeline + 中断 + 重试

## 跳过/待做事项

- [ ] **4.3 敏感操作确认弹窗** — 当前4个工具全部只读，无敏感操作。待阶段3.5加入敏感写操作后补做：后端工具白名单 + confirm_required 标记 + 前端确认弹窗。
- [ ] **@Transactional** — 无数据库层，暂不需要。待引入 JPA/JDBC 后评估。
- [ ] **Spring Security** — 阶段7再做。

## 后续扩展计划

| 阶段 | 新增能力 |
|---|---|
| 第3阶段(3.5) | 工具安全边界：白名单、参数校验、敏感操作待确认 |
| 第5阶段 | MCP Server（stdio + Streamable HTTP） |
| 第6阶段 | 框架选型 + 对话历史 + Checkpoint |
| 第7阶段 | 鉴权与权限边界 |

## 构建与运行

```bash
cd ai-assistant
mvn spring-boot:run
```

启动后访问 http://localhost:8080/api/chat 测试。

## 外部依赖

- LongCat API（通过 HTTP 调用，不需要本地部署）
