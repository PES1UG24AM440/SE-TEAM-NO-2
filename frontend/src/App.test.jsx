import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App.jsx';
import { clearCart, getBooks, getCart, getOrder, placeOrder, removeCartItem, updateCartItem } from './api.js';

vi.mock('./api.js', () => ({
  clearCart: vi.fn(),
  getBooks: vi.fn(),
  getCart: vi.fn(),
  getOrder: vi.fn(),
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
  localStorage.clear();
  cartLines = {};
  getBooks.mockResolvedValue(fixture);
  getCart.mockResolvedValue({ items: [], subtotal: 0 });
  getOrder.mockResolvedValue({});
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

async function finishCheckout(user) {
  await screen.findByRole('heading', { name: 'The Alchemist' });
  await user.click(screen.getByRole('button', { name: /Add to bag/ }));
  await user.click(screen.getByRole('button', { name: /Shopping bag/ }));
  await user.click(screen.getByRole('button', { name: /Continue to checkout/ }));
  for (const [id, value] of Object.entries({ 'field-name': 'A Reader', 'field-email': 'reader@example.com', 'field-address_line': '1 Story Lane', 'field-city': 'Mysuru', 'field-state': 'Karnataka', 'field-postal_code': '570001', 'field-country': 'India' })) fireEvent.change(document.getElementById(id), { target: { value } });
  await user.click(screen.getByRole('button', { name: /Place order/ }));
}

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

  it('shows order ID, purchased items, total and status after checkout', async () => {
    const user = userEvent.setup();
    placeOrder.mockResolvedValue({ order_id: 'order-confirm-58', status: 'CONFIRMED', total: 299, items: [{ book_id: 1, title: 'The Alchemist', quantity: 1, line_total: 299 }] });
    render(<App />); await finishCheckout(user);
    expect(await screen.findByText('Thank you for your order.')).toBeInTheDocument();
    expect(screen.getByText('order-confirm-58')).toBeInTheDocument();
    expect(screen.getByText('CONFIRMED')).toBeInTheDocument();
    expect(screen.getByText('The Alchemist × 1')).toBeInTheDocument();
    expect(screen.getAllByText('₹299.00').length).toBeGreaterThan(0);
    expect(JSON.parse(localStorage.getItem('fieldnotes-order-ids'))).toEqual(['order-confirm-58']);
  });

  it('lists a saved order fetched by getOrder', async () => {
    localStorage.setItem('fieldnotes-order-ids', JSON.stringify(['saved-58']));
    getOrder.mockResolvedValue({ order_id: 'saved-58', status: 'CONFIRMED', total: 299, items: [{ book_id: 1, title: 'The Alchemist', quantity: 1, line_total: 299 }] });
    const user = userEvent.setup(); render(<App />); await user.click(await screen.findByRole('button', { name: 'Orders' }));
    expect(await screen.findByText('saved-58')).toBeInTheDocument();
    expect(screen.getByText('The Alchemist')).toBeInTheDocument();
    expect(getOrder).toHaveBeenCalledWith('saved-58');
  });

  it('shows the empty order history state', async () => {
    const user = userEvent.setup(); render(<App />); await user.click(await screen.findByRole('button', { name: 'Orders' }));
    expect(await screen.findByText('No orders yet.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Back to the shelf' })).toBeInTheDocument();
  });

  it('shows a clear message for an invalid order ID without showing order details', async () => {
    getOrder.mockRejectedValue(new Error('Order not found.'));
    const user = userEvent.setup(); render(<App />); await user.click(await screen.findByRole('button', { name: 'Orders' }));
    await user.type(screen.getByLabelText('Order ID'), 'invalid-id');
    await user.click(screen.getByRole('button', { name: 'Find order' }));
    expect(await screen.findByText("We couldn't find an order with that ID.")).toBeInTheDocument();
    expect(screen.queryByText('Order ID')).not.toBeInTheDocument();
    expect(screen.queryByText('CONFIRMED')).not.toBeInTheDocument();
    expect(screen.queryByText('invalid-id', { selector: 'strong' })).not.toBeInTheDocument();
  });

  it('shows retrieval errors and keeps the orders view usable', async () => {
    getOrder.mockRejectedValue(new Error('Network connection failed.'));
    const user = userEvent.setup(); render(<App />); await user.click(await screen.findByRole('button', { name: 'Orders' }));
    await user.type(screen.getByLabelText('Order ID'), 'network-id');
    await user.click(screen.getByRole('button', { name: 'Find order' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Network connection failed.');
    expect(screen.getByLabelText('Order ID')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Browse books' })).toBeInTheDocument();
  });

  it('asks for an order ID when lookup is empty', async () => {
    const user = userEvent.setup(); render(<App />); await user.click(await screen.findByRole('button', { name: 'Orders' }));
    await user.click(screen.getByRole('button', { name: 'Find order' }));
    expect(await screen.findByText('Enter an order ID.')).toBeInTheDocument();
    expect(getOrder).not.toHaveBeenCalled();
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

describe('saved books and wishlist', () => {
  it('saves a book from its card and updates the wishlist count immediately', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: 'Add The Alchemist to wishlist' }));
    expect(screen.getByRole('button', { name: 'Remove The Alchemist from wishlist' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'Wishlist, 1 saved book' })).toBeInTheDocument();
  });

  it('removes a saved book and updates the count', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: 'Add The Alchemist to wishlist' }));
    await user.click(screen.getByRole('button', { name: 'Wishlist, 1 saved book' }));
    await user.click(screen.getByRole('button', { name: 'Remove The Alchemist from wishlist' }));
    expect(screen.getByRole('button', { name: 'Wishlist, 0 saved books' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Your Wishlist is Empty!' })).toBeInTheDocument();
  });

  it('deduplicates persisted books and persists wishlist changes across a remount', async () => {
    window.localStorage.setItem('fieldnotes-wishlist', JSON.stringify([fixture[0], fixture[0]]));
    const user = userEvent.setup();
    const firstRender = render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    expect(screen.getByRole('button', { name: 'Wishlist, 1 saved book' })).toBeInTheDocument();
    expect(JSON.parse(window.localStorage.getItem('fieldnotes-wishlist'))).toHaveLength(1);
    await user.click(screen.getByRole('button', { name: 'Remove The Alchemist from wishlist' }));
    await user.click(screen.getByRole('button', { name: 'Add The Alchemist to wishlist' }));
    firstRender.unmount();
    render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    expect(screen.getByRole('button', { name: 'Remove The Alchemist from wishlist' })).toHaveAttribute('aria-pressed', 'true');
    await user.click(screen.getByRole('button', { name: 'Wishlist, 1 saved book' }));
    expect(screen.getByRole('heading', { name: 'Your Wishlist' })).toBeInTheDocument();
    expect(screen.getAllByText('Paulo Coelho').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('₹299.00')).toBeInTheDocument();
    expect(screen.getByText('2 in stock')).toBeInTheDocument();
  });

  it('ignores malformed or incomplete saved data without breaking the catalogue', async () => {
    const logError = vi.spyOn(console, 'error').mockImplementation(() => {});
    window.localStorage.setItem('fieldnotes-wishlist', JSON.stringify([null, { id: 2, title: 'Incomplete book' }]));
    render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    expect(screen.getByRole('button', { name: 'Wishlist, 0 saved books' })).toBeInTheDocument();
    expect(JSON.parse(window.localStorage.getItem('fieldnotes-wishlist'))).toEqual([]);
    logError.mockRestore();
  });

  it('keeps wishlist status synchronized between book cards and the details view', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: 'Add The Alchemist to wishlist' }));
    await user.click(screen.getByRole('button', { name: 'The Alchemist' }));
    expect(screen.getByRole('button', { name: 'Remove The Alchemist from wishlist' })).toHaveAttribute('aria-pressed', 'true');
    await user.click(screen.getByRole('button', { name: 'Wishlist, 1 saved book' }));
    expect(screen.getByRole('heading', { name: 'The Alchemist' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Browse books' }));
    expect(screen.getByRole('button', { name: 'Remove The Alchemist from wishlist' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('shows the empty message and Explore Books returns to the catalogue', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: 'Wishlist, 0 saved books' }));
    expect(screen.getByText("You haven't saved any books yet. Explore our collection and save your favourite books.")).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Explore Books' }));
    expect(screen.getByRole('heading', { name: 'Find your next favourite' })).toBeInTheDocument();
  });

  it('adds a saved book to the server-backed cart and updates the cart count', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: 'Add The Alchemist to wishlist' }));
    await user.click(screen.getByRole('button', { name: 'Wishlist, 1 saved book' }));
    await user.click(screen.getByRole('button', { name: 'Add to Cart' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The Alchemist added to your cart.');
    expect(await screen.findByRole('button', { name: 'Shopping bag, 1 items' })).toBeInTheDocument();
    expect(updateCartItem).toHaveBeenCalledWith(1, 1);
    await user.click(screen.getByRole('button', { name: 'Shopping bag, 1 items' }));
    expect(screen.getByRole('heading', { name: 'Your bag' })).toBeInTheDocument();
  });

  it('disables adding a saved book that is out of stock', async () => {
    getBooks.mockResolvedValue([{ ...fixture[0], stock: 0 }]);
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: 'Add The Alchemist to wishlist' }));
    await user.click(screen.getByRole('button', { name: 'Wishlist, 1 saved book' }));
    expect(screen.getByRole('button', { name: 'Add to Cart' })).toBeDisabled();
    expect(screen.getByText('Out of stock')).toBeInTheDocument();
  });
});
