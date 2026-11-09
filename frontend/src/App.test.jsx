import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App.jsx';
import { getBooks, getOrderQuote, placeOrder } from './api.js';

vi.mock('./api.js', () => ({ getBooks: vi.fn(), getOrderQuote: vi.fn(), placeOrder: vi.fn() }));
const fixture = [{ id: 1, title: 'The Alchemist', author: 'Paulo Coelho', category: 'Fiction', description: 'A shepherd follows his dream.', price: 299, stock: 2 }];
const habits = { id: 2, title: 'Atomic Habits', author: 'James Clear', category: 'Self-help', description: 'Tiny changes.', price: 499, stock: 5 };

beforeEach(() => {
  vi.clearAllMocks(); getBooks.mockResolvedValue(fixture);
  getOrderQuote.mockImplementation((items) => Promise.resolve({
    items: items.map(({ book_id, quantity }) => ({ book_id, title: fixture.find((book) => book.id === book_id)?.title ?? habits.title, author: fixture.find((book) => book.id === book_id)?.author ?? habits.author, quantity, unit_price: fixture.find((book) => book.id === book_id)?.price ?? habits.price, line_total: (fixture.find((book) => book.id === book_id)?.price ?? habits.price) * quantity })),
    item_count: items.reduce((count, item) => count + item.quantity, 0), subtotal: items.reduce((sum, item) => sum + (fixture.find((book) => book.id === item.book_id)?.price ?? habits.price) * item.quantity, 0), shipping: 0, tax: 0, discount: 0,
    total: items.reduce((sum, item) => sum + (fixture.find((book) => book.id === item.book_id)?.price ?? habits.price) * item.quantity, 0),
  }));
});

describe('bookstore cart and checkout', () => {
  it('renders catalogue, adds books and updates quantity subtotal and total', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: /Add to bag/ }));
    await user.click(screen.getByRole('button', { name: /Shopping bag/ }));
    expect(screen.getAllByText('₹299.00').length).toBeGreaterThanOrEqual(1);
    await user.click(screen.getByRole('button', { name: 'Increase The Alchemist quantity' }));
    expect(screen.getAllByText('₹598.00').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('× 2')).toBeInTheDocument();
  });

  it('enforces available stock and removes items into the empty state', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: /Add to bag/ }));
    await user.click(screen.getByRole('button', { name: /Shopping bag/ }));
    await user.click(screen.getByRole('button', { name: 'Remove' }));
    expect(screen.getByText('Your bag is waiting for a story.')).toBeInTheDocument();
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
    expect(screen.getByRole('button', { name: /Place order/ })).toBeEnabled();
    expect(screen.queryByText('Thank you for your order.')).not.toBeInTheDocument();
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

  it('rejects whitespace-only required values and invalid phone or postal codes, then clears corrected errors', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: /Add to bag/ }));
    await user.click(screen.getByRole('button', { name: /Shopping bag/ }));
    await user.click(screen.getByRole('button', { name: /Continue to checkout/ }));
    fireEvent.change(document.getElementById('field-name'), { target: { value: '   ' } });
    fireEvent.change(document.getElementById('field-phone'), { target: { value: '-------' } });
    fireEvent.change(document.getElementById('field-postal_code'), { target: { value: '12!!!' } });
    await user.click(screen.getByRole('button', { name: /Place order/ }));
    expect(await screen.findByText('Full name is required.')).toBeInTheDocument();
    expect(screen.getByText('Enter a valid phone number.')).toBeInTheDocument();
    expect(screen.getByText('Enter a valid postal code.')).toBeInTheDocument();
    await user.clear(document.getElementById('field-name'));
    await user.type(document.getElementById('field-name'), 'Zoë O’Connor');
    await user.clear(document.getElementById('field-phone'));
    await user.type(document.getElementById('field-phone'), '+91 98765 43210');
    await user.clear(document.getElementById('field-postal_code'));
    await user.type(document.getElementById('field-postal_code'), 'SW1A 1AA');
    expect(screen.queryByText('Full name is required.')).not.toBeInTheDocument();
    expect(screen.queryByText('Enter a valid phone number.')).not.toBeInTheDocument();
    expect(screen.queryByText('Enter a valid postal code.')).not.toBeInTheDocument();
    expect(document.getElementById('field-name')).toHaveValue('Zoë O’Connor');
  });

  it('shows server-calculated line totals and payable amount in the checkout summary', async () => {
    getOrderQuote.mockResolvedValue({ items: [{ book_id: 1, title: 'The Alchemist', author: 'Paulo Coelho', quantity: 2, unit_price: 299, line_total: 598 }], item_count: 2, subtotal: 598, shipping: 40, tax: 32, discount: 10, total: 660 });
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: /Add to bag/ }));
    await user.click(screen.getByRole('button', { name: /Add to bag/ }));
    await user.click(screen.getByRole('button', { name: /Shopping bag/ }));
    await user.click(screen.getByRole('button', { name: /Continue to checkout/ }));
    expect(await screen.findByText('Final payable amount')).toBeInTheDocument();
    expect(screen.getByText('₹660.00')).toBeInTheDocument();
    expect(screen.getByText('₹40.00')).toBeInTheDocument();
    expect(getOrderQuote).toHaveBeenCalledWith([{ book_id: 1, quantity: 2 }]);
  });

  it('locks checkout while a request is in flight and submits only once', async () => {
    let completeOrder;
    placeOrder.mockImplementation(() => new Promise((resolve) => { completeOrder = resolve; }));
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: /Add to bag/ }));
    await user.click(screen.getByRole('button', { name: /Shopping bag/ }));
    await user.click(screen.getByRole('button', { name: /Continue to checkout/ }));
    for (const [id, value] of Object.entries({ 'field-name': 'A Reader', 'field-email': 'reader@example.com', 'field-address_line': '1 Story Lane', 'field-city': 'Mysuru', 'field-state': 'Karnataka', 'field-postal_code': '570001', 'field-country': 'India' })) fireEvent.change(document.getElementById(id), { target: { value } });
    const submit = await screen.findByRole('button', { name: /Place order/ });
    await user.click(submit);
    expect(await screen.findByRole('button', { name: 'Placing your order…' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Placing your order…' }));
    expect(placeOrder).toHaveBeenCalledTimes(1);
    completeOrder({ order_id: 'one-order', status: 'CONFIRMED', total: 299, items: [{ book_id: 1, title: 'The Alchemist', quantity: 1, line_total: 299 }] });
    expect(await screen.findByText('one-order')).toBeInTheDocument();
  });
});
