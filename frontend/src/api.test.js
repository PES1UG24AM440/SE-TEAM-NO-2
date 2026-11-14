import { afterEach, describe, expect, it, vi } from 'vitest';
import { getBook } from './api.js';

afterEach(() => vi.unstubAllGlobals());
const respond = (status, body) => vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: status < 400, status, json: () => Promise.resolve(body) }));

describe('api request errors', () => {
  it('maps Spring default 404 bodies to a not-found error with status', async () => {
    respond(404, { timestamp: 'x', status: 404, error: 'Not Found', path: '/api/books/999' });
    await expect(getBook(999)).rejects.toMatchObject({ message: 'Not found.', status: 404 });
  });
  it('uses the detail message from OrderException responses', async () => {
    respond(409, { detail: { code: 'insufficient_stock', message: 'Only 2 copies left.' } });
    await expect(getBook(1)).rejects.toMatchObject({ message: 'Only 2 copies left.', status: 409 });
  });
  it('returns the book on success', async () => {
    respond(200, { id: 1, title: 'The Alchemist' });
    await expect(getBook(1)).resolves.toEqual({ id: 1, title: 'The Alchemist' });
  });
});
