import { UploadCloud, FileText, Download, Trash2, Loader2 } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { fetchDocuments, uploadDocument, deleteDocument, downloadDocument } from '../api/client';

interface Document {
  uuid: string;
  name: string;
}

export function KnowledgeBase() {
  const [documents, setDocuments] = useState<Document[]>([]);
  const [isUploading, setIsUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const loadDocuments = () => {
    fetchDocuments().then(setDocuments);
  };

  useEffect(() => {
    loadDocuments();
  }, []);

  const handleFileUpload = async (file: File) => {
    setIsUploading(true);
    const formData = new FormData();
    formData.append('file', file);
    try {
      await uploadDocument(formData);
      loadDocuments(); // Refresh the list
    } catch (error) {
      console.error("Error uploading file:", error);
    } finally {
      setIsUploading(false);
    }
  };

  const handleUploadClick = () => {
    if (!isUploading) {
      fileInputRef.current?.click();
    }
  };

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      handleFileUpload(file);
    }
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handleDragOver = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
  };

  const handleDrop = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    if (isUploading) return;
    const file = event.dataTransfer.files?.[0];
    if (file) {
      handleFileUpload(file);
    }
  };

  const handleDelete = async (uuid: string) => {
    try {
      await deleteDocument(uuid);
      loadDocuments();
    } catch (error) {
      console.error("Error deleting document:", error);
    }
  };

  const handleDownload = (uuid: string, fileName: string) => {
    downloadDocument(uuid, fileName);
  };

  return (
    <div className="kb-container">
      <div className="kb-content-wrapper">

        {/* Header */}
        <div className="kb-header-section">
          <h1 className="kb-title">Knowledge Base</h1>
          <p className="kb-subtitle">Manage the documents your AI will base its answers on.</p>
        </div>

        {/* Upload Zone */}
        <div
          className={`kb-upload-zone ${isUploading ? 'cursor-not-allowed opacity-75' : ''}`}
          onClick={handleUploadClick}
          onDragOver={handleDragOver}
          onDrop={handleDrop}
        >
          {isUploading ? (
            <div className="flex flex-col items-center justify-center">
              <Loader2 className="kb-upload-icon animate-spin" />
              <h3 className="kb-upload-title">Uploading...</h3>
            </div>
          ) : (
            <>
              <UploadCloud className="kb-upload-icon" />
              <h3 className="kb-upload-title">Click or drag your files here</h3>
              <p className="kb-upload-subtitle">Supports PDF (Max 50MB)</p>
            </>
          )}
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileChange}
            style={{ display: 'none' }}
            accept=".pdf"
            disabled={isUploading}
          />
        </div>

        {/* Documents Table */}
        <div className="kb-table-container">
          <table className="kb-table">
            <thead className="kb-table-header">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">File Name</th>
              <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider w-24" style={{ textAlign: 'right' }}>Actions</th>
            </tr>
            </thead>
            <tbody className="kb-table-body">
            {documents.map((doc) => (
              <tr key={doc.uuid} className="kb-table-row">
                <td className="kb-table-cell">
                  <div className="flex items-center">
                    <FileText className="kb-file-icon" />
                    <span className="kb-table-cell file-name">{doc.name}</span>
                  </div>
                </td>
                <td className="kb-table-cell kb-actions-cell">
                  <button 
                    className="kb-action-button" 
                    title="Download"
                    onClick={() => handleDownload(doc.uuid, doc.name)}
                  >
                    <Download className="kb-action-button-icon" />
                  </button>
                  <button 
                    className="kb-action-button delete" 
                    title="Delete"
                    onClick={() => handleDelete(doc.uuid)}
                  >
                    <Trash2 className="kb-action-button-icon" />
                  </button>
                </td>
              </tr>
            ))}
            </tbody>
          </table>
        </div>

      </div>
    </div>
  );
}