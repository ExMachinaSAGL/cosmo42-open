import { NavLink } from 'react-router-dom';
import { Database, MessageSquare, Plus, PanelLeftClose, MoreVertical, Edit2, Trash2 } from 'lucide-react';
import { useState, useEffect } from 'react';
import logo from '../assets/Cosmo42logo_128x128.jpg';

export function Sidebar() {
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);

  useEffect(() => {
    const handleClickOutside = () => setOpenMenuId(null);
    document.addEventListener('click', handleClickOutside);
    return () => document.removeEventListener('click', handleClickOutside);
  }, []);

  const toggleMenu = (e: React.MouseEvent, id: string) => {
    e.preventDefault();
    e.stopPropagation();
    setOpenMenuId(openMenuId === id ? null : id);
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
              <button className="sidebar-new-chat-button" title="New Chat">
                <MessageSquare size={16}/>
              </button>
            ) : (
              <>
                <h2 className="sidebar-section-title">Chats</h2>
                <button className="sidebar-new-chat-button" title="New Chat">
                  <Plus size={16}/>
                </button>
              </>
            )}
          </div>
          {!isCollapsed && (
            <ul className="sidebar-nav-list">
              <li className="sidebar-nav-item-container">
                <NavLink to="/" className={({ isActive }) => `sidebar-nav-link ${isActive ? 'active' : ''}`} title="Progetto X...">
                  <span className="sidebar-nav-item-text">Progetto X...</span>
                </NavLink>

                <div className="sidebar-item-actions">
                  <button 
                    onClick={(e) => toggleMenu(e, 'proj-1')} 
                    className="sidebar-action-btn"
                    title="Opzioni"
                  >
                    <MoreVertical size={16} />
                  </button>
                  {openMenuId === 'proj-1' && (
                    <div className="sidebar-item-menu" onClick={e => e.stopPropagation()}>
                      <button className="sidebar-menu-btn" onClick={() => setOpenMenuId(null)}>
                        <Edit2 size={14} /> Rename
                      </button>
                      <button className="sidebar-menu-btn delete" onClick={() => setOpenMenuId(null)}>
                        <Trash2 size={14} /> Delete
                      </button>
                    </div>
                  )}
                </div>
              </li>

            </ul>
          )}
        </div>

      </nav>
    </aside>
  );
}
