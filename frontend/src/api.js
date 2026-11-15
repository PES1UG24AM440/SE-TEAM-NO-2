const base = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');

async function request(path, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(`${base}${path}`, {
      ...options,
      credentials: 'include',
      signal: controller.signal,
      headers: { 'Content-Type': 'application/json', ...options.headers },
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      const detail = body.detail;
      // OrderException responses carry { detail }, while Spring's default error body (e.g. a 404 from
      // GET /api/books/{id}) only has { status, error, message }; keep the HTTP status for callers.
      const fallback = response.status === 404 ? 'Not found.' : 'The request could not be completed.';
      const message = typeof detail === 'string' ? detail : detail?.message || fallback;
      throw Object.assign(new Error(message), { status: response.status, code: detail?.code, fields: detail?.fields || [] });
    }
    return body;
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('The bookstore server took too long to respond. Please try again.');
    if (error instanceof TypeError) throw new Error('Could not reach the bookstore server. Check your connection and try again.');
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

export const getBooks = ({ q = '', category = '' } = {}) => {
  const params = new URLSearchParams();
  if (q.trim()) params.set('q', q.trim());
  if (category && category !== 'All') params.set('category', category);
  const query = params.toString();
  return request(`/api/books${query ? `?${query}` : ''}`);
};
export const getBook = (id) => request(`/api/books/${encodeURIComponent(id)}`);

export const getOrderQuote = (items) =>
  request('/api/orders/quote', {
    method: 'POST',
    body: JSON.stringify({ items }),
  });

export const getCart = () => request('/api/cart');

export const updateCartItem = (bookId, quantity) =>
  request(`/api/cart/items/${encodeURIComponent(bookId)}`, {
    method: 'PUT',
    body: JSON.stringify({ quantity }),
  });

export const removeCartItem = (bookId) =>
  request(`/api/cart/items/${encodeURIComponent(bookId)}`, {
    method: 'DELETE',
  });

export const clearCart = () =>
  request('/api/cart', {
    method: 'DELETE',
  });

export const placeOrder = (order, idempotencyKey) =>
  request('/api/orders', {
    method: 'POST',
    body: JSON.stringify(order),
    headers: { 'Idempotency-Key': idempotencyKey },
  });

export const getOrder = (id) =>
  request(`/api/orders/${encodeURIComponent(id)}`);

// Accounts and roles (login is session-based; the browser sends the session cookie automatically).
export const register = (account) => request('/api/auth/register', { method: 'POST', body: JSON.stringify(account) });
export const login = (email, password) => request('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
export const logout = () => request('/api/auth/logout', { method: 'POST' });
export const getMe = () => request('/api/auth/me');

// Seller listing management (#72). The server only lets a seller change their own listings.
export const getMyListings = () => request('/api/seller/books');
export const createListing = (listing) => request('/api/seller/books', { method: 'POST', body: JSON.stringify(listing) });
export const updateListing = (id, listing) => request(`/api/seller/books/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(listing) });
export const deactivateListing = (id) => request(`/api/seller/books/${encodeURIComponent(id)}`, { method: 'DELETE' });
export const activateListing = (id) => request(`/api/seller/books/${encodeURIComponent(id)}/activate`, { method: 'POST' });
