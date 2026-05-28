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

  describe('Chat Actions', () => {
    it('renames a chat successfully', async () => {
      const newTitle = 'Renamed Chat 1';
      renameChatSpy.mockResolvedValueOnce({ ...mockChats.content[0], title: newTitle });

      render(
        <MemoryRouter>
          <Sidebar />
        </MemoryRouter>
      );
      await waitFor(() => expect(screen.getByText('Chat 1')).toBeInTheDocument());

      const optionsButton = screen.getAllByTitle('Options')[0];
      fireEvent.click(optionsButton);

      const renameMenuButton = screen.getByText('Rename');
      fireEvent.click(renameMenuButton);

      // Verify modal is shown
      const input = screen.getByDisplayValue('Chat 1');
      fireEvent.change(input, { target: { value: newTitle } });

      const buttons = screen.getAllByText('Rename');
      // The modal confirm button will be the last one rendered with "Rename" text
      fireEvent.click(buttons[buttons.length - 1]);

      await waitFor(() => {
        expect(renameChatSpy).toHaveBeenCalledWith('1', newTitle);
        expect(toast.success).toHaveBeenCalledWith('Chat renamed');
        expect(screen.getByText(newTitle)).toBeInTheDocument();
        expect(screen.queryByText('Chat 1')).not.toBeInTheDocument();
      });
    });

    it('deletes a chat successfully', async () => {
      deleteChatSpy.mockResolvedValueOnce(null);

      render(
        <MemoryRouter>
          <Sidebar />
        </MemoryRouter>
      );
      await waitFor(() => expect(screen.getByText('Chat 1')).toBeInTheDocument());

      const optionsButton = screen.getAllByTitle('Options')[0];
      fireEvent.click(optionsButton);

      const deleteMenuButton = screen.getByText('Delete');
      fireEvent.click(deleteMenuButton);

      // Use modal's confirm button
      const buttons = screen.getAllByText('Delete');
      fireEvent.click(buttons[buttons.length - 1]);

      await waitFor(() => {
        expect(deleteChatSpy).toHaveBeenCalledWith('1');
        expect(toast.success).toHaveBeenCalledWith('Chat deleted');
        expect(screen.queryByText('Chat 1')).not.toBeInTheDocument();
      });
    });

    it('handles rename failure', async () => {
        const newTitle = 'Renamed Chat 1';
        renameChatSpy.mockRejectedValueOnce(new Error('Rename failed'));
  
        render(
          <MemoryRouter>
            <Sidebar />
          </MemoryRouter>
        );
        await waitFor(() => expect(screen.getByText('Chat 1')).toBeInTheDocument());
  
        const optionsButton = screen.getAllByTitle('Options')[0];
        fireEvent.click(optionsButton);
  
        const renameMenuButton = screen.getByText('Rename');
        fireEvent.click(renameMenuButton);
  
        // Change input
        const input = screen.getByDisplayValue('Chat 1');
        fireEvent.change(input, { target: { value: newTitle } });

        const buttons = screen.getAllByText('Rename');
        fireEvent.click(buttons[buttons.length - 1]);
  
        await waitFor(() => {
          expect(toast.error).toHaveBeenCalledWith('Failed to rename chat');
          expect(screen.getByText('Chat 1')).toBeInTheDocument(); // Title remains unchanged
        });
      });
  
      it('handles delete failure', async () => {
        deleteChatSpy.mockRejectedValueOnce(new Error('Delete failed'));
  
        render(
          <MemoryRouter>
            <Sidebar />
          </MemoryRouter>
        );
        await waitFor(() => expect(screen.getByText('Chat 1')).toBeInTheDocument());
  
        const optionsButton = screen.getAllByTitle('Options')[0];
        fireEvent.click(optionsButton);
  
        const deleteMenuButton = screen.getByText('Delete');
        fireEvent.click(deleteMenuButton);
  
        const buttons = screen.getAllByText('Delete');
        fireEvent.click(buttons[buttons.length - 1]);
  
        await waitFor(() => {
          expect(toast.error).toHaveBeenCalledWith('Failed to delete chat');
          expect(screen.getByText('Chat 1')).toBeInTheDocument(); // Chat remains in the list
        });
      });
  });
});