import { UploadCloud, FileText, Download, Trash2 } from 'lucide-react';

// Dati mockati per i documenti
const MOCK_DOCUMENTS = [
  { id: '1', name: 'Manuale_Utente_v2.pdf', size: '2.4 MB', date: '2026-05-10', status: 'Indicizzato' },
  { id: '2', name: 'Policy_Aziendale_2026.docx', size: '1.1 MB', date: '2026-05-11', status: 'Indicizzato' },
  { id: '3', name: 'Report_Q1_Finanza.xlsx', size: '4.7 MB', date: '2026-05-12', status: 'In elaborazione...' },
];

export function KnowledgeBase() {
  return (
    <div className="kb-container">
      <div className="kb-content-wrapper">

        {/* Intestazione */}
        <div className="kb-header-section">
          <h1 className="kb-title">Knowledge Base</h1>
          <p className="kb-subtitle">Gestisci i documenti su cui l'IA baserà le sue risposte.</p>
        </div>

        {/* Zona di Upload */}
        <div className="kb-upload-zone">
          <UploadCloud className="kb-upload-icon" />
          <h3 className="kb-upload-title">Clicca o trascina qui i tuoi file</h3>
          <p className="kb-upload-subtitle">Supporta PDF, DOCX, TXT e CSV (Max 50MB)</p>
        </div>

        {/* Tabella Documenti */}
        <div className="kb-table-container">
          <table className="kb-table">
            <thead className="kb-table-header">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Nome File</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Data</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Dimensione</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Stato</th>
              <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Azioni</th>
            </tr>
            </thead>
            <tbody className="kb-table-body">
            {MOCK_DOCUMENTS.map((doc) => (
              <tr key={doc.id} className="kb-table-row">
                <td className="kb-table-cell">
                  <div className="flex items-center">
                    <FileText className="kb-file-icon" />
                    <span className="kb-table-cell file-name">{doc.name}</span>
                  </div>
                </td>
                <td className="kb-table-cell">{doc.date}</td>
                <td className="kb-table-cell">{doc.size}</td>
                <td className="kb-table-cell">
                    <span className={`kb-status-badge ${
                      doc.status === 'Indicizzato' ? 'indexed' : 'processing'
                    }`}>
                      {doc.status}
                    </span>
                </td>
                <td className="kb-table-cell kb-actions-cell">
                  <button className="kb-action-button" title="Scarica">
                    <Download className="kb-action-button-icon" />
                  </button>
                  <button className="kb-action-button delete" title="Elimina">
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