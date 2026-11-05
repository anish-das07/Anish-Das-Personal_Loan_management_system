import { Navigate, Outlet, useLocation } from "react-router-dom";

/**
 * Route guard (case study section 9). This is a usability feature, not a security boundary:
 * server APIs authorise every call independently.
 */
export function ProtectedRoute({ user, allowedRoles, initialising = false }) {
  const location = useLocation();
  if (initialising) return <p className="p-4" role="status">Checking your session…</p>;
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />;
  if (allowedRoles && !allowedRoles.some((role) => user.roles.includes(role))) {
    return <Navigate to="/forbidden" replace />;
  }
  return <Outlet />;
}
