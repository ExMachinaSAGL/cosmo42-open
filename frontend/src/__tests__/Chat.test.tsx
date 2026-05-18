import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { vi, Mock } from 'vitest';
import { Chat } from '../pages/Chat';
import * as apiClient from '../api/client';
import toast from 'react-hot-toast';

// Mock the API client
vi.mock('../api/client', () => ({
  fetchChatHistory: vi.fn(),
  sendChatMessage: vi.fn(),
}));

// Mock react-hot-toast
vi.mock('react-hot-toast', () => ({
  default: {
    error: vi.fn(),
  },
}));

describe('Chat', () => {
  beforeEach(() => {
    // Reset mocks before each test
    vi.resetAllMocks();
    
    // Polyfill scrollIntoView since jsdom doesn't implement it
    window.HTMLElement.prototype.scrollIntoView = vi.fn();
  });

  it('renders the chat page for a new chat', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<Chat />} />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByText(/New Chat/i)).toBeInTheDocument();
    expect(screen.getByText(/Hello! I am your AI assistant./i)).toBeInTheDocument();
  });

  it('loads chat history for an existing chat', async () => {
    const mockHistory = {
      chatTitle: 'Test Chat',
      messages: [
        { source: 'user', content: 'Hello' },
        { source: 'ai', content: 'Hi there!' },
      ],
    };
    (apiClient.fetchChatHistory as Mock).mockResolvedValue(mockHistory);

    render(
      <MemoryRouter initialEntries={['/chat/123']}>
        <Routes>
          <Route path="/chat/:chatId" element={<Chat />} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText(/Loading.../i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Test Chat')).toBeInTheDocument();
    });

    expect(screen.getByText('Hello')).toBeInTheDocument();
    expect(screen.getByText('Hi there!')).toBeInTheDocument();
  });

  it('handles error when loading chat history fails', async () => {
    (apiClient.fetchChatHistory as Mock).mockRejectedValue(new Error('Failed to load'));

    render(
      <MemoryRouter initialEntries={['/chat/123']}>
        <Routes>
          <Route path="/chat/:chatId" element={<Chat />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith('Could not load history.');
    });
  });

  it('disables send button when input is empty', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<Chat />} />
        </Routes>
      </MemoryRouter>
    );

    const input = screen.getByPlaceholderText(/Write a message to the AI.../i);
    const sendButton = screen.getByRole('button', { name: /send/i });

    expect(sendButton).toBeDisabled();

    fireEvent.change(input, { target: { value: '   ' } });
    expect(sendButton).toBeDisabled();

    fireEvent.change(input, { target: { value: 'Test' } });
    expect(sendButton).not.toBeDisabled();
  });

  it('sends a message and displays it', async () => {
    (apiClient.sendChatMessage as Mock).mockResolvedValue({
      body: {
        getReader: () => {
          const stream = new ReadableStream({
            start(controller) {
              const encoder = new TextEncoder();
              const completedEvent = { type: 'COMPLETED' };
              const chunk = `data: ${JSON.stringify(completedEvent)}\n\n`;
              controller.enqueue(encoder.encode(chunk));
              controller.close();
            },
          });
          return stream.getReader();
        },
      },
    });

    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<Chat />} />
        </Routes>
      </MemoryRouter>
    );

    const input = screen.getByPlaceholderText(/Write a message to the AI.../i);
    const sendButton = screen.getByRole('button', { name: /send/i });

    fireEvent.change(input, { target: { value: 'Test message' } });
    fireEvent.click(sendButton);

    await waitFor(() => {
      expect(screen.getByText('Test message')).toBeInTheDocument();
    });

    // Check if the input is cleared after sending
    expect(input.value).toBe('');
  });

  it('handles API failure when sending a message', async () => {
    (apiClient.sendChatMessage as Mock).mockRejectedValue(new Error('API Error'));

    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<Chat />} />
        </Routes>
      </MemoryRouter>
    );

    const input = screen.getByPlaceholderText(/Write a message to the AI.../i);
    const sendButton = screen.getByRole('button', { name: /send/i });

    fireEvent.change(input, { target: { value: 'Test message' } });
    fireEvent.click(sendButton);

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith('Could not send the message.');
    });
  });

  it('receives and displays a streaming AI response', async () => {
    (apiClient.sendChatMessage as Mock).mockResolvedValue({
      body: {
        getReader: () => {
          const encoder = new TextEncoder();
          const stream = new ReadableStream({
            async start(controller) {
              const uuidEvent = { type: 'UUID', data: 'new-chat-uuid' };
              const titleEvent = { type: 'TITLE', data: 'New Title' };
              const statusEvent = { type: 'STATUS', data: 'Thinking' };
              const chunkEvent1 = { type: 'CHUNK', data: 'Hello ' };
              const chunkEvent2 = { type: 'CHUNK', data: 'World' };
              const completedEvent = { type: 'COMPLETED' };

              controller.enqueue(encoder.encode(`data: ${JSON.stringify(uuidEvent)}\n\n`));
              controller.enqueue(encoder.encode(`data: ${JSON.stringify(titleEvent)}\n\n`));
              controller.enqueue(encoder.encode(`data: ${JSON.stringify(statusEvent)}\n\n`));
              
              // Give React a small amount of time to render the status update
              await new Promise(resolve => setTimeout(resolve, 50));
              
              controller.enqueue(encoder.encode(`data: ${JSON.stringify(chunkEvent1)}\n\n`));
              controller.enqueue(encoder.encode(`data: ${JSON.stringify(chunkEvent2)}\n\n`));
              controller.enqueue(encoder.encode(`data: ${JSON.stringify(completedEvent)}\n\n`));
              controller.close();
            },
          });
          return stream.getReader();
        },
      },
    });

    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<Chat />} />
        </Routes>
      </MemoryRouter>
    );

    const input = screen.getByPlaceholderText(/Write a message to the AI.../i);
    const sendButton = screen.getByRole('button', { name: /send/i });

    fireEvent.change(input, { target: { value: 'User message' } });
    fireEvent.click(sendButton);

    await waitFor(() => {
      expect(screen.getByText('New Title')).toBeInTheDocument();
    });
    
    // The status text appears and is quickly replaced by the chunk content.
    // waitFor will catch it during a re-render.
    await waitFor(() => {
      expect(screen.getByText('Thinking...')).toBeInTheDocument();
    });

    // The final content should be visible.
    await waitFor(() => {
      expect(screen.getByText('Hello World')).toBeInTheDocument();
    });

    // By the end, the status text should be gone.
    await waitFor(() => {
      expect(screen.queryByText('Thinking...')).not.toBeInTheDocument();
    });
  });

  it('handles stream ERROR event correctly', async () => {
    (apiClient.sendChatMessage as Mock).mockResolvedValue({
      body: {
        getReader: () => {
          const encoder = new TextEncoder();
          const stream = new ReadableStream({
            start(controller) {
              const errorEvent = { type: 'ERROR', data: 'Something went wrong' };
              controller.enqueue(encoder.encode(`data: ${JSON.stringify(errorEvent)}\n\n`));
              controller.close();
            },
          });
          return stream.getReader();
        },
      },
    });

    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<Chat />} />
        </Routes>
      </MemoryRouter>
    );

    const input = screen.getByPlaceholderText(/Write a message to the AI.../i);
    const sendButton = screen.getByRole('button', { name: /send/i });

    fireEvent.change(input, { target: { value: 'Test message' } });
    fireEvent.click(sendButton);

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith('Error: Something went wrong');
    });
  });
});