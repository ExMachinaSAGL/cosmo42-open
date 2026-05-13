import { useState } from 'react';
import { Send, User, Bot } from 'lucide-react';

// Dati mockati per la conversazione
const MOCK_MESSAGES = [
  { id: '1', role: 'assistant', content: 'Ciao! Sono il tuo assistente IA. Come posso aiutarti oggi guardando i tuoi documenti?' },
  { id: '2', role: 'user', content: 'Puoi riassumermi le novità principali della Policy Aziendale 2026?' },
  { id: '3', role: 'assistant', content: 'Certamente! Basandomi sul documento "Policy_Aziendale_2026.docx", le novità principali riguardano:\n\n1. Lavoro ibrido esteso a 3 giorni a settimana.\n2. Nuovi budget per la formazione continua.\n3. Aggiornamento delle policy di rimborso spese.\n\nVuoi che approfondisca uno di questi punti?' },
];

export function Chat() {
  const [inputValue, setInputValue] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputValue.trim()) return;

    // Qui in futuro chiamerai la tua fetch in streaming
    console.log('Invio messaggio:', inputValue);
    setInputValue('');
  };

  return (
    <div className="chat-container">

      {/* Intestazione della chat */}
      <div className="chat-header">
        <h2 className="chat-header-title">Assistente Documentale</h2>
      </div>

      {/* Area dei messaggi (scrollabile) */}
      <div className="chat-messages-area">
        <div className="chat-messages-container">
          {MOCK_MESSAGES.map((msg) => (
            <div key={msg.id} className={`chat-message-item ${msg.role === 'user' ? 'user' : ''}`}>
              {/* Avatar */}
              <div className={`chat-avatar ${msg.role === 'user' ? 'user' : 'assistant'}`}>
                {msg.role === 'user' ? <User size={18} /> : <Bot size={18} />}
              </div>

              {/* Bolla del messaggio */}
              <div className={`chat-message-bubble ${msg.role === 'user' ? 'user' : 'assistant'}`}>
                <p className="chat-message-content">{msg.content}</p>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Area di Input ancorata in basso */}
      <div className="chat-input-area">
        <div className="chat-input-form-container">
          <form onSubmit={handleSubmit} className="chat-input-form">
            <input
              type="text"
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              placeholder="Scrivi un messaggio all'IA..."
              className="chat-input-field"
            />
            <button
              type="submit"
              disabled={!inputValue.trim()}
              className="chat-send-button"
            >
              <Send size={18} />
            </button>
          </form>
          <div className="chat-disclaimer-container">
            <span className="chat-disclaimer-text">L'IA può fare errori. Controlla sempre i dati importanti.</span>
          </div>
        </div>
      </div>

    </div>
  );
}