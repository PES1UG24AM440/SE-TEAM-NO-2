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
      throw Object.assign(new Error(message), { status: response.status });
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
export const placeOrder = (order, idempotencyKey) => request('/api/orders', {
  method: 'POST', body: JSON.stringify(order), headers: { 'Idempotency-Key': idempotencyKey },
});
export const getOrder = (id) => request(`/api/orders/${encodeURIComponent(id)}`);
