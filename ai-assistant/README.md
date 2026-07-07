# AI Assistant

Spring Boot + React 的 AI 助手，随着 Agent 开发学习计划逐步扩展能力。

## 当前能力

| 功能 | 接口 | 说明 |
|---|---|---|
| 非流式聊天 | `POST /api/chat` | 一次性返回完整回复 |
| 流式聊天 | `POST /api/chat/stream` | SSE 逐 chunk 推送，打字机效果 |
| 工具调用 | `POST /api/chat/tool-stream` | Agent Loop：AI 调用本地工具后生成回复 |

### 已注册工具

| 工具名 | 功能 | 参数 |
|---|---|---|
| `get_weather` | 查询城市天气 | `location`: 城市名 |
| `get_current_date` | 获取当前日期 | 无 |
| `calculate` | 数学计算 | `expression`: 表达式 |

## 技术栈

- **后端**: Spring Boot 3.5 / Java 17 / MyBatis（预留）
- **前端**: React 19 / TypeScript / Vite
- **LLM**: LongCat API（OpenAI 兼容格式）

## 快速开始

### 后端

```bash
cd ai-assistant

# 修改配置
# src/main/resources/application.yaml 里填入你的 API Key

# 启动
mvn spring-boot:run
# 或在 IDEA 里运行 AiAssistantApplication.main()
```

后端启动在 `http://localhost:3180`

### 前端

```bash
cd frontend

pnpm install
pnpm dev
```

前端启动在 `http://localhost:3100`

## 项目结构

```
ai-assistant/
├── pom.xml
├── CLAUDE.md
└── src/main/java/com/assistant/ai/
    ├── AiAssistantApplication.java    # 启动类
    ├── config/
    │   ├── LlmProperties.java         # LLM API 配置
    │   └── WebConfig.java             # CORS 跨域配置
    ├── controller/
    │   └── ChatController.java        # REST 接口
    ├── dto/
    │   ├── ChatRequest.java           # 请求体
    │   └── ChatResponse.java          # 响应体
    ├── service/
    │   └── ChatService.java           # 核心逻辑（LLM 调用 + Agent Loop）
    └── tool/
        └── ToolService.java           # 本地工具执行

frontend/
├── src/
│   ├── App.tsx                        # 聊天界面
│   ├── App.css                        # 样式
│   └── main.tsx                       # 入口
└── vite.config.ts                     # Vite 配置
```

## API 示例

### 非流式聊天

```bash
curl -X POST http://localhost:3180/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "你好"}'
```

### 流式聊天

```bash
curl -N -X POST http://localhost:3180/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "你好"}'
```

### 工具调用

```bash
curl -N -X POST http://localhost:3180/api/chat/tool-stream \
  -H "Content-Type: application/json" \
  -d '{"message": "北京今天天气怎么样？"}'
```

## 学习进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| 第2阶段 | LLM API 基础（HTTP 调用 + SSE 流式） | ✅ |
| 第3阶段 | Tool Use + Agent Loop | ✅ |
| 第4阶段 | MCP 协议 | 🔜 |
| 第5阶段 | Agent 框架（对话历史 + 状态管理） | 🔜 |
| 第6阶段 | 前端集成 | ✅ |
