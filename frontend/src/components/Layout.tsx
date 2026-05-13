import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';

export function Layout() {
return (
  <div className="layout-container">
    <Sidebar/>
    <main className="main-content">
      <Outlet/>
    </main>
  </div>
);
}
