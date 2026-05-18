import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, Mock } from 'vitest';
import { KnowledgeBase } from '../pages/KnowledgeBase';
import * as apiClient from '../api/client';

// Mock the API client
vi.mock('../api/client', () => ({
  fetchDocuments: vi.fn(),
  uploadDocument: vi.fn(),
  deleteDocument: vi.fn(),
  downloadDocument: vi.fn(),
}));

describe('KnowledgeBase', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('renders and fetches documents on load', async () => {
    const mockDocuments = [
      { uuid: '1', name: 'document1.pdf' },
      { uuid: '2', name: 'document2.pdf' },
    ];
    (apiClient.fetchDocuments as Mock).mockResolvedValue(mockDocuments);

    render(<KnowledgeBase />);

    expect(screen.getByText('Knowledge Base')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      expect(screen.getByText('document2.pdf')).toBeInTheDocument();
    });
  });

  it('handles document upload via input click', async () => {
    (apiClient.fetchDocuments as Mock).mockResolvedValue([]);
    (apiClient.uploadDocument as Mock).mockResolvedValue({});

    render(<KnowledgeBase />);

    const uploadZone = screen.getByText(/Click or drag your files here/i);
    const file = new File(['dummy content'], 'test.pdf', { type: 'application/pdf' });
    
    // We can't easily simulate clicking the hidden input, so we trigger a change on it directly
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(input, 'files', {
      value: [file]
    });
    fireEvent.change(input);

    await waitFor(() => {
      expect(apiClient.uploadDocument).toHaveBeenCalled();
    });
    
    expect(apiClient.fetchDocuments).toHaveBeenCalledTimes(2); // Initial load + after upload
  });

  it('handles document upload via drop', async () => {
    (apiClient.fetchDocuments as Mock).mockResolvedValue([]);
    (apiClient.uploadDocument as Mock).mockResolvedValue({});

    render(<KnowledgeBase />);

    const uploadZone = screen.getByText(/Click or drag your files here/i).closest('.kb-upload-zone');
    const file = new File(['dummy content'], 'test.pdf', { type: 'application/pdf' });
    
    if (uploadZone) {
      fireEvent.drop(uploadZone, {
        dataTransfer: {
          files: [file],
        },
      });
    }

    await waitFor(() => {
      expect(apiClient.uploadDocument).toHaveBeenCalled();
    });
  });

  it('handles document deletion', async () => {
    const mockDocuments = [
      { uuid: '1', name: 'document1.pdf' },
    ];
    (apiClient.fetchDocuments as Mock).mockResolvedValue(mockDocuments);
    (apiClient.deleteDocument as Mock).mockResolvedValue({});

    render(<KnowledgeBase />);

    await waitFor(() => {
      expect(screen.getByText('document1.pdf')).toBeInTheDocument();
    });

    const deleteButton = screen.getByTitle('Delete');
    fireEvent.click(deleteButton);

    await waitFor(() => {
      expect(apiClient.deleteDocument).toHaveBeenCalledWith('1');
    });
    
    expect(apiClient.fetchDocuments).toHaveBeenCalledTimes(2); // Initial load + after delete
  });

  it('handles document download', async () => {
    const mockDocuments = [
      { uuid: '1', name: 'document1.pdf' },
    ];
    (apiClient.fetchDocuments as Mock).mockResolvedValue(mockDocuments);

    render(<KnowledgeBase />);

    await waitFor(() => {
      expect(screen.getByText('document1.pdf')).toBeInTheDocument();
    });

    const downloadButton = screen.getByTitle('Download');
    fireEvent.click(downloadButton);

    expect(apiClient.downloadDocument).toHaveBeenCalledWith('1', 'document1.pdf');
  });
});