# Agent Learning

Agent 开发学习项目，从零搭建 AI 助手，逐步扩展能力。按 `agent-dev-learning-plan-v3.md`（事件推进版）的阶段逐步迭代。

## 项目结构

```
Agent-Learning/
├── pom.xml                            # 父 Maven 项目（管理 ai-assistant + mcp-server）
├── ai-assistant/                      # AI 助手主模块（Spring Boot 后端，端口 3180）
│   ├── src/main/java/com/assistant/ai/
│   │   ├── AiAssistantApplication.java
│   │   ├── config/                    # LLM/MCP 配置、CORS、全局异常处理
│   │   ├── controller/                # REST 接口
│   │   ├── dto/                       # 请求/响应/统一返回体
│   │   ├── exception/                 # 业务异常
│   │   ├── service/                   # 核心逻辑（LLM 调用 + Agent Loop）
│   │   ├── tool/                      # 工具注册表、执行、参数校验
│   │   └── mcp/                       # MCP 客户端（连接 mcp-server，发现远程工具）
│   └── src/main/resources/
│       └── application.yaml
├── mcp-server/                        # MCP Server 独立模块（端口 3190）
│   └── src/main/java/com/assistant/mcp/
│       ├── McpServerApplication.java
│       ├── McpConfig.java             # MCP 协议配置（Streamable HTTP）
│       └── WeatherTool.java           # MCP Tool: get_weather
├── frontend/                          # React 前端（端口 3100）
│   ├── src/
│   │   ├── App.tsx                    # 聊天界面（流式输出 + 工具调用 Timeline）
│   │   └── main.tsx
│   └── vite.config.ts
├── agent-dev-learning-plan-v3.md      # 当前学习计划（事件推进版）
├── agent-dev-learning-plan-v2.md      # 旧版（19周计划）
├── 旁观者说.md
└── .claude/
    └── evaluation.md                  # 计划评估报告
```

## 快速开始

### 1. MCP Server（工具提供方，端口 3190）

```bash
cd mcp-server
mvn spring-boot:run
```

### 2. AI Assistant（后端，端口 3180）

```bash
cd ai-assistant
# 修改 src/main/resources/application.yaml 里的 API Key
mvn spring-boot:run
```

启动时会自动连接 MCP Server 并发现远程工具。

### 3. 前端（端口 3100）

```bash
cd frontend
pnpm install
pnpm dev
```

打开 http://localhost:3100 使用。

## 技术栈

- **后端**: Spring Boot 3.5 / Java 17 / Lombok / Validation
- **MCP**: MCP Java SDK 0.17（Streamable HTTP 传输）
- **前端**: React 19 / TypeScript / Vite 8
- **LLM**: LongCat API（OpenAI 兼容格式）

## 当前能力

### ai-assistant

| 接口 | 说明 |
|---|---|
| `POST /api/chat` | 非流式聊天，一次性返回完整回复 |
| `POST /api/chat/stream` | SSE 流式聊天，逐 chunk 推送 |
| `POST /api/chat/tool-stream` | SSE 流式 + Agent Loop（最多 10 轮），AI 调用工具后生成回复 |

**本地工具**: `get_current_date`（查日期）、`calculate`（计算器）、`get_ip`（查本机 IP）

**MCP 远程工具**: 通过 MCP 客户端从 mcp-server 发现，当前有 `get_weather`（查城市天气）

**工程能力**: 全局异常处理、参数校验、工具白名单 + 参数校验 + 敏感操作标记

### mcp-server

独立运行的 MCP Server，对外提供工具。当前注册 1 个工具：`get_weather`。

### 前端

流式输出（打字机效果）、工具调用链路 Timeline、中断、重试。

## 学习进度

**续接请读 [`HANDOFF.md`](./HANDOFF.md)**（任意新对话 / 换模型的权威进度）。

| 阶段 | 内容 | 状态 |
|---|---|---|
| 阶段1 | Spring Boot 工程化补强（异常处理、校验、分层） | ✅ |
| 阶段2 | LLM API 底层调用（HTTP + SSE 流式 → 后迁 Spring AI） | ✅ |
| 阶段3 | Tool Use / Function Calling + Agent Loop | ✅ |
| 阶段4 | 前端 Agent 闭环（Timeline、中断、重试、敏感确认） | ✅ |
| 阶段5 | MCP 协议（Server + 客户端集成） | ✅ |
| 阶段6 | Spring AI + 对话历史 + MySQL 落库 + 清空会话 | 核心✅ / 收尾中 |
| 阶段7 | 鉴权与权限边界 | 待做 |
| 阶段8-10 | 可观测性、限流成本统计、整合演示 | 待做 |
