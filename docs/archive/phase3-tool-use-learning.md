# 第3阶段学习笔记：Tool Use / Function Calling

> 学习日期：2026-07-06
> 对应计划：3.1 Tool Schema 定义 / 3.2 工具调用完整链路 / 3.3 Agent Loop

---

## 一、Tool Use 是什么

**一句话：让 AI 能调用你写的代码，而不只是返回文本。**

普通聊天：
```
用户: "北京天气怎么样？"
AI: "我不知道实时天气"（或者瞎编）
```

带工具的聊天：
```
用户: "北京天气怎么样？"
AI: "我帮你查一下" → 调用 get_weather("北京") → 拿到结果 → "北京今天晴，28°C"
```

**AI 不会真的执行工具**，它只是说"我要调用 get_weather，参数是北京"。你（Java 代码）负责执行，然后把结果告诉它。

---

## 二、核心架构

### 请求流程

```
用户消息
  ↓
ChatController.chatToolStream()
  ↓
ChatService.chatStreamWithTools()
  ↓
┌─────────────────────────────────┐
│         Agent Loop              │
│                                 │
│  [第1轮] 发消息+工具定义给模型   │
│     ↓                           │
│  模型返回: tool_calls → get_weather("北京")
│     ↓                           │
│  ToolService.execute() → 执行工具
│     ↓                           │
│  把工具结果加入对话历史           │
│     ↓                           │
│  [第2轮] 把更新后的对话发给模型   │
│     ↓                           │
│  模型返回: "北京今天晴，28°C"    │
│     ↓                           │
│  finish_reason: "stop" → 结束   │
└─────────────────────────────────┘
  ↓
SSE 推送给前端
```

### 代码文件职责

| 文件 | 职责 |
|---|---|
| `ChatController.java` | 接收 HTTP 请求，创建 SseEmitter |
| `ChatService.java` | 核心逻辑：构建请求、Agent Loop、解析响应 |
| `ToolService.java` | 本地执行工具（天气、日期、计算器） |

---

## 三、关键代码讲解

### 1. 工具定义（告诉模型有哪些工具）

```java
// ChatService.buildToolsArray()
[
  {
    "type": "function",
    "function": {
      "name": "get_weather",           // 工具名，模型通过这个名字调用
      "description": "查询城市天气",    // 描述，模型根据这个决定什么时候用
      "parameters": {                  // 参数定义（JSON Schema）
        "type": "object",
        "properties": {
          "location": {
            "type": "string",
            "description": "城市名称"
          }
        },
        "required": ["location"]       // 必填参数
      }
    }
  }
]
```

**这段 JSON 不是给代码用的，是给模型看的。** 模型读了这个 Schema，就知道"我有 get_weather 这个工具可以用，需要传 location 参数"。

### 2. Agent Loop（核心循环）

```java
// ChatService.chatStreamWithTools()
for (int round = 0; round < 10; round++) {
    // 1. 把对话历史 + 工具定义发给模型
    String requestBody = buildRequestBodyFromMessages(messages, true, true);
    HttpResponse<InputStream> response = httpClient.send(...);

    // 2. 读取流式响应
    String finishReason = readStreamResponse(response.body(), emitter, fullContent, toolCalls);

    // 3. 把 assistant 回复加入对话历史
    messages.add(assistantMsg);

    // 4. 判断模型要做什么
    if ("tool_calls".equals(finishReason)) {
        // 模型要调工具 → 执行工具 → 结果加入对话历史 → 继续下一轮
        for (ToolCallInfo tc : toolCalls) {
            String result = toolService.execute(tc.name, tc.arguments);
            messages.add(toolMsg);  // role: "tool"
        }
        continue;  // 回到循环顶部
    }

    // finish_reason 是 "stop" → 模型给了最终回复 → 结束
    break;
}
```

**关键点：**
- 每轮都要把**完整对话历史**发给模型（模型需要上下文才能决策）
- `finish_reason` 决定循环是否继续：`tool_calls` = 继续，`stop` = 结束
- 最多循环 10 次，防止死循环

### 3. 工具执行（本地代码）

```java
// ToolService.execute()
return switch (toolName) {
    case "get_weather" -> executeGetWeather(argsJson);    // 查天气
    case "get_current_date" -> executeGetCurrentDate();   // 查日期
    case "calculate" -> executeCalculate(argsJson);       // 计算
    default -> "错误: 未知工具 " + toolName;
};
```

**这里就是普通的 Java 方法调用。** 实际项目中会调真实 API（天气 API、数据库查询等），这里用假数据模拟。

### 4. 流式响应中的工具调用解析

```java
// ChatService.readStreamResponse()
// SSE 格式：
// data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_xxx",...
//        "function":{"name":"get_weather","arguments":"{\"loca"}}]}]}]}

JsonNode toolCallsNode = choice.path("delta").path("tool_calls");
if (toolCallsNode.isArray()) {
    for (JsonNode tc : toolCallsNode) {
        int index = tc.path("index").asInt(0);
        ToolCallInfo info = toolCalls.get(index);
        info.id = tc.path("id").asText("");           // 工具调用 ID
        info.name = tc.path("function").path("name").asText("");  // 工具名
        info.arguments += tc.path("function").path("arguments").asText("");  // 参数（可能分多个 chunk）
    }
}
```

**注意：** 工具调用的参数可能分多个 SSE chunk 推送，所以用 `+=` 拼接。

---

## 四、数据结构变化

### 普通聊天的对话历史

```json
[
  {"role": "user", "content": "北京天气怎么样？"},
  {"role": "assistant", "content": "北京今天晴，28°C"}
]
```

### 带工具调用的对话历史

```json
[
  {"role": "user", "content": "北京天气怎么样？"},
  {"role": "assistant", "content": "", "tool_calls": [
    {"id": "call_xxx", "function": {"name": "get_weather", "arguments": "{\"location\":\"北京\"}"}}
  ]},
  {"role": "tool", "tool_call_id": "call_xxx", "content": "天气: 晴, 温度: 28°C"},
  {"role": "assistant", "content": "北京今天晴，28°C"}
]
```

**多了两种消息角色：**
- `assistant` + `tool_calls`：模型说"我要调工具"
- `tool`：工具执行结果发回模型

---

## 五、前端配合

### 普通模式 vs 工具模式

| | 普通模式 | 工具模式 |
|---|---|---|
| API 端点 | `/api/chat/stream` | `/api/chat/tool-stream` |
| 模型行为 | 直接生成文本 | 先调工具，再生成文本 |
| 响应时间 | 快（几秒） | 慢（需要多轮交互） |
| 适用场景 | 闲聊、写作 | 需要实时数据的场景 |

### 前端切换逻辑

```typescript
const endpoint = toolMode
  ? 'http://localhost:3180/api/chat/tool-stream'
  : 'http://localhost:3180/api/chat/stream'
```

前端不需要关心工具调用的细节——后端通过 SSE 推送的都是文本 chunk，前端只管显示。

---

## 六、学到的概念

| 概念 | 一句话解释 |
|---|---|
| Tool Schema | JSON 格式的工具定义，给模型看的 |
| tool_calls | 模型返回的"我要调工具"的指令 |
| tool role | 工具执行结果的消息角色 |
| Agent Loop | 模型调工具 → 你执行 → 结果发回 → 模型再决策，循环 |
| finish_reason | `tool_calls`=继续循环，`stop`=结束 |
| 本地执行 | 工具是你的 Java 代码，不是模型执行的 |

---

## 七、下一步计划

第4阶段：MCP 协议 — 把工具通过标准协议暴露给其他应用。

当前工具是硬编码在 ToolService 里的，MCP 会让工具定义更标准化，其他应用也能调用你的工具。
