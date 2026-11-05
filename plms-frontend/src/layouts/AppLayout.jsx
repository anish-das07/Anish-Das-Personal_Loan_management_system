import { useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { ROLE_LABELS } from "../utils/status";
import { visibleItems } from "../routes/navigation";
import { NotificationPanel } from "../features/notifications/NotificationPanel";

export function TopNavigation({ user, onLogout, onToggleMenu, showNotifications = true }) {
  const [menuOpen, setMenuOpen] = useState(false);
  return (
    <header className="top-nav navbar border-bottom bg-body px-3">
      <button type="button" className="btn btn-outline-secondary btn-sm d-lg-none me-2" onClick={onToggleMenu}
        aria-label="Toggle navigation">☰</button>
      <Link to="/" className="navbar-brand fw-bold me-auto">PLMS <span className="fw-normal d-none d-sm-inline">Personal Loan Portal</span></Link>
      {showNotifications && user?.roles?.includes("CUSTOMER") && <NotificationPanel />}
      <div className="position-relative ms-2">
        <button type="button" className="btn btn-light btn-sm" aria-haspopup="menu" aria-expanded={menuOpen}
          onClick={() => setMenuOpen((o) => !o)}>
          {user?.fullName ?? "Account"} ▾
        </button>
        {menuOpen && (
          <div className="dropdown-menu dropdown-menu-end show" role="menu">
            <span className="dropdown-item-text small text-body-secondary">{user?.email}</span>
            <span className="dropdown-item-text small">{user?.roles?.map((r) => ROLE_LABELS[r] ?? r).join(", ")}</span>
            <div className="dropdown-divider" />
            <button type="button" role="menuitem" className="dropdown-item" onClick={onLogout}>Sign out</button>
          </div>
        )}
      </div>
    </header>
  );
}

export function SidebarNavigation({ items, open, onNavigate }) {
  return (
    <nav className={`sidebar border-end bg-body-tertiary ${open ? "open" : ""}`} aria-label="Main navigation">
      <ul className="nav flex-column p-2">
        {items.map((item) => (
          <li key={item.to} className="nav-item">
            <NavLink to={item.to} className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`} onClick={onNavigate}>
              {item.label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}

export function Breadcrumbs({ items }) {
  const location = useLocation();
  const crumbs = items ?? location.pathname.split("/").filter(Boolean).map((part, i, all) => ({
    label: part.length > 20 ? part.slice(0, 8).toUpperCase() : part.replaceAll("-", " ").replace(/^\w/, (c) => c.toUpperCase()),
    to: i < all.length - 1 ? "/" + all.slice(0, i + 1).join("/") : undefined
  }));
  return (
    <nav aria-label="Breadcrumb">
      <ol className="breadcrumb small mb-2">
        {crumbs.map((c, i) => (
          <li key={i} className={`breadcrumb-item ${c.to ? "" : "active"}`} aria-current={c.to ? undefined : "page"}>
            {c.to ? <Link to={c.to}>{c.label}</Link> : c.label}
          </li>
        ))}
      </ol>
    </nav>
  );
}

export function PageHeader({ title, subtitle, actions }) {
  return (
    <div className="page-header d-flex flex-wrap align-items-start gap-2 mb-3">
      <div className="me-auto">
        <h1 className="h3 mb-1">{title}</h1>
        {subtitle && <p className="text-body-secondary mb-0">{subtitle}</p>}
      </div>
      {actions && <div className="d-flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

/** Application shell (section 8). Navigation filtering improves UX only; APIs still enforce authorisation. */
export function AppLayout({ user, navigationItems, onLogout }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const items = visibleItems(navigationItems, user);
  return (
    <div className="app-shell">
      <TopNavigation user={user} onLogout={onLogout} onToggleMenu={() => setMenuOpen((o) => !o)} />
      <div className="app-body">
        <SidebarNavigation items={items} open={menuOpen} onNavigate={() => setMenuOpen(false)} />
        <main id="main-content" className="app-main" tabIndex={-1}>
          <Breadcrumbs />
          <Outlet />
        </main>
      </div>
      <footer className="app-footer border-top small text-body-secondary px-3 py-2">
        Help · Privacy · Training build {import.meta.env?.VITE_BUILD_VERSION ?? "1.0.0"} · Synthetic data and simulated payments only
      </footer>
    </div>
  );
}
