// src/components/layouts/MainLayout.jsx
// Global layout shell for all client-facing pages.
// Automatically provides Navbar at top and Footer at bottom.
// Child pages only need to render their own content - no more manual Navbar/Footer imports.

import { Outlet } from 'react-router-dom';
import Navbar from './Navbar';
import Footer from './Footer';

export default function MainLayout() {
  return (
    <div className="min-h-screen flex flex-col bg-[var(--bg-canvas)] text-[var(--text-primary)]">
      <Navbar />
      <main className="flex-1 flex flex-col">
        <Outlet />
      </main>
      <Footer />
    </div>
  );
}
