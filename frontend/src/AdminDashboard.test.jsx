import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AdminDashboard from './AdminDashboard.jsx';
import {
  getAdminBooks, getAdminOrders, getAdminSummary, getAdminUsers, getAuditLog,
  setBookActive, setUserActive, updateAdminBook,
} from './api.js';

vi.mock('./api.js', () => ({
  getAdminBooks: vi.fn(), getAdminOrders: vi.fn(), getAdminSummary: vi.fn(), getAdminUsers: vi.fn(),
  getAuditLog: vi.fn(), setBookActive: vi.fn(), setUserActive: vi.fn(), updateAdminBook: vi.fn(),
}));

const summary = {
  books: { total: 5, active: 4, inactive: 1, out_of_stock: 2 },
  users: { total: 3, customers: 1, sellers: 1, admins: 1, deactivated: 0 },
  orders: { total: 7, confirmed: 6, cancelled: 1, confirmed_revenue: 1234.5 },
};
const admin = { id: 1, email: 'admin@x.test', display_name: 'Admin', role: 'ADMIN', active: true };
const cara = { id: 2, email: 'cara@x.test', display_name: 'Cara', role: 'CUSTOMER', active: true };
const book = { id: 9, title: 'Managed', author: 'A', category: 'C', description: '', price: 100, stock: 3, active: true, sellerId: 4 };

beforeEach(() => {
  vi.clearAllMocks();
  getAdminSummary.mockResolvedValue(summary);
  getAdminUsers.mockResolvedValue([admin, cara]);
  getAdminBooks.mockResolvedValue([book]);
  getAdminOrders.mockResolvedValue([{ customer_name: 'Cara', email: 'cara@x.test', shipping_address: '1 Road', order: { order_id: 'o-1', status: 'CONFIRMED', total: 100, items: [{ book_id: 9, title: 'Managed', quantity: 1 }] } }]);
  getAuditLog.mockResolvedValue([{ id: 1, actor_email: 'admin@x.test', action: 'USER_DEACTIVATED', target_type: 'USER', target_id: '2', details: 'cara@x.test (CUSTOMER)', created_at: '2026-11-15T03:00:00Z' }]);
});

describe('AdminDashboard', () => {
  it('shows catalogue, user and order summaries', async () => {
    render(<AdminDashboard currentUserId={1} />);
    expect(await screen.findByText('₹1,234.50')).toBeInTheDocument();
    expect(screen.getByText('Accounts').nextSibling).toHaveTextContent('3');
    expect(screen.getByText('Orders', { selector: 'span' }).nextSibling).toHaveTextContent('7');
  });

  it('deactivates another account but never offers it for your own', async () => {
    setUserActive.mockResolvedValue({ ...cara, active: false });
    const user = userEvent.setup();
    render(<AdminDashboard currentUserId={1} />);
    await user.click(screen.getByRole('tab', { name: 'Users' }));
    const ownRow = (await screen.findByText('admin@x.test')).closest('tr');
    expect(within(ownRow).queryByRole('button')).toBeNull();

    await user.click(screen.getByRole('button', { name: 'Deactivate cara@x.test' }));
    expect(setUserActive).toHaveBeenCalledWith(2, false);
    expect(await screen.findByText('cara@x.test is now deactivated.')).toBeInTheDocument();
  });

  it('edits and removes listings', async () => {
    updateAdminBook.mockResolvedValue({ ...book, price: 90 });
    setBookActive.mockResolvedValue({ ...book, active: false });
    const onCatalogueChanged = vi.fn();
    const user = userEvent.setup();
    render(<AdminDashboard currentUserId={1} onCatalogueChanged={onCatalogueChanged} />);
    await user.click(screen.getByRole('tab', { name: 'Listings' }));
    expect(await screen.findByText('Seller #4')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Edit Managed' }));
    const price = screen.getByLabelText('Price (₹)');
    await user.clear(price); await user.type(price, '90');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(updateAdminBook).toHaveBeenCalledWith(9, expect.objectContaining({ price: 90 }));

    await user.click(await screen.findByRole('button', { name: 'Remove Managed' }));
    expect(setBookActive).toHaveBeenCalledWith(9, false);
    expect(onCatalogueChanged).toHaveBeenCalled();
  });

  it('lists orders and the audit log', async () => {
    const user = userEvent.setup();
    render(<AdminDashboard currentUserId={1} />);
    await user.click(screen.getByRole('tab', { name: 'Orders' }));
    expect(await screen.findByText('o-1')).toBeInTheDocument();
    await user.click(screen.getByRole('tab', { name: 'Audit log' }));
    expect(await screen.findByText('USER_DEACTIVATED')).toBeInTheDocument();
    expect(screen.getByText('USER #2')).toBeInTheDocument();
  });

  it('shows the server refusal when access is denied', async () => {
    getAdminSummary.mockRejectedValue(Object.assign(new Error('You do not have permission to do that.'), { status: 403 }));
    render(<AdminDashboard currentUserId={1} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('You do not have permission to do that.');
  });
});
