import { useEffect, useState } from "react";
import { notificationApi } from "../staff/staffApi";
import { formatDateTime } from "../../utils/format";

/** Bell with unread count and a list of mock notifications (customer only). */
export function NotificationPanel() {
  const [items, setItems] = useState([]);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    let alive = true;
    notificationApi.mine().then((data) => { if (alive) setItems(data); }).catch(() => {});
    return () => { alive = false; };
  }, [open]);

  const unread = items.filter((n) => !n.read).length;
  const markRead = async (id) => {
    await notificationApi.read(id).catch(() => {});
    setItems((list) => list.map((n) => (n.id === id ? { ...n, read: true } : n)));
  };

  return (
    <div className="position-relative">
      <button type="button" className="btn btn-light btn-sm" aria-haspopup="dialog" aria-expanded={open}
        onClick={() => setOpen((o) => !o)} aria-label={`Notifications, ${unread} unread`}>
        🔔 <span className="badge text-bg-primary">{unread}</span>
      </button>
      {open && (
        <div className="card notification-panel shadow" role="dialog" aria-label="Notifications">
          <ul className="list-group list-group-flush">
            {items.length === 0 && <li className="list-group-item small">No notifications</li>}
            {items.slice(0, 10).map((n) => (
              <li key={n.id} className={`list-group-item small ${n.read ? "" : "fw-semibold"}`}>
                <div>{n.subject}</div>
                <div className="fw-normal">{n.body}</div>
                <div className="d-flex justify-content-between text-body-secondary">
                  <span>{formatDateTime(n.createdAt)}</span>
                  {!n.read && <button type="button" className="btn btn-link btn-sm p-0" onClick={() => markRead(n.id)}>Mark read</button>}
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
