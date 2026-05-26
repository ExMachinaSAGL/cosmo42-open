import { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { User, Bot } from 'lucide-react';
import toast from 'react-hot-toast';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { fetchChatHistory, sendChatMessage } from '../api/client';
import './Chat.css';
import { ChatInput } from './ChatInput';

type EventType = 'UUID' | 'TITLE' | 'STATUS' | 'CHUNK' | 'COMPLETED' | 'ERROR';

export interface ChatMessageItem {
  id?: string | number;
  chat_uuid?: string;
  role: 'user' | 'assistant';
  content?: string;
  status?: string;
}

interface StreamEvent {
  type: EventType;
  data?: string;
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

  // Refs for tracking navigation and fetching logic
  const justCreatedChatRef = useRef(false);
  const isNewChat = !chatId;

  const loadMessagesInit = useCallback(() => {
    const isNewChat = !chatId;
    if (isNewChat) {
      // Setup for a new chat
      setMessages([{ role: 'assistant', content: 'Hello! How can I help you today by looking at your documents?' }]);
      setChatTitle('New Chat');
      setCurrentChatUUID(null);
    }  else {
      // Load an existing chat
      setMessages([]);
      setChatTitle('Loading...');
      const loadHistory = async () => {
        try {
          const data = await fetchChatHistory(chatId);
          setChatTitle(data.title || 'Chat');
          setMessages(data.messages || []);
          setCurrentChatUUID(chatId);
        } catch (err) {
          toast.error('Could not load history.');
          console.error(err);
        }
      };
      loadHistory();
    }
}, [chatId]);


  // Refs to hold the latest state for use in callbacks without dependency issues
  const chatUUIDRef = useRef(currentChatUUID);
  useEffect(() => {
    chatUUIDRef.current = currentChatUUID;
  }, [currentChatUUID]);

  const chatTitleRef = useRef(chatTitle);
  useEffect(() => {
    chatTitleRef.current = chatTitle;
  }, [chatTitle]);

  // Main effect to handle route changes and data fetching
  useEffect(() => {
    // If a new chat was just created and we navigated here, skip fetching
    if (justCreatedChatRef.current) {
      justCreatedChatRef.current = false; // Reset the flag
      return;
    }

    loadMessagesInit(); 
    
  }, [chatId, isNewChat, loadMessagesInit]);

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
      if (lastMessage?.role === 'assistant') {
        const updatedMessage = { ...lastMessage, status: nextStatus };
        return [...prev.slice(0, -1), updatedMessage];
      }
      return prev;
    });

    
  }, []);

  useEffect(() => {
    statusTimerRef.current = setTimeout(() => {
      processNextStatus();
    }, 2000);
  }, [processNextStatus]);

  const addStatusToQueue = useCallback((status: string) => {
    statusQueueRef.current.push(status);
    if (!isStatusProcessingRef.current) {
      isStatusProcessingRef.current = true;
      processNextStatus();
    }
  }, [processNextStatus]);

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
          console.warn(e);
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
          if (lastMessage?.role !== 'assistant') {
            setTimeout(() => addStatusToQueue(event.data), 0);
            return [...prev, { role: 'assistant', content: '' }];
          } else {
             addStatusToQueue(event.data);
             return prev;
          }
        });
        break;

      case 'CHUNK':
        clearStatusQueue();
        setMessages(prev => {
          const lastMessage = prev[prev.length - 1];
          if (lastMessage?.role === 'assistant') {
            const updatedMessage = {
              ...lastMessage,
              content: (lastMessage.content || '') + event.data
            };
            delete updatedMessage.status;
            return [...prev.slice(0, -1), updatedMessage];
          } else {
            const newMessage: ChatMessageItem = {
              role: 'assistant',
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
          if (lastMessage?.role === 'assistant') {
            const finalMessage = { ...lastMessage };
            delete finalMessage.status; 
            return [...prev.slice(0, -1), finalMessage];
          }
          return prev; 
        });
        setIsStreaming(false);

        // Navigation logic correctly delayed until stream is complete
        if (isNewChat && chatUUIDRef.current && chatTitleRef.current) {
          const customEvent = new CustomEvent('chat-created', {
            detail: {
              uuid: chatUUIDRef.current,
              title: chatTitleRef.current,
            }
          });
          window.dispatchEvent(customEvent);
          
          // Set the flag to prevent fetch on navigate
          justCreatedChatRef.current = true;
          navigate(`/chat/${chatUUIDRef.current}`, { replace: true });
        }
        break;

      case 'ERROR':
        clearStatusQueue();
        setIsStreaming(false);
        toast.error("Error: " + event.data);
        break;
    }
  }, [addStatusToQueue, clearStatusQueue, isNewChat, navigate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputValue.trim() || isStreaming) return;

    const newMessage: ChatMessageItem = {
      ...(currentChatUUID && { chat_uuid: currentChatUUID }),
      role: 'user',
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
              <div key={msg.id || `msg-${index}`} className={`chat-message-item ${msg.role === 'user' ? 'user' : ''}`}>
                {/* Avatar */}
                <div className={`chat-avatar ${msg.role === 'user' ? 'user' : 'assistant'}`}>
                  {msg.role === 'user' ? <User size={18} /> : <Bot size={18} />}
                </div>

                {/* Message bubble */}
                <div className={`chat-message-bubble ${msg.role === 'user' ? 'user' : 'assistant'}`}>
                  {msg.status && <p className="chat-message-status" style={{ fontStyle: 'italic', fontSize: '0.8em', color: '#888', marginBottom: '4px' }}>{msg.status}...</p>}
                  {msg.content && (
                    <div className="chat-message-content">
                      {msg.role === 'assistant' ? (
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

      <ChatInput
        value={inputValue}
        onChange={setInputValue}
        onSubmit={handleSubmit}
        disabled={isStreaming}
      />

    </div>
  );
}
