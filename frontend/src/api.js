const base = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');

async function request(path, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(`${base}${path}`, {
      ...options,
      signal: controller.signal,
      headers: { 'Content-Type': 'application/json', ...options.headers },
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      const detail = body.detail;
      const message = typeof detail === 'string' ? detail : detail?.message || 'The request could not be completed.';
      throw new Error(message);
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

export const getBooks = () => request('/api/books');
export const placeOrder = (order, idempotencyKey) => request('/api/orders', {
  method: 'POST', body: JSON.stringify(order), headers: { 'Idempotency-Key': idempotencyKey },
});
