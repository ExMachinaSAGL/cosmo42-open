const BASE_URL = 'http://localhost:8080/api/v1/kb';

async function apiFetch(endpoint: string, options: RequestInit = {}) {
    const response = await fetch(`${BASE_URL}${endpoint}`, {
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

// TODO

export const fetchDocuments = () => apiFetch('/documents');
export const deleteDocument = (id: string) => apiFetch(`/documents/${id}`, { method: 'DELETE' });

export const downloadDocument = (id: string, fileName: string) => {
    fetch(`${BASE_URL}/documents/${id}/download`)
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

// Nota: per l'upload di file (FormData), il Content-Type NON deve essere impostato a application/json,
// il browser deve impostarlo automaticamente per includere il boundary.
export const uploadDocument = (data: FormData) => fetch(`${BASE_URL}/documents`, {
    method: 'POST',
    body: data
}).then(res => res.json());