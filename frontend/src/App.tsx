import { useState, useRef, useEffect } from 'react'
import './App.css'

interface Message {
  role: 'user' | 'assistant'
  content: string
}

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

function App() {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [toolMode, setToolMode] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages, loading])

  const sendMessage = async (text?: string) => {
    const msg = text || input.trim()
    if (!msg || loading) return

    const userMessage: Message = { role: 'user', content: msg }
    setMessages(prev => [...prev, userMessage])
    setInput('')
    setLoading(true)

    // 根据模式选择 API 端点
    const endpoint = toolMode
      ? 'http://localhost:3180/api/chat/tool-stream'
      : 'http://localhost:3180/api/chat/stream'

    try {
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: msg })
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      const reader = response.body?.getReader()
      if (!reader) throw new Error('无法读取响应流')

      const decoder = new TextDecoder()
      let assistantContent = ''
      let buffer = ''

      setMessages(prev => [...prev, { role: 'assistant', content: '' }])

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (!trimmed) continue

          if (trimmed === 'data: [DONE]') continue

          if (trimmed.startsWith('data:')) {
            const data = trimmed.substring(5).trimStart()
            if (!data || data === '[DONE]') continue

            assistantContent += data

            setMessages(prev => {
              const updated = [...prev]
              updated[updated.length - 1] = {
                role: 'assistant',
                content: assistantContent
              }
              return updated
            })
          }
        }
      }
    } catch (error) {
      const errMsg = error instanceof Error ? error.message : String(error)
      setMessages(prev => {
        const last = prev[prev.length - 1]
        if (last?.role === 'assistant' && last.content === '') {
          const updated = [...prev]
          updated[updated.length - 1] = { role: 'assistant', content: `请求失败: ${errMsg}` }
          return updated
        }
        return [...prev, { role: 'assistant', content: `请求失败: ${errMsg}` }]
      })
    } finally {
      setLoading(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  const suggestions = toolMode ? TOOL_SUGGESTIONS : SUGGESTIONS

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
                  <div className="content">
                    {msg.content}
                    {loading && i === messages.length - 1 && msg.role === 'assistant' && (
                      <span className="cursor">▌</span>
                    )}
                  </div>
                </div>
              </div>
            ))
          )}
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
            />
            <button
              className="send-btn"
              onClick={() => sendMessage()}
              disabled={loading || !input.trim()}
            >
              <svg viewBox="0 0 24 24">
                <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
              </svg>
            </button>
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
