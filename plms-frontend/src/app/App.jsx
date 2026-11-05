import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "../context/AuthContext";
import { useAuth } from "../hooks/useAuth";
import { AppLayout } from "../layouts/AppLayout";
import { ProtectedRoute } from "../routes/ProtectedRoute";
import { NAVIGATION_ITEMS } from "../routes/navigation";
import { homePathFor } from "../utils/status";
import { ForbiddenPage, ForgotPasswordPage, LoginPage, NotFoundPage, RegistrationPage, ResetPasswordPage } from "../pages/AuthPages";
import { CustomerDashboard, ProductCataloguePage, ProductDetailsPage, ProfilePage } from "../pages/CustomerPages";
import { ApplicationStatusPage, ApplicationWizardPage, ApplicationsListPage } from "../pages/ApplicationPages";
import { LoanAccountPage, LoanAccountsPage } from "../pages/LoanPages";
import { ApplicationQueuePage, ApplicationReviewPage, DocumentQueuePage, ReportsPage, StaffDashboardPage } from "../pages/StaffPages";
import { DisbursementPage, OverduePage } from "../pages/FinancePages";
import { AuditPage, ProductsAdminPage, UsersPage } from "../pages/AdminPages";

const STAFF = ["LOAN_OFFICER", "CREDIT_ANALYST", "DOCUMENT_VERIFIER", "LOAN_APPROVER", "FINANCE_OFFICER"];

/** Route table (case study section 10). Guards are for usability; the API authorises every call. */
export function AppRoutes() {
  const { user, initialising, logout } = useAuth();
  const guard = (roles) => <ProtectedRoute user={user} allowedRoles={roles} initialising={initialising} />;
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegistrationPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/forbidden" element={<ForbiddenPage />} />
      <Route path="/" element={initialising ? null : <Navigate to={homePathFor(user)} replace />} />

      <Route element={guard()}>
        <Route element={<AppLayout user={user} navigationItems={NAVIGATION_ITEMS} onLogout={logout} />}>
          <Route element={guard(["CUSTOMER"])}>
            <Route path="/app/dashboard" element={<CustomerDashboard />} />
            <Route path="/app/profile" element={<ProfilePage />} />
            <Route path="/app/products" element={<ProductCataloguePage />} />
            <Route path="/app/products/:productId" element={<ProductDetailsPage />} />
            <Route path="/app/applications" element={<ApplicationsListPage />} />
            <Route path="/app/applications/new" element={<ApplicationWizardPage />} />
            <Route path="/app/applications/:applicationId" element={<ApplicationStatusPage />} />
            <Route path="/app/applications/:applicationId/edit" element={<ApplicationWizardPage />} />
            <Route path="/app/loans" element={<LoanAccountsPage />} />
            <Route path="/app/loans/:accountId" element={<LoanAccountPage />} />
          </Route>
          <Route element={guard(STAFF)}>
            <Route path="/staff/dashboard" element={<StaffDashboardPage />} />
          </Route>
          <Route element={guard(["LOAN_OFFICER"])}>
            <Route path="/staff/loan-officer/queue" element={<ApplicationQueuePage title="Loan officer workspace" />} />
          </Route>
          <Route element={guard(["CREDIT_ANALYST"])}>
            <Route path="/staff/credit/queue" element={<ApplicationQueuePage title="Credit assessment queue" queues={["REVIEW", "NEW"]} />} />
          </Route>
          <Route element={guard(["LOAN_APPROVER"])}>
            <Route path="/staff/approvals/queue" element={<ApplicationQueuePage title="Approval queue" queues={["REVIEW", "DECIDED"]} />} />
          </Route>
          <Route element={guard(["LOAN_OFFICER", "CREDIT_ANALYST", "LOAN_APPROVER"])}>
            <Route path="/staff/applications/:applicationId" element={<ApplicationReviewPage />} />
          </Route>
          <Route element={guard(["DOCUMENT_VERIFIER"])}>
            <Route path="/staff/documents/queue" element={<DocumentQueuePage />} />
          </Route>
          <Route element={guard(["FINANCE_OFFICER"])}>
            <Route path="/staff/disbursements" element={<DisbursementPage />} />
            <Route path="/staff/overdue" element={<OverduePage />} />
          </Route>
          <Route element={guard(["FINANCE_OFFICER", "LOAN_APPROVER", "ADMIN"])}>
            <Route path="/staff/reports" element={<ReportsPage />} />
          </Route>
          <Route element={guard(["ADMIN"])}>
            <Route path="/admin/users" element={<UsersPage />} />
            <Route path="/admin/products" element={<ProductsAdminPage />} />
            <Route path="/admin/audit" element={<AuditPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  );
}
