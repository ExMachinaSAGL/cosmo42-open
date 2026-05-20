import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Sidebar } from '../components/Sidebar';
import { vi } from 'vitest';
import toast from 'react-hot-toast';
import * as apiClient from '../api/client';

// Mocks
vi.mock('react-hot-toast', () => ({
  default: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

const mockChats = {
  content: [
    { uuid: '1', title: 'Chat 1', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
    { uuid: '2', title: 'Chat 2', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
  ],
};

const fetchChatListSpy = vi.spyOn(apiClient, 'fetchChatList');
const renameChatSpy = vi.spyOn(apiClient, 'renameChat');
const deleteChatSpy = vi.spyOn(apiClient, 'deleteChat');

describe('Sidebar Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fetchChatListSpy.mockResolvedValue(mockChats);
    window.prompt = vi.fn();
    window.confirm = vi.fn();
  });

  it('renders sidebar and loads chats', async () => {
    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>
    );

    expect(fetchChatListSpy).toHaveBeenCalledTimes(1);
    await waitFor(() => {
      expect(screen.getByText('Chat 1')).toBeInTheDocument();
      expect(screen.getByText('Chat 2')).toBeInTheDocument();
    });
  });

  it('handles error when loading chats', async () => {
    fetchChatListSpy.mockRejectedValueOnce(new Error('Failed to fetch'));
    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith('Failed to load chats');
    });
  });

  it('toggles sidebar collapse state', async () => {
    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>
    );
    await waitFor(() => expect(screen.getByText('Chat 1')).toBeInTheDocument());

    const reduceButton = screen.getByTitle('Reduce Sidebar');
    fireEvent.click(reduceButton);

    expect(screen.queryByText('Chat 1')).not.toBeInTheDocument();

    const expandButton = screen.getByTitle('Expand Sidebar');
    fireEvent.click(expandButton);
    expect(screen.getByText('Chat 1')).toBeInTheDocument();
  });

  it('navigates to home and shows toast when New Chat is clicked', () => {
    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>
    );

    const newChatButton = screen.getByTitle('New Chat');
    fireEvent.click(newChatButton);

    expect(mockNavigate).toHaveBeenCalledWith('/');
  });

  describe('Chat Actions', () => {
    it('renames a chat successfully', async () => {
      const newTitle = 'Renamed Chat 1';
      (window.prompt as vi.Mock).mockReturnValue(newTitle);
      renameChatSpy.mockResolvedValueOnce({ ...mockChats.content[0], title: newTitle });

      render(
        <MemoryRouter>
          <Sidebar />
        </MemoryRouter>
      );
      await waitFor(() => expect(screen.getByText('Chat 1')).toBeInTheDocument());

      const optionsButton = screen.getAllByTitle('Options')[0];
      fireEvent.click(optionsButton);

      const renameButton = screen.getByText('Rename');
      fireEvent.click(renameButton);

      expect(window.prompt).toHaveBeenCalledWith('Enter new chat title:', 'Chat 1');
      await waitFor(() => {
        expect(renameChatSpy).toHaveBeenCalledWith('1', newTitle);
        expect(toast.success).toHaveBeenCalledWith('Chat renamed');
        expect(screen.getByText(newTitle)).toBeInTheDocument();
        expect(screen.queryByText('Chat 1')).not.toBeInTheDocument();
      });
    });

    it('deletes a chat successfully', async () => {
      (window.confirm as vi.Mock).mockReturnValue(true);
      deleteChatSpy.mockResolvedValueOnce(null);

      render(
        <MemoryRouter>
          <Sidebar />
        </MemoryRouter>
      );
      await waitFor(() => expect(screen.getByText('Chat 1')).toBeInTheDocument());

      const optionsButton = screen.getAllByTitle('Opzioni')[0];
      fireEvent.click(optionsButton);

      const deleteButton = screen.getByText('Delete');
      fireEvent.click(deleteButton);

      expect(window.confirm).toHaveBeenCalledWith('Are you sure you want to delete this chat?');
      await waitFor(() => {
        expect(deleteChatSpy).toHaveBeenCalledWith('1');
        expect(toast.success).toHaveBeenCalledWith('Chat deleted');
        expect(screen.queryByText('Chat 1')).not.toBeInTheDocument();
      });
    });

    it('handles rename failure', async () => {
        const newTitle = 'Renamed Chat 1';
        (window.prompt as vi.Mock).mockReturnValue(newTitle);
        renameChatSpy.mockRejectedValueOnce(new Error('Rename failed'));
  
        render(
          <MemoryRouter>
            <Sidebar />
          </MemoryRouter>
        );
        await waitFor(() => expect(screen.getByText('Chat 1')).toBeInTheDocument());
  
        const optionsButton = screen.getAllByTitle('Opzioni')[0];
        fireEvent.click(optionsButton);
  
        const renameButton = screen.getByText('Rename');
        fireEvent.click(renameButton);
  
        await waitFor(() => {
          expect(toast.error).toHaveBeenCalledWith('Failed to rename chat');
          expect(screen.getByText('Chat 1')).toBeInTheDocument(); // Title remains unchanged
        });
      });
  
      it('handles delete failure', async () => {
        (window.confirm as vi.Mock).mockReturnValue(true);
        deleteChatSpy.mockRejectedValueOnce(new Error('Delete failed'));
  
        render(
          <MemoryRouter>
            <Sidebar />
          </MemoryRouter>
        );
        await waitFor(() => expect(screen.getByText('Chat 1')).toBeInTheDocument());
  
        const optionsButton = screen.getAllByTitle('Opzioni')[0];
        fireEvent.click(optionsButton);
  
        const deleteButton = screen.getByText('Delete');
        fireEvent.click(deleteButton);
  
        await waitFor(() => {
          expect(toast.error).toHaveBeenCalledWith('Failed to delete chat');
          expect(screen.getByText('Chat 1')).toBeInTheDocument(); // Chat remains in the list
        });
      });
  });
});