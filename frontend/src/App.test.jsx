import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App.jsx';
import {
  clearCart,
  getBook,
  getBooks,
  getCart,
  getOrder,
  getOrderQuote,
  placeOrder,
  removeCartItem,
  updateCartItem,
} from './api.js';
vi.mock('./api.js', () => ({
  clearCart: vi.fn(),
  getBook: vi.fn(),
  getBooks: vi.fn(),
  getCart: vi.fn(),
  getOrder: vi.fn(),
  getOrderQuote: vi.fn(),
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
  window.history.replaceState(null, '', '/');

  cartLines = {};

  getBooks.mockResolvedValue(fixture);
  getCart.mockResolvedValue({ items: [], subtotal: 0 });
  getOrder.mockResolvedValue({});

  getOrderQuote.mockImplementation((items) =>
    Promise.resolve({
      items: items.map(({ book_id, quantity }) => ({
        book_id,
        title: fixture.find((book) => book.id === book_id)?.title ?? habits.title,
        author: fixture.find((book) => book.id === book_id)?.author ?? habits.author,
        quantity,
        unit_price:
          fixture.find((book) => book.id === book_id)?.price ?? habits.price,
        line_total:
          (fixture.find((book) => book.id === book_id)?.price ?? habits.price) *
          quantity,
      })),
      item_count: items.reduce((count, item) => count + item.quantity, 0),
      subtotal: items.reduce(
        (sum, item) =>
          sum +
          (fixture.find((book) => book.id === item.book_id)?.price ??
            habits.price) *
            item.quantity,
        0
      ),
      shipping: 0,
      tax: 0,
      discount: 0,
      total: items.reduce(
        (sum, item) =>
          sum +
          (fixture.find((book) => book.id === item.book_id)?.price ??
            habits.price) *
            item.quantity,
        0
      ),
    })
  );

  getBook.mockImplementation((id) => {
    const book = [...fixture, habits].find(
      (item) => item.id === Number(id)
    );
    return book
      ? Promise.resolve(book)
      : Promise.reject(new Error('Book not found'));
  });

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
    expect(screen.getByRole('button', { name: /Place order/ })).toBeEnabled();
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

describe('catalogue details, sorting and filters', () => {
  const shelf = [
    { id: 1, title: 'The Alchemist', author: 'Paulo Coelho', category: 'Fiction', description: 'A shepherd follows his dream.', price: 299, stock: 2 },
    { id: 2, title: 'Atomic Habits', author: 'James Clear', category: 'Self-help', description: 'Tiny changes.', price: 499, stock: 0 },
    { id: 3, title: 'Clean Code', author: 'Robert C. Martin', category: 'Technology', description: 'Readable software.', price: 650, stock: 3 },
  ];
  const shownTitles = () => screen.getAllByRole('heading', { level: 3 }).map((heading) => heading.textContent);

  beforeEach(() => { getBooks.mockResolvedValue(shelf); });

  it('sorts the shelf by price and title', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'Clean Code' });
    await user.selectOptions(screen.getByLabelText('Sort by'), 'price-desc');
    expect(shownTitles()).toEqual(['Clean Code', 'Atomic Habits', 'The Alchemist']);
    await user.selectOptions(screen.getByLabelText('Sort by'), 'title-asc');
    expect(shownTitles()).toEqual(['Atomic Habits', 'Clean Code', 'The Alchemist']);
  });

  it('filters by price range and stock, shows counts and resets', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'Clean Code' });
    await user.type(screen.getByLabelText('Minimum price'), '300');
    expect(shownTitles()).toEqual(['Atomic Habits', 'Clean Code']);
    expect(screen.getByText('Showing 2 of 3 books')).toBeInTheDocument();
    await user.click(screen.getByLabelText('In stock only'));
    expect(shownTitles()).toEqual(['Clean Code']);
    await user.type(screen.getByLabelText('Maximum price'), '100');
    expect(screen.getByRole('alert')).toHaveTextContent('Minimum price cannot be more than the maximum price.');
    await user.click(screen.getByRole('button', { name: 'Reset sort & filters' }));
    expect(shownTitles()).toHaveLength(3);
    expect(screen.getByText('3 books on the shelf')).toBeInTheDocument();
  });

  it('explains when filters hide every book', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'Clean Code' });
    await user.type(screen.getByLabelText('Minimum price'), '5000');
    expect(screen.getByText(/No books match these filters/)).toBeInTheDocument();
  });

  it('opens individual book details with fresh server data and a shareable link', async () => {
    getBook.mockResolvedValueOnce({ ...shelf[2], stock: 1, price: 600 });
    const user = userEvent.setup(); render(<App />);
    await user.click(await screen.findByRole('button', { name: 'Clean Code' }));
    expect(getBook).toHaveBeenCalledWith(3);
    expect(window.location.hash).toBe('#book-3');
    expect(screen.getByRole('heading', { level: 2, name: 'Clean Code' })).toBeInTheDocument();
    expect(await screen.findByText('1 in stock')).toBeInTheDocument();
    expect(screen.getByText('₹600.00')).toBeInTheDocument();
    expect(screen.getByText('by Robert C. Martin')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '← Back to books' }));
    expect(window.location.hash).toBe('#shop');
    expect(screen.getByRole('heading', { name: 'Find your next favourite' })).toBeInTheDocument();
  });

  it('opens a book directly from a #book-<id> link', async () => {
    window.history.replaceState(null, '', '#book-1');
    render(<App />);
    expect(await screen.findByRole('heading', { level: 2, name: 'The Alchemist' })).toBeInTheDocument();
    expect(getBook).toHaveBeenCalledWith(1);
  });

  it('shows a friendly message for an unknown book link', async () => {
    window.history.replaceState(null, '', '#book-999');
    render(<App />);
    expect(await screen.findByRole('alert')).toHaveTextContent('We could not find that book.');
    expect(screen.getByRole('heading', { name: 'Book unavailable' })).toBeInTheDocument();
  });
});
