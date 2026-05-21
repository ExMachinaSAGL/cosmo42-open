import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Toaster } from 'react-hot-toast';

export function Layout() {
  return (
    <div className="layout-container">
      <Toaster position="top-center" />
      <Sidebar />
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
}
