import { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Send, User, Bot, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { fetchChatHistory, sendChatMessage } from '../api/client';

type EventType = 'UUID' | 'TITLE' | 'STATUS' | 'CHUNK' | 'COMPLETED' | 'ERROR';

export interface ChatMessageItem {
  id?: string | number;
  chat_uuid?: string;
  source: 'user' | 'ai';
  content?: string;
  status?: string;
}

interface StreamEvent {
  type: EventType;
  data?: any;
}

export function Chat() {
  const { chatId } = useParams();
  const navigate = useNavigate();
  const [inputValue, setInputValue] = useState('');
  
  const [currentChatUUID, setCurrentChatUUID] = useState<string | null>(null);
  const [isStreaming, setIsStreaming] = useState(false);
  const [messages, setMessages] = useState<ChatMessageItem[]>([]);
  const [chatTitle, setChatTitle] = useState<string | null>(null);
  const messagesEndRef = useRef<null | HTMLDivElement>(null);

  // Status queue management
  const statusQueueRef = useRef<string[]>([]);
  const statusTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const currentStatusIndexRef = useRef<number>(-1);
  const isStatusProcessingRef = useRef<boolean>(false);

  const isNewChatRef = useRef(!chatId);
  const eventDispatchedRef = useRef(false);
  const lastLoadedChatId = useRef<string | null>(null);

  useEffect(() => {
    isNewChatRef.current = !chatId;
    eventDispatchedRef.current = false;
  }, [chatId]);

  useEffect(() => {
    if (isNewChatRef.current && !eventDispatchedRef.current && currentChatUUID && chatTitle && chatTitle !== 'New Chat' && chatTitle !== 'Loading...') {
      const event = new CustomEvent('chat-created', {
        detail: {
          uuid: currentChatUUID,
          title: chatTitle,
        }
      });
      window.dispatchEvent(event);
      eventDispatchedRef.current = true;
      lastLoadedChatId.current = currentChatUUID;
      navigate(`/chat/${currentChatUUID}`, { replace: true });
    }
  }, [currentChatUUID, chatTitle, navigate]);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  // Cleanup timer on unmount
  useEffect(() => {
    return () => {
      if (statusTimerRef.current) {
        clearTimeout(statusTimerRef.current);
      }
    };
  }, []);

  const clearStatusQueue = useCallback(() => {
    statusQueueRef.current = [];
    currentStatusIndexRef.current = -1;
    isStatusProcessingRef.current = false;
    if (statusTimerRef.current) {
      clearTimeout(statusTimerRef.current);
      statusTimerRef.current = null;
    }
  }, []);

  const processNextStatus = useCallback(() => {
    const queue = statusQueueRef.current;
    const currentIndex = currentStatusIndexRef.current;

    if (currentIndex + 1 >= queue.length) {
      isStatusProcessingRef.current = false;
      return;
    }

    currentStatusIndexRef.current = currentIndex + 1;
    const nextStatus = queue[currentStatusIndexRef.current];

    setMessages(prev => {
      const lastMessage = prev[prev.length - 1];
      if (lastMessage?.source === 'ai') {
        const updatedMessage = { ...lastMessage, status: nextStatus };
        return [...prev.slice(0, -1), updatedMessage];
      }
      return prev;
    });

    statusTimerRef.current = setTimeout(() => {
      processNextStatus();
    }, 2000);
  }, []);

  const addStatusToQueue = useCallback((status: string) => {
    statusQueueRef.current.push(status);
    if (!isStatusProcessingRef.current) {
      isStatusProcessingRef.current = true;
      
      // Immediately process the first status
      processNextStatus();
    }
  }, [processNextStatus]);

  useEffect(() => {
    if (chatId) {
      if (lastLoadedChatId.current === chatId) {
        return;
      }
      lastLoadedChatId.current = chatId;
      setCurrentChatUUID(chatId);
      setChatTitle("Loading...");
      const loadHistory = async () => {
        try {
          const data = await fetchChatHistory(chatId);
          setChatTitle(data.chatTitle || "Chat");
          setMessages(data.messages || []);
        } catch (err) {
          toast.error('Could not load history.');
          console.error(err);
        }
      };
      loadHistory();
    } else {
      lastLoadedChatId.current = null;
      setCurrentChatUUID(null);
      setChatTitle("New Chat");
      setMessages([{ source: 'ai', content: 'Hello! I am your AI assistant. How can I help you today by looking at your documents?' }]);
    }
  }, [chatId]);

  const parseSSEChunk = (chunk: string): StreamEvent[] => {
    const events: StreamEvent[] = [];
    const lines = chunk.split('\n');
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();
      if (line.startsWith('data:')) {
        const dataContent = line.replace(/^data:\s*/, '');
        if (dataContent.trim()) {
          try {
            const event: StreamEvent = JSON.parse(dataContent);
            events.push(event);
          } catch (e) {
            console.error('Error parsing JSON:', e, 'Content:', dataContent);
          }
        }
      } else if (line.startsWith('{')) {
        // Fallback in case the raw JSON is streamed without data: prefix
        try {
          const event: StreamEvent = JSON.parse(line);
          if (event.type) {
            events.push(event);
          }
        } catch (e) {
          // ignore parsing error for non-JSON lines
        }
      }
    }
    return events;
  };

  const handleStreamEvent = useCallback((event: StreamEvent) => {
    switch (event.type) {
      case 'UUID':
        if (event.data) {
          setCurrentChatUUID(event.data);
        }
        break;

      case 'TITLE':
        if (event.data) {
          setChatTitle(event.data);
        }
        break;

      case 'STATUS':
        setMessages(prev => {
          const lastMessage = prev[prev.length - 1];
          // If we haven't created the AI message yet, create it with the status
          if (lastMessage?.source !== 'ai') {
            const newMessage: ChatMessageItem = {
              source: 'ai',
              status: event.data,
              content: '',
            };
            
            // Wait, we need to push to queue even for the first message, 
            // so we add it to queue and let processNextStatus update it.
            // But if we just created the message, it won't be updated by processNextStatus immediately 
            // because processNextStatus works on the *previous* state.
            // So we add the initial status to the queue and it will be processed.
            setTimeout(() => addStatusToQueue(event.data), 0);
            return [...prev, { source: 'ai', content: '' }];
          } else {
             // Ai message already exists
             addStatusToQueue(event.data);
             return prev;
          }
        });
        break;

      case 'CHUNK':
        clearStatusQueue();
        setMessages(prev => {
          const lastMessage = prev[prev.length - 1];
          if (lastMessage?.source === 'ai') {
            const updatedMessage = {
              ...lastMessage,
              content: (lastMessage.content || '') + event.data
            };
            delete updatedMessage.status;
            return [...prev.slice(0, -1), updatedMessage];
          } else {
            const newMessage: ChatMessageItem = {
              source: 'ai',
              content: event.data,
            };
            return [...prev, newMessage];
          }
        });
        break;

      case 'COMPLETED':
        clearStatusQueue();
        setMessages(prev => {
          const lastMessage = prev[prev.length - 1];
          if (lastMessage?.source === 'ai') {
            const finalMessage = { ...lastMessage };
            delete finalMessage.status; 
            return [...prev.slice(0, -1), finalMessage];
          }
          return prev; 
        });
        setIsStreaming(false);
        break;

      case 'ERROR':
        clearStatusQueue();
        setIsStreaming(false);
        toast.error("Error: " + event.data);
        break;
    }
  }, [addStatusToQueue, clearStatusQueue]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputValue.trim() || isStreaming) return;

    const newMessage: ChatMessageItem = {
      ...(currentChatUUID && { chat_uuid: currentChatUUID }),
      source: 'user',
      content: inputValue
    };
    setMessages(prevMessages => [...prevMessages, newMessage]);
    setInputValue('');
    setIsStreaming(true);
    try {
      const response = await sendChatMessage({
        uuid: newMessage.chat_uuid,
        message: newMessage.content,
      });
      const reader = response.body?.getReader();
      if (!reader) throw new Error('No reader available');

      const decoder = new TextDecoder();
      let buffer = '';
      try {
        while (true) {
          const { done, value } = await reader.read();

          if (value) {
            buffer += decoder.decode(value, { stream: true });
          }
          const parts = buffer.split('\n\n');
          buffer = parts.pop() || '';

          for (const part of parts) {
            if (part.trim()) {
              const events = parseSSEChunk(part + '\n\n');
              events.forEach(event => handleStreamEvent(event));
            }
          }

          if (done) {
            if (buffer.trim()) {
               const events = parseSSEChunk(buffer);
               events.forEach(event => handleStreamEvent(event));
            }
            break;
          }
        }
      } catch (error) {
        console.error('Error during streaming:', error);
        toast.error('Streaming error: ' + (error as Error).message);
      } finally {
        reader.releaseLock();
      }
    } catch (err) {
      toast.error('Could not send the message.');
      console.log(err);
    } finally {
      setIsStreaming(false);
    }
  };

  return (
    <div className="chat-container">

      {/* Chat header */}
      <div className="chat-header">
        <h2 className="chat-header-title">
          {chatTitle || 'Loading...'}
        </h2>
      </div>

      {/* Messages area (scrollable) */}
      <div className="chat-messages-area">
        <div className="chat-messages-container">
          {messages.length === 0 ? (
            <div className="chat-empty-state" style={{ textAlign: 'center', marginTop: '40px', color: '#666' }}>
              <p>Start a new conversation by typing a message below.</p>
            </div>
          ) : (
            messages.map((msg, index) => (
              <div key={msg.id || `msg-${index}`} className={`chat-message-item ${msg.source === 'user' ? 'user' : ''}`}>
                {/* Avatar */}
                <div className={`chat-avatar ${msg.source === 'user' ? 'user' : 'assistant'}`}>
                  {msg.source === 'user' ? <User size={18} /> : <Bot size={18} />}
                </div>

                {/* Message bubble */}
                <div className={`chat-message-bubble ${msg.source === 'user' ? 'user' : 'assistant'}`}>
                  {msg.status && <p className="chat-message-status" style={{ fontStyle: 'italic', fontSize: '0.8em', color: '#888', marginBottom: '4px' }}>{msg.status}...</p>}
                  {msg.content && (
                    <div className="chat-message-content">
                      {msg.source === 'ai' ? (
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>
                          {msg.content}
                        </ReactMarkdown>
                      ) : (
                        <p style={{ whiteSpace: 'pre-wrap' }}>{msg.content}</p>
                      )}
                    </div>
                  )}
                </div>
              </div>
            ))
          )}
          <div ref={messagesEndRef} />
        </div>
      </div>

      {/* Input Area anchored to the bottom */}
      <div className="chat-input-area">
        <div className="chat-input-form-container">
          <form onSubmit={handleSubmit} className="chat-input-form">
            <input
              type="text"
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              placeholder="Write a message to the AI..."
              className="chat-input-field"
              disabled={isStreaming}
            />
            <button
              type="submit"
              disabled={!inputValue.trim() || isStreaming}
              className="chat-send-button"
              aria-label="Send"
            >
              {isStreaming ? <Loader2 size={18} className="animate-spin" /> : <Send size={18} />}
            </button>
          </form>
          <div className="chat-disclaimer-container">
            <span className="chat-disclaimer-text">AI can make mistakes. Always check important data.</span>
          </div>
        </div>
      </div>

    </div>
  );
}