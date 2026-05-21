import { Send, Loader2 } from 'lucide-react';

interface ChatInputProps {
  value: string;
  onChange: (value: string) => void;
  onSubmit: (e: React.FormEvent) => void;
  disabled: boolean;
}

export function ChatInput({ value, onChange, onSubmit, disabled }: ChatInputProps) {
  return (
    <div className="chat-input-area">
      <div className="chat-input-form-container">
        <form onSubmit={onSubmit} className="chat-input-form flex items-center">
          <input
            type="text"
            value={value}
            onChange={(e) => onChange(e.target.value)}
            placeholder="Write a message to the AI..."
            className="chat-input-field flex-grow"
            disabled={disabled}
          />
          <button
            type="submit"
            disabled={!value.trim() || disabled}
            className="chat-send-button"
            aria-label="Send"
          >
            {disabled ? <Loader2 size={18} className="animate-spin" /> : <Send size={18} />}
          </button>
        </form>
        <div className="chat-disclaimer-container">
          <span className="chat-disclaimer-text">AI can make mistakes. Always check important data.</span>
        </div>
      </div>
    </div>
  );
}
