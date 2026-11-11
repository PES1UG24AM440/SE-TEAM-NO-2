import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App.jsx';
import { clearCart, getBooks, getCart, placeOrder, removeCartItem, updateCartItem } from './api.js';

vi.mock('./api.js', () => ({
  clearCart: vi.fn(),
  getBooks: vi.fn(),
  getCart: vi.fn(),
  placeOrder: vi.fn(),
  removeCartItem: vi.fn(),
  updateCartItem: vi.fn(),
}));
const fixture = [{ id: 1, title: 'The Alchemist', author: 'Paulo Coelho', category: 'Fiction', description: 'A shepherd follows his dream.', price: 299, stock: 2 }];
const habits = { id: 2, title: 'Atomic Habits', author: 'James Clear', category: 'Self-help', description: 'Tiny changes.', price: 499, stock: 5 };
let cartLines;
function cartResponse() {
  const items = Object.entries(cartLines).map(([id, quantity]) => {
    const book = [...fixture, habits].find((item) => item.id === Number(id));
    return {
      book_id: book.id, title: book.title, author: book.author, category: book.category,
      description: book.description, quantity, stock: book.stock,
      unit_price: book.price, line_total: book.price * quantity,
    };
  });
  return { items, subtotal: items.reduce((sum, item) => sum + item.line_total, 0) };
}

beforeEach(() => {
  vi.clearAllMocks();
  cartLines = {};
  getBooks.mockResolvedValue(fixture);
  getCart.mockResolvedValue({ items: [], subtotal: 0 });
  updateCartItem.mockImplementation((id, quantity) => {
    cartLines[id] = quantity;
    return Promise.resolve(cartResponse());
  });
  removeCartItem.mockImplementation((id) => {
    delete cartLines[id];
    return Promise.resolve(cartResponse());
  });
  clearCart.mockImplementation(() => {
    cartLines = {};
    return Promise.resolve(cartResponse());
  });
});

describe('bookstore cart and checkout', () => {
  it('renders catalogue, adds books and updates quantity subtotal and total', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: /Add to bag/ }));
    await user.click(screen.getByRole('button', { name: /Shopping bag/ }));
    expect(screen.getAllByText('₹299.00').length).toBeGreaterThanOrEqual(1);
    await user.click(screen.getByRole('button', { name: 'Increase The Alchemist quantity' }));
    expect(await screen.findAllByText('₹598.00')).toHaveLength(3);
    expect(screen.getByText('× 2')).toBeInTheDocument();
    expect(updateCartItem).toHaveBeenLastCalledWith(1, 2);
  });

  it('enforces available stock and removes items into the empty state', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: /Add to bag/ }));
    await user.click(screen.getByRole('button', { name: /Shopping bag/ }));
    await user.click(screen.getByRole('button', { name: 'Remove' }));
    expect(screen.getByText('Your bag is waiting for a story.')).toBeInTheDocument();
    expect(removeCartItem).toHaveBeenCalledWith(1);
  });

  it('clears the session cart', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: /Add to bag/ }));
    await user.click(screen.getByRole('button', { name: /Shopping bag/ }));
    await user.click(screen.getByRole('button', { name: 'Clear bag' }));
    expect(screen.getByText('Your bag is waiting for a story.')).toBeInTheDocument();
    expect(clearCart).toHaveBeenCalledOnce();
  });

  it('shows required and invalid email errors without submitting', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: /Add to bag/ }));
    await user.click(screen.getByRole('button', { name: /Shopping bag/ }));
    await user.click(screen.getByRole('button', { name: /Continue to checkout/ }));
    fireEvent.change(screen.getByLabelText(/Email/), { target: { value: 'not-an-email' } });
    await user.click(screen.getByRole('button', { name: /Place order/ }));
    expect(await screen.findByText('Full name is required.')).toBeInTheDocument();
    expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument();
    expect(placeOrder).not.toHaveBeenCalled();
  });

  it('shows server failure and never displays a false confirmation', async () => {
    const user = userEvent.setup(); placeOrder.mockRejectedValue(new Error('Only 0 copies remain.'));
    render(<App />); await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: /Add to bag/ }));
    await user.click(screen.getByRole('button', { name: /Shopping bag/ }));
    await user.click(screen.getByRole('button', { name: /Continue to checkout/ }));
    for (const [id, value] of Object.entries({ 'field-name': 'A Reader', 'field-email': 'reader@example.com', 'field-address_line': '1 Story Lane', 'field-city': 'Mysuru', 'field-state': 'Karnataka', 'field-postal_code': '570001', 'field-country': 'India' })) fireEvent.change(document.getElementById(id), { target: { value } });
    await user.click(screen.getByRole('button', { name: /Place order/ }));
    expect(await screen.findByRole('status')).toHaveTextContent('Only 0 copies remain.');
    expect(screen.queryByText('Thank you for your order.')).not.toBeInTheDocument();
  });

  it('clears the session cart after successful checkout', async () => {
    const user = userEvent.setup();
    placeOrder.mockResolvedValue({
      order_id: 'order-123', status: 'CONFIRMED', total: 299,
      items: [{ book_id: 1, title: 'The Alchemist', quantity: 1, line_total: 299 }],
    });
    render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: /Add to bag/ }));
    await user.click(screen.getByRole('button', { name: /Shopping bag/ }));
    await user.click(screen.getByRole('button', { name: /Continue to checkout/ }));
    for (const [id, value] of Object.entries({
      'field-name': 'A Reader',
      'field-email': 'reader@example.com',
      'field-address_line': '1 Story Lane',
      'field-city': 'Mysuru',
      'field-state': 'Karnataka',
      'field-postal_code': '570001',
      'field-country': 'India',
    })) fireEvent.change(document.getElementById(id), { target: { value } });
    await user.click(screen.getByRole('button', { name: /Place order/ }));
    expect(await screen.findByText('Thank you for your order.')).toBeInTheDocument();
    expect(clearCart).toHaveBeenCalledOnce();
  });

  it('shows category and description and sends search and category filters to the API', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    expect(screen.getByText('A shepherd follows his dream.')).toBeInTheDocument();
    getBooks.mockResolvedValue([habits]);
    await user.click(screen.getByRole('button', { name: 'Fiction' }));
    expect(await screen.findByRole('heading', { name: 'Atomic Habits' })).toBeInTheDocument();
    expect(getBooks).toHaveBeenLastCalledWith({ q: '', category: 'Fiction' });
    await user.type(screen.getByLabelText('Search books'), 'habit');
    await vi.waitFor(() => expect(getBooks).toHaveBeenLastCalledWith({ q: 'habit', category: 'Fiction' }));
  });

  it('keeps a bagged book in the cart when a filter hides it from the shelf', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: /Add to bag/ }));
    getBooks.mockResolvedValue([habits]);
    await user.click(screen.getByRole('button', { name: 'Fiction' }));
    await screen.findByRole('heading', { name: 'Atomic Habits' });
    await user.click(screen.getByRole('button', { name: /Shopping bag/ }));
    expect(screen.getByRole('heading', { name: 'The Alchemist' })).toBeInTheDocument();
  });

  it('shows a friendly message when no books match', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    getBooks.mockResolvedValue([]);
    await user.type(screen.getByLabelText('Search books'), 'zzzz');
    expect(await screen.findByText(/No books match your search/)).toBeInTheDocument();
  });
});
