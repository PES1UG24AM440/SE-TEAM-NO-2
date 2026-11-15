import { useCallback, useEffect, useState } from 'react';
import {
  getAdminBooks, getAdminOrders, getAdminSummary, getAdminUsers, getAuditLog,
  setBookActive, setUserActive, updateAdminBook,
} from './api.js';
import ListingForm from './ListingForm.jsx';
import { listingFromBook } from './listing.js';

const currency = (value) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(value ?? 0);
const when = (value) => (value ? new Date(value).toLocaleString('en-IN') : '');
const tabs = [['overview', 'Overview'], ['users', 'Users'], ['books', 'Listings'], ['orders', 'Orders'], ['audit', 'Audit log']];
const loaders = { overview: getAdminSummary, users: getAdminUsers, books: getAdminBooks, orders: getAdminOrders, audit: getAuditLog };

function Tile({ label, value, note }) {
  return <div className="summary-tile"><span>{label}</span><strong>{value}</strong>{note && <small>{note}</small>}</div>;
}

// Administrator dashboard (#73). Hiding this screen is only convenience: the server enforces ADMIN.
export default function AdminDashboard({ currentUserId, onCatalogueChanged = () => {} }) {
  const [tab, setTab] = useState('overview');
  const [data, setData] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [editing, setEditing] = useState(null);

  const load = useCallback((name) => {
    setLoading(true); setError('');
    return loaders[name]()
      .then((result) => setData((current) => ({ ...current, [name]: result })))
      .catch((failure) => setError(failure.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(tab); }, [tab, load]);

  async function run(action, message, reload) {
    setError(''); setNotice('');
    try {
      const result = await action();
      setNotice(message(result));
      await load(reload);
      return result;
    } catch (failure) {
      setError(failure.message);
      return null;
    }
  }

  const toggleUser = (user) => run(() => setUserActive(user.id, !user.active),
    (updated) => `${updated.email} is now ${updated.active ? 'active' : 'deactivated'}.`, 'users');
  const toggleBook = (book) => run(() => setBookActive(book.id, !book.active),
    (updated) => `"${updated.title}" is now ${updated.active ? 'listed' : 'removed from the catalogue'}.`, 'books')
    .then(onCatalogueChanged);
  async function saveBook(payload) {
    const updated = await updateAdminBook(editing.id, payload);
    setEditing(null);
    setNotice(`"${updated.title}" was updated.`);
    await load('books'); onCatalogueChanged();
  }

  const summary = data.overview;
  return <section className="dashboard" aria-labelledby="admin-title">
    <p className="eyebrow">Administrator</p>
    <h2 id="admin-title">Store administration</h2>
    <div className="tab-row" role="tablist" aria-label="Admin sections">
      {tabs.map(([key, label]) => <button key={key} role="tab" aria-selected={tab === key} className={tab === key ? 'chip active' : 'chip'} onClick={() => { setTab(key); setNotice(''); setEditing(null); }}>{label}</button>)}
    </div>
    {notice && <p className="notice" role="status">{notice}</p>}
    {error && <p className="order-error" role="alert">{error}</p>}
    {loading && <p className="loading" role="status">Loading…</p>}

    {tab === 'overview' && summary && <>
      <h3>Catalogue</h3>
      <div className="summary-tiles"><Tile label="Books" value={summary.books.total} /><Tile label="Listed" value={summary.books.active} /><Tile label="Removed" value={summary.books.inactive} /><Tile label="Out of stock" value={summary.books.out_of_stock} note="listed books with 0 stock" /></div>
      <h3>Users</h3>
      <div className="summary-tiles"><Tile label="Accounts" value={summary.users.total} /><Tile label="Customers" value={summary.users.customers} /><Tile label="Sellers" value={summary.users.sellers} /><Tile label="Admins" value={summary.users.admins} /><Tile label="Deactivated" value={summary.users.deactivated} /></div>
      <h3>Orders</h3>
      <div className="summary-tiles"><Tile label="Orders" value={summary.orders.total} /><Tile label="Confirmed" value={summary.orders.confirmed} /><Tile label="Cancelled" value={summary.orders.cancelled} /><Tile label="Confirmed revenue" value={currency(summary.orders.confirmed_revenue)} /><Tile label="Refunded" value={currency(summary.orders.refunded_total)} /></div>
    </>}

    {tab === 'users' && data.users && <div className="table-scroll"><table className="data-table">
      <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Actions</th></tr></thead>
      <tbody>{data.users.map((user) => <tr key={user.id}>
        <td>{user.display_name}</td><td>{user.email}</td><td>{user.role}</td>
        <td><span className={user.active ? 'status-pill' : 'status-pill off'}>{user.active ? 'Active' : 'Deactivated'}</span></td>
        <td>{user.id === currentUserId ? <small>You</small>
          : <button className="text-button" onClick={() => toggleUser(user)} aria-label={`${user.active ? 'Deactivate' : 'Activate'} ${user.email}`}>{user.active ? 'Deactivate' : 'Activate'}</button>}</td>
      </tr>)}</tbody>
    </table></div>}

    {tab === 'books' && editing && <><h3>Edit “{editing.title}”</h3><ListingForm key={editing.id} idPrefix="admin-listing" initial={listingFromBook(editing)} submitLabel="Save changes" onSubmit={saveBook} onCancel={() => setEditing(null)} /></>}
    {tab === 'books' && data.books && <div className="table-scroll"><table className="data-table">
      <thead><tr><th>Title</th><th>Seller</th><th>Price</th><th>Stock</th><th>Status</th><th>Actions</th></tr></thead>
      <tbody>{data.books.map((book) => <tr key={book.id}>
        <td><strong>{book.title}</strong><br /><small>{book.author} · {book.category}</small></td>
        <td>{book.sellerId ? `Seller #${book.sellerId}` : 'Store'}</td>
        <td>{currency(book.price)}</td><td>{book.stock}</td>
        <td><span className={book.active ? 'status-pill' : 'status-pill off'}>{book.active ? 'Listed' : 'Removed'}</span></td>
        <td><div className="row-actions">
          <button className="text-button" onClick={() => setEditing(book)} aria-label={`Edit ${book.title}`}>Edit</button>
          <button className="text-button" onClick={() => toggleBook(book)} aria-label={`${book.active ? 'Remove' : 'Relist'} ${book.title}`}>{book.active ? 'Remove' : 'Relist'}</button>
        </div></td>
      </tr>)}</tbody>
    </table></div>}

    {tab === 'orders' && data.orders && (data.orders.length === 0 ? <p className="account-meta">No orders yet.</p>
      : <div className="table-scroll"><table className="data-table">
        <thead><tr><th>Order</th><th>Customer</th><th>Items</th><th>Total</th><th>Status</th><th>Refund</th></tr></thead>
        <tbody>{data.orders.map(({ order, customer_name, email, shipping_address }) => <tr key={order.order_id}>
          <td><code>{order.order_id}</code></td>
          <td>{customer_name}<br /><small>{email}</small><br /><small>{shipping_address}</small></td>
          <td>{order.items.map((item) => <div key={item.book_id}>{item.title} × {item.quantity}</div>)}</td>
          <td>{currency(order.total)}</td>
          <td><span className={order.status === 'CONFIRMED' ? 'status-pill' : 'status-pill off'}>{order.status}</span></td>
          <td>{order.refund ? `${currency(order.refund.amount)} (${order.refund.type.toLowerCase()})` : '—'}</td>
        </tr>)}</tbody>
      </table></div>)}

    {tab === 'audit' && data.audit && (data.audit.length === 0 ? <p className="account-meta">No administrative actions recorded yet.</p>
      : <div className="table-scroll"><table className="data-table">
        <thead><tr><th>When</th><th>Administrator</th><th>Action</th><th>Target</th><th>Details</th></tr></thead>
        <tbody>{data.audit.map((entry) => <tr key={entry.id}>
          <td>{when(entry.created_at)}</td><td>{entry.actor_email}</td><td>{entry.action}</td>
          <td>{entry.target_type} #{entry.target_id}</td><td>{entry.details}</td>
        </tr>)}</tbody>
      </table></div>)}
  </section>;
}
