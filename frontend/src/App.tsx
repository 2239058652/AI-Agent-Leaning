import {useCallback, useEffect, useRef, useState} from 'react'
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
    get_weather: '查天气',
    get_current_date: '查日期',
    calculate: '计算器',
    get_ip: '查IP',
    query_orders: '查订单',
    analyze_orders: '订单统计',
    cancel_order: '取消订单',
}

function getToolLabel(name: string): string {
    return TOOL_LABELS[name] || name
}

// ============================================================================
// 主组件
// ============================================================================

/** 确认弹窗状态 */
interface ConfirmState {
    toolName: string
    argsJson: string
    message: string
}

function App() {
    const [messages, setMessages] = useState<Message[]>([])
    const [input, setInput] = useState('')
    const [loading, setLoading] = useState(false)
    const [toolMode, setToolMode] = useState(false)
    const [confirmState, setConfirmState] = useState<ConfirmState | null>(null)
    const messagesEndRef = useRef<HTMLDivElement | null>(null)
    const abortRef = useRef<AbortController | null>(null)
    const toolCallsRef = useRef<ToolCall[]>([])
    const toolResultsRef = useRef<ToolResult[]>([])
    // 会话 ID：页面加载时生成一次，本页所有消息属于同一会话（刷新即开新会话）
    const conversationIdRef = useRef(crypto.randomUUID())

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({behavior: 'smooth'})
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

        const userMessage: Message = {role: 'user', content: msg}
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
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({message: msg, conversationId: conversationIdRef.current}),
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

            // 先插入一条空的 assistant 消息，后续所有更新都改最后一条
            setMessages(prev => [...prev, {
                role: 'assistant',
                content: '',
                toolCalls: [],
                toolResults: [],
            }])

            // eventName 必须在 while 循环外面声明！
            // 因为 event: 和 data: 可能被拆在两个 TCP 包里到达。
            // 如果写在 for 循环里面，每次 read() 都会重置，导致类型丢失。
            let eventName = ''

            console.log('=== SSE 流已连接，开始接收后端数据 ===')

            while (true) {
                const {done, value} = await reader.read()
                if (done) {
                    console.log('SSE 流结束 (done)')
                    break
                }

                buffer += decoder.decode(value, {stream: true})

                const lines = buffer.split('\n')
                buffer = lines.pop() || ''

                for (const line of lines) {
                    const trimmed = line.trim()
                    if (!trimmed) continue

                    // 1. 收到事件名行 (event: chunk / confirmation_required 等)
                    if (trimmed.startsWith('event:')) {
                        eventName = trimmed.substring(6).trim()
                        console.log(`收到事件类型: ${eventName}`)
                        continue
                    }

                    // 2. 收到数据行 (data: ...)
                    if (!trimmed.startsWith('data:')) continue

                    const eventData = trimmed.substring(5).trimStart()

                    // 处理结束标记
                    if (eventData === '[DONE]') {
                        console.log('收到 [DONE]，本轮对话结束')
                        eventName = ''
                        continue
                    }

                    // 3. 工具调用事件（画 Timeline）
                    if (eventName === 'tool_call') {
                        try {
                            const tc = JSON.parse(eventData) as ToolCall
                            toolCallsRef.current = [...toolCallsRef.current, tc]
                            console.log('收到工具调用:', tc.name)
                        } catch { /* ignore parse error */
                        }
                        eventName = ''
                        continue
                    }

                    // 4. 工具结果事件
                    if (eventName === 'tool_result') {
                        try {
                            const tr = JSON.parse(eventData) as ToolResult
                            toolResultsRef.current = [...toolResultsRef.current, tr]
                            console.log('收到工具结果:', tr.name)
                        } catch { /* ignore parse error */
                        }
                        eventName = ''
                        continue
                    }

                    // 5. 敏感操作确认事件 → 弹窗
                    if (eventName === 'confirmation_required') {
                        try {
                            const confirm = JSON.parse(eventData)
                            console.log('收到确认请求，准备弹窗:', confirm.toolName)
                            setConfirmState({
                                toolName: confirm.toolName,
                                argsJson: confirm.argsJson,
                                message: confirm.message,
                            })
                        } catch { /* ignore parse error */
                        }
                        eventName = ''
                        continue
                    }

                    // 6. 默认文本内容（chunk）
                    // 注意：这里更新的是我们前面插入的那条空的 assistant 消息
                    if (eventName === '' || eventName === 'chunk') {
                        assistantContent += eventData
                        console.log('收到文字 chunk，当前累计长度:', assistantContent.length)
                    }
                    eventName = ''

                    // 7. 关键：更新 React state → 界面重新渲染
                    // 每次收到新内容或工具事件，都更新最后一条 assistant 消息
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
    // 敏感操作确认
    // ========================================================================

    const handleConfirm = async () => {
        if (!confirmState) return

        const {toolName, argsJson} = confirmState
        setConfirmState(null)
        setLoading(true)

        try {
            const response = await fetch(`${API_BASE}/api/chat/execute-confirmed`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({toolName, argsJson, conversationId: conversationIdRef.current}),
            })

            const data = await response.json()
            const resultText = data.content || '操作完成'

            // 添加确认结果到消息列表
            setMessages(prev => [...prev, {
                role: 'assistant',
                content: resultText,
                toolCalls: [{name: toolName, arguments: argsJson}],
                toolResults: [{name: toolName, result: resultText}],
            }])
        } catch (error) {
            const errMsg = error instanceof Error ? error.message : String(error)
            setMessages(prev => [...prev, {
                role: 'assistant',
                content: `操作失败: ${errMsg}`,
                isError: true,
            }])
        } finally {
            setLoading(false)
        }
    }

    const handleCancelConfirm = () => {
        setConfirmState(null)
        setMessages(prev => [...prev, {
            role: 'assistant',
            content: '操作已取消。',
        }])
    }

    // ========================================================================
    // 新对话 — 清服务端记忆 + 换 conversationId + 清空界面
    // ========================================================================

    const handleNewConversation = async () => {
        if (loading) {
            abortRef.current?.abort()
        }

        const oldId = conversationIdRef.current
        try {
            await fetch(`${API_BASE}/api/conversations/${oldId}`, {method: 'DELETE'})
        } catch {
            // 删库失败仍开本地新会话，避免卡在旧桌号
        }

        conversationIdRef.current = crypto.randomUUID()
        setMessages([])
        setConfirmState(null)
        setInput('')
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
                <button
                    type="button"
                    className="new-chat-btn"
                    onClick={handleNewConversation}
                    title="清空当前会话并开始新对话"
                >
                    新对话
                </button>
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
                    <div ref={messagesEndRef}/>
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
                                    <rect x="6" y="6" width="12" height="12" rx="2"/>
                                </svg>
                            </button>
                        ) : (
                            <button
                                className="send-btn"
                                onClick={() => sendMessage()}
                                disabled={!input.trim()}
                            >
                                <svg viewBox="0 0 24 24">
                                    <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
                                </svg>
                            </button>
                        )}
                    </div>
                    <div className="footer-hint">
                        {toolMode ? '🔧 工具模式 · AI 可调用本地工具' : 'LongCat-2.0 · 按 Enter 发送'}
                    </div>
                </div>
            </div>

            {/* 确认弹窗 */}
            {confirmState && (
                <div className="modal-overlay">
                    <div className="modal">
                        <div className="modal-header">⚠️ 敏感操作确认</div>
                        <div className="modal-body">
                            <p>{confirmState.message}</p>
                            <p className="modal-hint">此操作不可撤销，请确认是否执行。</p>
                        </div>
                        <div className="modal-actions">
                            <button className="modal-btn cancel" onClick={handleCancelConfirm}>取消</button>
                            <button className="modal-btn confirm" onClick={handleConfirm}>确认执行</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}

export default App
