# AI Assistant

Spring Boot 主后端。完整学习进度见仓库根目录 **`HANDOFF.md`**。

## 端口

`http://localhost:3180`

## 能力

| 接口 | 说明 |
|---|---|
| `POST /api/chat` | 非流式 |
| `POST /api/chat/stream` | SSE 流式 |
| `POST /api/chat/tool-stream` | SSE + 工具（Spring AI） |
| `POST /api/chat/execute-confirmed` | 敏感操作确认后执行 |
| `DELETE /api/conversations/{id}` | 清空会话记忆 |

请求示例需带 `conversationId`：

```bash
curl -N -X POST http://localhost:3180/api/chat/tool-stream \
  -H "Content-Type: application/json" \
  -d "{\"message\": \"你好\", \"conversationId\": \"demo-1\"}"
```

## 工具

- 本地：`get_current_date`、`calculate`、`get_ip`
- 业务：`query_orders`、`analyze_orders`、`cancel_order`（敏感）
- MCP：`get_weather`（需 mcp-server:3190）

## 技术栈

Spring Boot 3.5 / Java 17 / Spring AI 1.1.8 / MyBatis / MySQL / 智谱 GLM（`spring-ai-starter-model-zhipuai`，`glm-5.3-flash`）

## 运行

```bash
# 需 MySQL 库 ai_assistant；配置见 src/main/resources/application.yaml
mvn spring-boot:run
```

## 进度

以仓库根目录 `HANDOFF.md` 为准（勿用本文件旧表格当进度）。
