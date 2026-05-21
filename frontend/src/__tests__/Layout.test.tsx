import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Layout } from '../components/Layout';
import { vi } from 'vitest';

// Mock Toaster and Sidebar components to keep tests focused
vi.mock('react-hot-toast', () => ({
  Toaster: () => <div data-testid="toaster-mock" />,
}));

vi.mock('../components/Sidebar', () => ({
  Sidebar: () => <div data-testid="sidebar-mock" />,
}));

describe('Layout Component', () => {
  it('renders Sidebar, Toaster, and main content area (Outlet)', () => {
    render(
      <MemoryRouter>
        <Layout />
      </MemoryRouter>
    );

    expect(screen.getByTestId('toaster-mock')).toBeInTheDocument();
    expect(screen.getByTestId('sidebar-mock')).toBeInTheDocument();
    
    // The main element shouldn't be empty if Outlet renders something, 
    // but without nested routes, we just check if main tag exists
    const mainElement = document.querySelector('.main-content');
    expect(mainElement).toBeInTheDocument();
  });
});