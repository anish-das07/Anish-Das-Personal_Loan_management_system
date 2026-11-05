/** Sidebar items. Role filtering improves usability only; every API call is authorised server-side. */
export const NAVIGATION_ITEMS = [
  { to: "/app/dashboard", label: "Dashboard", roles: ["CUSTOMER"] },
  { to: "/app/products", label: "Loan Products", roles: ["CUSTOMER"] },
  { to: "/app/applications", label: "My Applications", roles: ["CUSTOMER"] },
  { to: "/app/loans", label: "Loan Accounts", roles: ["CUSTOMER"] },
  { to: "/app/profile", label: "My Profile", roles: ["CUSTOMER"] },
  { to: "/staff/dashboard", label: "Staff Dashboard",
    roles: ["LOAN_OFFICER", "CREDIT_ANALYST", "DOCUMENT_VERIFIER", "LOAN_APPROVER", "FINANCE_OFFICER"] },
  { to: "/staff/loan-officer/queue", label: "Review Queue", roles: ["LOAN_OFFICER"] },
  { to: "/staff/credit/queue", label: "Credit Assessment", roles: ["CREDIT_ANALYST"] },
  { to: "/staff/documents/queue", label: "Document Verification", roles: ["DOCUMENT_VERIFIER"] },
  { to: "/staff/approvals/queue", label: "Approvals", roles: ["LOAN_APPROVER"] },
  { to: "/staff/disbursements", label: "Disbursements", roles: ["FINANCE_OFFICER"] },
  { to: "/staff/overdue", label: "Overdue & Closure", roles: ["FINANCE_OFFICER"] },
  { to: "/staff/reports", label: "Reports", roles: ["FINANCE_OFFICER", "LOAN_APPROVER", "ADMIN"] },
  { to: "/admin/users", label: "Users & Roles", roles: ["ADMIN"] },
  { to: "/admin/products", label: "Products & Policies", roles: ["ADMIN"] },
  { to: "/admin/audit", label: "Audit Trail", roles: ["ADMIN"] }
];

export function visibleItems(items, user) {
  if (!user) return [];
  return items.filter((item) => !item.roles || item.roles.some((role) => user.roles.includes(role)));
}
