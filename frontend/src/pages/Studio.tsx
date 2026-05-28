import React, { useState } from 'react';
import { Paperclip, Send, Loader2 } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { runStudioExperiment } from '../api/client';
import './Studio.css';

type OutputFormat = 'raw' | 'markdown' | 'json';

export function Studio() {
  const [prompt, setPrompt] = useState('');
  const [attachments, setAttachments] = useState<File[]>([]);
  const [response, setResponse] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [outputFormat, setOutputFormat] = useState<OutputFormat>('raw');

  const handlePromptChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setPrompt(e.target.value);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      const newFiles = Array.from(e.target.files);
      setAttachments(prev => [...prev, ...newFiles]);
      e.target.value = '';
    }
  };

  const handleRemoveAttachment = (indexToRemove: number) => {
    setAttachments(prev => prev.filter((_, index) => index !== indexToRemove));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!prompt.trim() && attachments.length === 0) return;

    setIsLoading(true);
    setResponse(null);

    const formData = new FormData();
    if (prompt.trim()) {
      formData.append('prompt', prompt);
    }
    if (attachments.length > 0) {
        attachments.forEach(file => {
            formData.append('attachments', file);
        });
    }

    try {
      const result = await runStudioExperiment(formData);
      setResponse(result);
    } catch (error) {
      console.error("Error calling studio API:", error);
      setResponse(`Error: Could not generate response. ${error instanceof Error ? error.message : ''}`);
    } finally {
      setIsLoading(false);
    }
  };

  const renderOutput = () => {
    if (!response) return null;

    switch (outputFormat) {
      case 'markdown':
        return <ReactMarkdown remarkPlugins={[remarkGfm]}>{response}</ReactMarkdown>;
      case 'json':
        try {
          const parsedJson = JSON.parse(response);
          return <pre><code>{JSON.stringify(parsedJson, null, 2)}</code></pre>;
        } catch {
          return <p className="error-text">Failed to parse JSON: The response is not valid JSON.</p>;
        }
      case 'raw':
      default:
        return <p className="response-text">{response}</p>;
    }
  };

  return (
    <div className="studio-container">
      <div className="studio-header">
        <h1>Studio</h1>
        <p>Experiment with custom prompts and attachments.</p>
      </div>

      <div className="studio-content">
        <div className="studio-workbench">
          <form onSubmit={handleSubmit} className="studio-form">
            <div className="prompt-container">
              <label htmlFor="prompt-input">Instructions</label>
              <textarea
                id="prompt-input"
                className="studio-textarea"
                value={prompt}
                onChange={handlePromptChange}
                placeholder="Enter your custom instructions or prompt here..."
              />
            </div>

            <div className="studio-controls">
              <div className="attachment-control">
                <input
                  type="file"
                  id="file-upload"
                  multiple
                  onChange={handleFileChange}
                  className="file-input-hidden"
                />
                <label htmlFor="file-upload" className="attachment-btn">
                  <Paperclip size={18} />
                  <span>Attach Files {attachments.length > 0 && `(${attachments.length})`}</span>
                </label>
                {attachments.length > 0 && (
                  <div className="attachment-list">
                    {attachments.map((file, index) => (
                      <div key={index} className="attachment-item">
                        <span>{file.name}</span>
                        <button 
                          type="button" 
                          className="remove-attachment-btn"
                          onClick={() => handleRemoveAttachment(index)}
                        >
                          &times;
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <button 
                type="submit" 
                className="studio-submit-btn"
                disabled={isLoading || (!prompt.trim() && attachments.length === 0)}
              >
                {isLoading ? (
                  <>
                    <Loader2 size={18} className="animate-spin" />
                    <span>Running...</span>
                  </>
                ) : (
                  <>
                    <Send size={18} />
                    <span>Run Experiment</span>
                  </>
                )}
              </button>
            </div>
          </form>
        </div>

        <div className="studio-results">
          <div className="studio-results-header">
            <div className="output-format-selector">
              {(['raw', 'markdown', 'json'] as OutputFormat[]).map(format => (
                <button
                  key={format}
                  className={`output-format-btn ${outputFormat === format ? 'active' : ''}`}
                  onClick={() => setOutputFormat(format)}
                >
                  {format.charAt(0).toUpperCase() + format.slice(1)}
                </button>
              ))}
            </div>
          </div>
          <div className={`result-box ${isLoading ? 'loading' : ''} ${!response && !isLoading ? 'empty' : ''}`}>
            {isLoading ? (
              <p className="loading-text">Generating output...</p>
            ) : response ? (
              renderOutput()
            ) : (
              <p className="empty-text">Run an experiment to see the output here.</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
