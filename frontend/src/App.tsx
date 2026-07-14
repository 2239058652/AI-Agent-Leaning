import { useState, useRef, useEffect, useCallback } from 'react'
import './App.css'

// ============================================================================
// 类型定义
// ============================================================================

/** 工具调用信息 — 模型决定调用某个工具时产生 */
interface ToolCall {
  name: string
  arguments: string
}

/** 工具执行结果 — 本地工具执行完毕后产生 */
interface ToolResult {
  name: string
  result: string
}

/** 聊天消息 — 支持文本 + 工具调用链路 */
interface Message {
  role: 'user' | 'assistant'
  content: string
  toolCalls?: ToolCall[]
  toolResults?: ToolResult[]
  /** 标记这条消息是否因错误产生 */
  isError?: boolean
}

// ============================================================================
// 常量
// ============================================================================

const API_BASE = 'http://localhost:3180'

const SUGGESTIONS = [
  '用一句话介绍你自己',
  '帮我写一首诗',
  '解释什么是 Spring Boot',
  '推荐几本技术书籍'
]

const TOOL_SUGGESTIONS = [
  '北京今天天气怎么样？',
  '今天是星期几？',
  '帮我算一下 123 * 456',
  '上海天气如何？适合出去玩吗？'
]

// ============================================================================
// 工具名中文映射 — 让 Timeline 更易读
// ============================================================================

const TOOL_LABELS: Record<string, string> = {
  get_weather_by_city: '查天气',
  get_current_date: '查日期',
  calculate: '计算器',
  get_ip: '查IP',
}

function getToolLabel(name: string): string {
  return TOOL_LABELS[name] || name
}

// ============================================================================
// 主组件
// ============================================================================

function App() {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [toolMode, setToolMode] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const toolCallsRef = useRef<ToolCall[]>([])
  const toolResultsRef = useRef<ToolResult[]>([])

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages, loading])

  // ========================================================================
  // 发送消息 + SSE 解析
  // ========================================================================

  const sendMessage = useCallback(async (text?: string) => {
    const msg = text || input.trim()
    if (!msg || loading) return

    const userMessage: Message = { role: 'user', content: msg }
    setMessages(prev => [...prev, userMessage])
    setInput('')
    setLoading(true)

    const endpoint = toolMode
      ? `${API_BASE}/api/chat/tool-stream`
      : `${API_BASE}/api/chat/stream`

    const controller = new AbortController()
    abortRef.current = controller

    try {
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: msg }),
        signal: controller.signal,
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      const reader = response.body?.getReader()
      if (!reader) throw new Error('无法读取响应流')

      const decoder = new TextDecoder()
      let assistantContent = ''
      let buffer = ''
      // 工具调用链路：用 ref 跟踪，避免 React 批处理导致丢失
      toolCallsRef.current = []
      toolResultsRef.current = []

      // 先插入一条空的 assistant 消息，后续逐步填充
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: '',
        toolCalls: [],
        toolResults: [],
      }])

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        let eventName = ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (!trimmed) continue

          // ---- SSE 事件名行 ----
          // 格式: "event: tool_call"
          if (trimmed.startsWith('event:')) {
            eventName = trimmed.substring(6).trim()
            continue
          }

          // ---- SSE 数据行 ----
          // 格式: "data: {...}"
          if (!trimmed.startsWith('data:')) continue

          const eventData = trimmed.substring(5).trimStart()

          // 处理 [DONE]
          if (eventData === '[DONE]') {
            eventName = ''
            continue
          }

          // ---- 工具调用事件 ----
          if (eventName === 'tool_call') {
            try {
              const tc = JSON.parse(eventData) as ToolCall
              toolCallsRef.current = [...toolCallsRef.current, tc]
            } catch { /* ignore parse error */ }
            eventName = ''
            continue
          }

          // ---- 工具结果事件 ----
          if (eventName === 'tool_result') {
            try {
              const tr = JSON.parse(eventData) as ToolResult
              toolResultsRef.current = [...toolResultsRef.current, tr]
            } catch { /* ignore parse error */ }
            eventName = ''
            continue
          }

          // ---- 文本内容事件（默认） ----
          // 没有 event 字段时，data 就是文本内容
          if (eventName === '' || eventName === 'chunk') {
            assistantContent += eventData
          }
          eventName = ''

          // ---- 更新最后一条 assistant 消息 ----
          setMessages(prev => {
            const updated = [...prev]
            updated[updated.length - 1] = {
              role: 'assistant',
              content: assistantContent,
              toolCalls: toolCallsRef.current,
              toolResults: toolResultsRef.current,
            }
            return updated
          })
        }
      }
    } catch (error) {
      if ((error as Error).name === 'AbortError') {
        // 用户主动中断，不显示错误
        setMessages(prev => {
          const last = prev[prev.length - 1]
          if (last?.role === 'assistant' && last.content === '') {
            return prev.slice(0, -1)
          }
          return prev
        })
        return
      }

      const errMsg = error instanceof Error ? error.message : String(error)
      setMessages(prev => {
        const last = prev[prev.length - 1]
        if (last?.role === 'assistant' && last.content === '' && !last.toolCalls?.length) {
          const updated = [...prev]
          updated[updated.length - 1] = {
            role: 'assistant',
            content: `请求失败: ${errMsg}`,
            isError: true,
          }
          return updated
        }
        return [...prev, {
          role: 'assistant',
          content: `请求失败: ${errMsg}`,
          isError: true,
        }]
      })
    } finally {
      setLoading(false)
      abortRef.current = null
    }
  }, [input, loading, toolMode])

  // ========================================================================
  // 中断
  // ========================================================================

  const handleStop = () => {
    abortRef.current?.abort()
  }

  // ========================================================================
  // 重试 — 找到最后一条用户消息，重新发送
  // ========================================================================

  const handleRetry = () => {
    const lastUserMsg = [...messages].reverse().find(m => m.role === 'user')
    if (!lastUserMsg) return
    // 删掉最后一条错误消息
    setMessages(prev => {
      const last = prev[prev.length - 1]
      if (last?.isError) return prev.slice(0, -1)
      return prev
    })
    sendMessage(lastUserMsg.content)
  }

  // ========================================================================
  // 键盘事件
  // ========================================================================

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  const suggestions = toolMode ? TOOL_SUGGESTIONS : SUGGESTIONS

  // ========================================================================
  // 渲染
  // ========================================================================

  return (
    <div className="app">
      {/* Header */}
      <div className="header">
        <div className="header-logo">A</div>
        <div className="header-info">
          <h1>AI Assistant</h1>
          <span>LongCat-2.0 · {toolMode ? '工具模式' : '普通模式'}</span>
        </div>
        <label className="tool-toggle">
          <input
            type="checkbox"
            checked={toolMode}
            onChange={(e) => {
              setToolMode(e.target.checked)
              setMessages([])
            }}
          />
          <span className="toggle-slider"></span>
          <span className="toggle-label">🔧 工具</span>
        </label>
      </div>

      {/* Messages */}
      <div className="chat-container">
        <div className="messages">
          {messages.length === 0 && !loading ? (
            <div className="empty-state">
              <div className="empty-icon">{toolMode ? '🔧' : '💬'}</div>
              <h2>{toolMode ? '工具模式已开启' : '有什么可以帮你的？'}</h2>
              <p>{toolMode ? 'AI 可以调用本地工具：查天气、查日期、计算器' : '发送消息开始对话'}</p>
              <div className="suggestions">
                {suggestions.map((s, i) => (
                  <button key={i} className="suggestion" onClick={() => sendMessage(s)}>
                    {s}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            messages.map((msg, i) => (
              <div key={i} className={`message ${msg.role}`}>
                <div className="avatar">
                  {msg.role === 'user' ? '你' : 'AI'}
                </div>
                <div className="message-body">
                  {/* 工具调用链路 Timeline */}
                  {msg.toolCalls && msg.toolCalls.length > 0 && (
                    <div className="tool-timeline">
                      {msg.toolCalls.map((tc, j) => (
                        <div key={j} className="tool-item">
                          <div className="tool-call">
                            <span className="tool-icon">🔧</span>
                            <span className="tool-name">{getToolLabel(tc.name)}</span>
                            <span className="tool-args">({tc.arguments})</span>
                          </div>
                          {msg.toolResults && msg.toolResults[j] && (
                            <div className="tool-result">
                              <span className="tool-arrow">↳</span>
                              <span className="tool-result-text">
                                {msg.toolResults[j].result}
                              </span>
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}

                  {/* 文本内容 */}
                  {msg.content && (
                    <div className={`content ${msg.isError ? 'error' : ''}`}>
                      {msg.content}
                      {loading && i === messages.length - 1 && msg.role === 'assistant' && !msg.isError && (
                        <span className="cursor">▌</span>
                      )}
                    </div>
                  )}

                  {/* 重试按钮 */}
                  {msg.isError && !loading && (
                    <div className="retry-area">
                      <button className="retry-btn" onClick={handleRetry}>
                        ↻ 重试
                      </button>
                    </div>
                  )}
                </div>
              </div>
            ))
          )}

          {/* 加载指示器 */}
          {loading && messages.length === 0 && (
            <div className="message assistant">
              <div className="avatar">AI</div>
              <div className="message-body">
                <div className="content loading">
                  {toolMode ? '调用工具中' : '思考中'}
                  <div className="loading-dots">
                    <span></span>
                    <span></span>
                    <span></span>
                  </div>
                </div>
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Input */}
        <div className="input-area">
          <div className="input-wrapper">
            <textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={toolMode ? '试试问我天气、日期、计算...' : '输入消息...'}
              rows={1}
              disabled={loading}
            />
            {loading ? (
              <button className="stop-btn" onClick={handleStop} title="停止">
                <svg viewBox="0 0 24 24">
                  <rect x="6" y="6" width="12" height="12" rx="2" />
                </svg>
              </button>
            ) : (
              <button
                className="send-btn"
                onClick={() => sendMessage()}
                disabled={!input.trim()}
              >
                <svg viewBox="0 0 24 24">
                  <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
                </svg>
              </button>
            )}
          </div>
          <div className="footer-hint">
            {toolMode ? '🔧 工具模式 · AI 可调用本地工具' : 'LongCat-2.0 · 按 Enter 发送'}
          </div>
        </div>
      </div>
    </div>
  )
}

export default App
