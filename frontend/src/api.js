const base = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');

export async function getBooks() {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(`${base}/api/books`, { signal: controller.signal });
    if (!response.ok) throw new Error('The book catalogue could not be loaded. Please try again.');
    return await response.json();
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('The bookstore server took too long to respond.');
    if (error instanceof TypeError) throw new Error('Could not reach the bookstore server.');
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}
