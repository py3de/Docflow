import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { FileStack, FlaskConical, LayoutDashboard, Moon, ScanText, Sun, Upload, ClipboardCheck } from 'lucide-react';
import { api, usingMock } from '../api/client';

export function Layout() {
  const [queue, setQueue] = useState<number>();
  const [theme, setTheme] = useState<'light' | 'dark'>(() =>
    window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light',
  );
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);
  const location = useLocation();
  useEffect(() => {
    api.getOverview().then((o) => setQueue(o.reviewQueue)).catch(() => undefined);
  }, [location.pathname]);

  const link = ({ isActive }: { isActive: boolean }) => `nav-link${isActive ? ' active' : ''}`;
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark"><ScanText size={17} /></div>
          <div>
            <div className="brand-name">DocFlow</div>
            <div className="brand-sub">Document AI console</div>
          </div>
        </div>
        <NavLink to="/" end className={link}><LayoutDashboard size={16} />Overview</NavLink>
        <NavLink to="/documents" className={link}><FileStack size={16} />Documents</NavLink>
        <NavLink to="/review" className={link}>
          <ClipboardCheck size={16} />Review queue
          {queue !== undefined && <span className="nav-count num">{queue}</span>}
        </NavLink>
        <NavLink to="/upload" className={link}><Upload size={16} />Upload</NavLink>
        <NavLink to="/evaluation" className={link}><FlaskConical size={16} />Model evaluation</NavLink>
        <div className="sidebar-foot">
          <span className="env-pill">
            <i className="dot" style={{ background: usingMock ? 'var(--warning)' : 'var(--good)' }} />
            {usingMock ? 'Mock data' : 'Connected to API'} · tenant <b>acme-de</b>
          </span>
          <button className="btn btn-ghost" style={{ justifyContent: 'flex-start', padding: '6px 0' }}
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}>
            {theme === 'dark' ? <Sun size={15} /> : <Moon size={15} />}
            {theme === 'dark' ? 'Light mode' : 'Dark mode'}
          </button>
        </div>
      </aside>
      <main className="main"><Outlet /></main>
    </div>
  );
}
