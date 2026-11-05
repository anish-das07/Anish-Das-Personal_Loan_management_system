import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ProtectedRoute } from "./ProtectedRoute";
import { NAVIGATION_ITEMS, visibleItems } from "./navigation";
import { AppLayout } from "../layouts/AppLayout";

vi.mock("../features/staff/staffApi", () => ({
  notificationApi: { mine: vi.fn().mockResolvedValue([]), read: vi.fn() }
}));

const customer = { id: "c1", fullName: "Asha Customer", email: "asha@example.test", roles: ["CUSTOMER"] };
const approver = { id: "a1", fullName: "Ravi Approver", email: "approver@plms.local", roles: ["LOAN_APPROVER"] };

function renderGuard(user, allowedRoles, path = "/secure") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<p>Login page</p>} />
        <Route path="/forbidden" element={<p>Forbidden page</p>} />
        <Route element={<ProtectedRoute user={user} allowedRoles={allowedRoles} />}>
          <Route path="/secure" element={<p>Secret content</p>} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
}

describe("ProtectedRoute", () => {
  test("redirects anonymous users to login", () => {
    renderGuard(null, ["CUSTOMER"]);
    expect(screen.getByText("Login page")).toBeInTheDocument();
    expect(screen.queryByText("Secret content")).not.toBeInTheDocument();
  });

  test("redirects users without an allowed role to forbidden", () => {
    renderGuard(customer, ["LOAN_APPROVER"]);
    expect(screen.getByText("Forbidden page")).toBeInTheDocument();
  });

  test("renders the outlet for an allowed role", () => {
    renderGuard(approver, ["LOAN_APPROVER"]);
    expect(screen.getByText("Secret content")).toBeInTheDocument();
  });

  test("shows a session check while initialising", () => {
    render(
      <MemoryRouter><Routes><Route path="/" element={<ProtectedRoute user={null} initialising />} /></Routes></MemoryRouter>
    );
    expect(screen.getByRole("status")).toHaveTextContent(/checking your session/i);
  });
});

describe("role-aware navigation", () => {
  test("visibleItems filters by role", () => {
    const labels = visibleItems(NAVIGATION_ITEMS, customer).map((i) => i.label);
    expect(labels).toContain("My Applications");
    expect(labels).not.toContain("Approvals");
    expect(visibleItems(NAVIGATION_ITEMS, null)).toEqual([]);
  });

  test("AppLayout shows only the items for the user's roles", () => {
    render(
      <MemoryRouter initialEntries={["/staff/approvals/queue"]}>
        <Routes>
          <Route element={<AppLayout user={approver} navigationItems={NAVIGATION_ITEMS} onLogout={vi.fn()} />}>
            <Route path="/staff/approvals/queue" element={<p>Queue</p>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );
    const nav = screen.getByRole("navigation", { name: /main navigation/i });
    expect(within(nav).getByRole("link", { name: "Approvals" })).toHaveClass("active");
    expect(within(nav).getByRole("link", { name: "Reports" })).toBeInTheDocument();
    expect(within(nav).queryByRole("link", { name: "My Applications" })).not.toBeInTheDocument();
    expect(within(nav).queryByRole("link", { name: "Users & Roles" })).not.toBeInTheDocument();
    expect(screen.getByRole("main")).toHaveTextContent("Queue");
  });
});
