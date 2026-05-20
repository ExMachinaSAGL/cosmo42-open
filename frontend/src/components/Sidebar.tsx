import { NavLink, useNavigate } from 'react-router-dom';
import { Database, MessageSquare, Plus, PanelLeftClose, MoreVertical, Edit2, Trash2 } from 'lucide-react';
import { useState, useEffect } from 'react';
import toast from 'react-hot-toast';
import logo from '../assets/Cosmo42logo_128x128.jpg';
import { fetchChatList, renameChat, deleteChat } from '../api/client';
import type { ChatConversationListItemDTO, Page } from '../types/chat';

export function Sidebar() {
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);
  const [chats, setChats] = useState<ChatConversationListItemDTO[]>([]);
  const navigate = useNavigate();

  useEffect(() => {
    const handleClickOutside = () => setOpenMenuId(null);
    document.addEventListener('click', handleClickOutside);
    return () => document.removeEventListener('click', handleClickOutside);
  }, []);

  useEffect(() => {
    const handleChatCreated = (event: Event) => {
      const { uuid, title } = (event as CustomEvent).detail;
      setChats(prevChats => [{ uuid, title }, ...prevChats]);
    };

    window.addEventListener('chat-created', handleChatCreated);

    return () => {
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

  const handleNewChat = () => {
    navigate('/');
    toast.success('Started a new chat');
  };

  const handleRenameChat = async (chatId: string, currentTitle: string) => {
    setOpenMenuId(null);
    const newTitle = window.prompt('Enter new chat title:', currentTitle);
    if (!newTitle || newTitle.trim() === '' || newTitle === currentTitle) return;

    try {
      const updatedChat = await renameChat(chatId, newTitle);
      setChats(chats.map(chat => chat.uuid === chatId ? updatedChat : chat));
      toast.success('Chat renamed');
    } catch (error) {
      console.error('Failed to rename chat:', error);
      toast.error('Failed to rename chat');
    }
  };

  const handleDeleteChat = async (chatId: string) => {
    setOpenMenuId(null);
    if (!window.confirm('Are you sure you want to delete this chat?')) return;

    try {
      await deleteChat(chatId);
      setChats(chats.filter(chat => chat.uuid !== chatId));
      toast.success('Chat deleted');
    } catch (error) {
      console.error('Failed to delete chat:', error);
      toast.error('Failed to delete chat');
    }
  };

  return (
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
          <NavLink to="/kb" className={({ isActive }) => `sidebar-nav-link ${isCollapsed ? 'justify-center' : ''} ${isActive ? 'active' : ''}`} title="Knowledge Base">
            <Database size={18}/>
            {!isCollapsed && <span className="sidebar-nav-item-text">Knowledge Base</span>}
          </NavLink>
        </div>

        <div>
          <div className={`sidebar-section-header ${isCollapsed ? 'justify-center' : ''}`}>
            {isCollapsed ? (
              <button onClick={handleNewChat} className="sidebar-new-chat-button" title="New Chat">
                <MessageSquare size={16}/>
              </button>
            ) : (
              <>
                <h2 className="sidebar-section-title">Chats</h2>
                <button onClick={handleNewChat} className="sidebar-new-chat-button" title="New Chat">
                  <Plus size={16}/>
                </button>
              </>
            )}
          </div>
          {!isCollapsed && (
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
                      title="Opzioni"
                    >
                      <MoreVertical size={16} />
                    </button>
                    {openMenuId === chat.uuid && (
                      <div className="sidebar-item-menu" onClick={e => e.stopPropagation()}>
                        <button className="sidebar-menu-btn" onClick={() => handleRenameChat(chat.uuid, chat.title)}>
                          <Edit2 size={14} /> Rename
                        </button>
                        <button className="sidebar-menu-btn delete" onClick={() => handleDeleteChat(chat.uuid)}>
                          <Trash2 size={14} /> Delete
                        </button>
                      </div>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

      </nav>
    </aside>
  );
}