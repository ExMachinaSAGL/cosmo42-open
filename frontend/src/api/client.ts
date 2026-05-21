const API_BASE_URL = 'http://localhost:8080/api/v1';
const KB_BASE_URL = `${API_BASE_URL}/kb`;
const CHAT_BASE_URL = `${API_BASE_URL}/chat`;

async function apiFetch(baseUrl: string, endpoint: string, options: RequestInit = {}) {
    const response = await fetch(`${baseUrl}${endpoint}`, {
        ...options,
        headers: {
            'Content-Type': 'application/json',
            ...options.headers,
        },
    });
    if (!response.ok) {
        throw new Error(`API Error: ${response.status}`);
    }
    if (response.status === 204) return null;
    return response.json();
}

// Knowledge Base API
export const fetchDocuments = () => apiFetch(KB_BASE_URL, '/documents');
export const deleteDocument = (id: string) => apiFetch(KB_BASE_URL, `/documents/${id}`, { method: 'DELETE' });

export const downloadDocument = (id: string, fileName: string) => {
    fetch(`${KB_BASE_URL}/documents/${id}/download`)
        .then(response => {
            if (!response.ok) throw new Error("Failed to download file");
            return response.blob();
        })
        .then(blob => {
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.style.display = 'none';
            a.href = url;
            a.download = fileName;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);
        })
        .catch(err => console.error("Error downloading file", err));
};

export const uploadDocument = (data: FormData) => fetch(`${KB_BASE_URL}/documents`, {
    method: 'POST',
    body: data
}).then(res => res.json());

// Chat API
export const fetchChatHistory = (chatId: string) => apiFetch(CHAT_BASE_URL, `/${chatId}`);

export const sendChatMessage = (messageData: any) => fetch(`${CHAT_BASE_URL}/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(messageData)
});

export const fetchChatList = () => apiFetch(CHAT_BASE_URL, '');

export const renameChat = (chatId: string, title: string) => apiFetch(CHAT_BASE_URL, `/${chatId}/title`, {
    method: 'PATCH',
    body: JSON.stringify({ title })
});

export const deleteChat = (chatId: string) => apiFetch(CHAT_BASE_URL, `/${chatId}`, {
    method: 'DELETE'
});
