import { NavLink, useNavigate } from 'react-router-dom';
import { Database, MessageSquare, PanelLeftClose, MoreVertical, Edit2, Trash2, FlaskConical } from 'lucide-react';
import { useState, useEffect } from 'react';
import toast from 'react-hot-toast';
import logo from '../assets/Cosmo42logo_128x128.jpg';
import { fetchChatList, renameChat, deleteChat } from '../api/client';
import type { ChatConversationListItemDTO, Page } from '../types/chat';
import Modal from './Modal';
import './Sidebar.css';
import { useFeatureFlag } from '../hooks/useFeatureFlag';

export function Sidebar() {
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);
  const [chats, setChats] = useState<ChatConversationListItemDTO[]>([]);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalContent, setModalContent] = useState<{ type: 'rename' | 'delete', chatId: string, currentTitle?: string } | null>(null);
  const [newTitle, setNewTitle] = useState('');
  const navigate = useNavigate();
  const isStudioEnabled = useFeatureFlag('studio');

  useEffect(() => {
    const handleClickOutside = () => setOpenMenuId(null);
    const handleChatCreated = (event: Event) => {
      const { uuid, title } = (event as CustomEvent).detail;
      setChats(prevChats => {
        if (prevChats.find(chat => chat.uuid === uuid)) {
          return prevChats;
        }
        return [{ uuid, title }, ...prevChats];
      });
    };

    document.addEventListener('click', handleClickOutside);
    window.addEventListener('chat-created', handleChatCreated);

    return () => {
      document.removeEventListener('click', handleClickOutside);
      window.removeEventListener('chat-created', handleChatCreated);
    };
  }, []);

  useEffect(() => {
    fetchChatList()
      .then((data: Page<ChatConversationListItemDTO>) => {
        if (data && data.content) {
          setChats(data.content);
        }
      })
      .catch((error) => {
        console.error('Failed to fetch chat list:', error);
        toast.error('Failed to load chats');
      });
  }, []);

  const toggleMenu = (e: React.MouseEvent, id: string) => {
    e.preventDefault();
    e.stopPropagation();
    setOpenMenuId(openMenuId === id ? null : id);
  };

  const openRenameModal = (chatId: string, currentTitle: string) => {
    setModalContent({ type: 'rename', chatId, currentTitle });
    setNewTitle(currentTitle);
    setIsModalOpen(true);
    setOpenMenuId(null);
  };

  const openDeleteModal = (chatId: string) => {
    setModalContent({ type: 'delete', chatId });
    setIsModalOpen(true);
    setOpenMenuId(null);
  };

  const handleConfirm = async () => {
    if (!modalContent) return;

    const { type, chatId } = modalContent;

    if (type === 'rename') {
      if (!newTitle || newTitle.trim() === '' || newTitle === modalContent.currentTitle) {
        setIsModalOpen(false);
        return;
      }
      try {
        const updatedChat = await renameChat(chatId, newTitle);
        setChats(chats.map(chat => chat.uuid === chatId ? updatedChat : chat));
        toast.success('Chat renamed');
      } catch (error) {
        console.error('Failed to rename chat:', error);
        toast.error('Failed to rename chat');
      }
    } else if (type === 'delete') {
      try {
        await deleteChat(chatId);
        setChats(chats.filter(chat => chat.uuid !== chatId));
        navigate('/chat');
        toast.success('Chat deleted');
      } catch (error) {
        console.error('Failed to delete chat:', error);
        toast.error('Failed to delete chat');
      }
    }
    setIsModalOpen(false);
    setModalContent(null);
  };

  return (
    <>
      <aside className={`sidebar ${isCollapsed ? 'collapsed' : ''}`}>
        <div className="sidebar-header">
          {isCollapsed ? (
            <button onClick={() => setIsCollapsed(false)} className="sidebar-logo-button" title="Expand Sidebar">
              <img src={logo} alt="Cosmo42 Logo" className="sidebar-logo" />
            </button>
          ) : (
            <img src={logo} alt="Cosmo42 Logo" className="sidebar-logo" />
          )}
          {!isCollapsed && (
            <button onClick={() => setIsCollapsed(true)} className="sidebar-toggle-top" title="Reduce Sidebar">
              <PanelLeftClose size={18} />
            </button>
          )}
        </div>

        <nav className="sidebar-nav">

          <div>
            <NavLink to="/chat" end className={({ isActive }) => `sidebar-nav-link ${isCollapsed ? 'justify-center' : ''} ${isActive ? 'active' : ''}`} title="New Chat">
              <MessageSquare size={18}/>
              {!isCollapsed && <span className="sidebar-nav-item-text">New Chat</span>}
            </NavLink>

            <NavLink to="/kb" className={({ isActive }) => `sidebar-nav-link ${isCollapsed ? 'justify-center' : ''} ${isActive ? 'active' : ''}`} title="Knowledge Base">
              <Database size={18}/>
              {!isCollapsed && <span className="sidebar-nav-item-text">Knowledge Base</span>}
            </NavLink>

            {isStudioEnabled && (
              <NavLink to="/studio" className={({ isActive }) => `sidebar-nav-link ${isCollapsed ? 'justify-center' : ''} ${isActive ? 'active' : ''}`} title="Studio">
                <FlaskConical size={18}/>
                {!isCollapsed && <span className="sidebar-nav-item-text">Studio</span>}
              </NavLink>
            )}
          </div>

          {!isCollapsed && (
            <div>
              <div className={`sidebar-section-header ${isCollapsed ? 'justify-center' : ''}`}>
                <h2 className="sidebar-section-title">Recents</h2>
              </div>
              <ul className="sidebar-nav-list">
                {chats.map(chat => (
                  <li key={chat.uuid} className="sidebar-nav-item-container">
                    <NavLink to={`/chat/${chat.uuid}`} className={({ isActive }) => `sidebar-nav-link ${isActive ? 'active' : ''}`} title={chat.title}>
                      <span className="sidebar-nav-item-text">{chat.title}</span>
                    </NavLink>

                    <div className="sidebar-item-actions">
                      <button 
                        onClick={(e) => toggleMenu(e, chat.uuid)} 
                        className="sidebar-action-btn"
                        title="Options"
                      >
                        <MoreVertical size={16} />
                      </button>
                      {openMenuId === chat.uuid && (
                        <div className="sidebar-item-menu" onClick={e => e.stopPropagation()}>
                          <button className="sidebar-menu-btn" onClick={() => openRenameModal(chat.uuid, chat.title)}>
                            <Edit2 size={14} /> Rename
                          </button>
                          <button className="sidebar-menu-btn delete" onClick={() => openDeleteModal(chat.uuid)}>
                            <Trash2 size={14} /> Delete
                          </button>
                        </div>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          )}

        </nav>
      </aside>
      {isModalOpen && modalContent && (
        <Modal
          isOpen={isModalOpen}
          onClose={() => setIsModalOpen(false)}
          title={modalContent.type === 'rename' ? 'Rename Chat' : 'Delete Chat'}
          onConfirm={handleConfirm}
          confirmText={modalContent.type === 'rename' ? 'Rename' : 'Delete'}
        >
          {modalContent.type === 'rename' ? (
            <input
              type="text"
              value={newTitle}
              onChange={(e) => setNewTitle(e.target.value)}
              className="modal-input"
            />
          ) : (
            <p>Are you sure you want to delete this chat?</p>
          )}
        </Modal>
      )}
    </>
  );
}