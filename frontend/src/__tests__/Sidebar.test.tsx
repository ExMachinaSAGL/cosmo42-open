import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { Sidebar } from '../components/Sidebar';
import { vi, Mock } from 'vitest';
import toast from 'react-hot-toast';

vi.mock('react-hot-toast', () => ({
  default: {
    success: vi.fn(),
  },
}));

// Mock useNavigate
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

describe('Sidebar Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders sidebar with navigation links', () => {
    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>
    );

    expect(screen.getByTitle('Knowledge Base')).toBeInTheDocument();
    expect(screen.getByTitle('New Chat')).toBeInTheDocument();
    expect(screen.getByText('Progetto X...')).toBeInTheDocument();
  });

  it('toggles sidebar collapse state', () => {
    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>
    );

    const reduceButton = screen.getByTitle('Reduce Sidebar');
    fireEvent.click(reduceButton);

    const expandButton = screen.getByTitle('Expand Sidebar');
    expect(expandButton).toBeInTheDocument();
    expect(screen.queryByText('Progetto X...')).not.toBeInTheDocument(); // Chat list hidden when collapsed

    fireEvent.click(expandButton);
    expect(screen.getByText('Progetto X...')).toBeInTheDocument();
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
    expect(toast.success).toHaveBeenCalledWith('Started a new chat');
  });

  it('toggles chat options menu and handles actions', async () => {
    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>
    );

    const optionsButton = screen.getByTitle('Opzioni');
    
    // Menu is initially closed
    expect(screen.queryByText('Rename')).not.toBeInTheDocument();

    // Click to open
    fireEvent.click(optionsButton);
    expect(screen.getByText('Rename')).toBeInTheDocument();
    expect(screen.getByText('Delete')).toBeInTheDocument();

    // Click Rename
    fireEvent.click(screen.getByText('Rename'));
    expect(toast.success).toHaveBeenCalledWith('Chat renamed');
    expect(screen.queryByText('Rename')).not.toBeInTheDocument(); // Menu closes

    // Open again to test Delete
    fireEvent.click(optionsButton);
    fireEvent.click(screen.getByText('Delete'));
    expect(toast.success).toHaveBeenCalledWith('Chat deleted');
    expect(screen.queryByText('Delete')).not.toBeInTheDocument();
  });

  it('closes menu when clicking outside', () => {
    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>
    );

    const optionsButton = screen.getByTitle('Opzioni');
    fireEvent.click(optionsButton);
    expect(screen.getByText('Rename')).toBeInTheDocument();

    // Click outside
    fireEvent.click(document.body);
    expect(screen.queryByText('Rename')).not.toBeInTheDocument();
  });
});