import { describe, expect, it } from 'vitest';
import { bookHash, bookIdFromHash, emptyFilters, filterBooks, hasActiveFilters, priceFilterError, sortBooks } from './catalogue.js';

const books = [
  { id: 1, title: 'the Hobbit', author: 'Tolkien', price: 399, stock: 6 },
  { id: 2, title: 'Atomic Habits', author: 'Clear', price: 450, stock: 0 },
  { id: 3, title: 'Clean Code', author: 'Martin', price: 650, stock: 3 },
  { id: 4, title: 'Animal Farm', author: 'Orwell', price: 399, stock: 9 },
];
const titles = (list) => list.map((book) => book.title);

describe('sortBooks', () => {
  it('keeps server order for featured and does not mutate the input', () => {
    expect(sortBooks(books, 'featured')).toBe(books);
    sortBooks(books, 'price-desc');
    expect(titles(books)).toEqual(['the Hobbit', 'Atomic Habits', 'Clean Code', 'Animal Farm']);
  });
  it('sorts by title case-insensitively in both directions', () => {
    expect(titles(sortBooks(books, 'title-asc'))).toEqual(['Animal Farm', 'Atomic Habits', 'Clean Code', 'the Hobbit']);
    expect(titles(sortBooks(books, 'title-desc'))).toEqual(['the Hobbit', 'Clean Code', 'Atomic Habits', 'Animal Farm']);
  });
  it('sorts by price with title as a stable tie-breaker', () => {
    expect(titles(sortBooks(books, 'price-asc'))).toEqual(['Animal Farm', 'the Hobbit', 'Atomic Habits', 'Clean Code']);
    expect(titles(sortBooks(books, 'price-desc'))).toEqual(['Clean Code', 'Atomic Habits', 'Animal Farm', 'the Hobbit']);
  });
  it('sorts by author', () => {
    expect(sortBooks(books, 'author-asc').map((book) => book.author)).toEqual(['Clear', 'Martin', 'Orwell', 'Tolkien']);
  });
});

describe('filterBooks', () => {
  it('returns everything with no filters', () => {
    expect(filterBooks(books, emptyFilters)).toHaveLength(4);
    expect(hasActiveFilters(emptyFilters)).toBe(false);
  });
  it('applies inclusive price bounds', () => {
    expect(titles(filterBooks(books, { ...emptyFilters, minPrice: '399', maxPrice: '450' }))).toEqual(['the Hobbit', 'Atomic Habits', 'Animal Farm']);
    expect(titles(filterBooks(books, { ...emptyFilters, minPrice: '500' }))).toEqual(['Clean Code']);
  });
  it('hides out-of-stock books when requested', () => {
    expect(titles(filterBooks(books, { ...emptyFilters, inStockOnly: true }))).not.toContain('Atomic Habits');
  });
  it('rejects an inverted or negative range and leaves the list unfiltered', () => {
    const inverted = { ...emptyFilters, minPrice: '600', maxPrice: '100' };
    expect(priceFilterError(inverted)).toMatch(/cannot be more than/);
    expect(filterBooks(books, inverted)).toHaveLength(4);
    expect(priceFilterError({ ...emptyFilters, minPrice: '-5' })).toMatch(/zero or more/);
  });
});

describe('book detail hashes', () => {
  it('round-trips a book id and ignores other hashes', () => {
    expect(bookIdFromHash(bookHash(42))).toBe(42);
    expect(bookIdFromHash('#shop')).toBeNull();
    expect(bookIdFromHash('#book-abc')).toBeNull();
    expect(bookIdFromHash('')).toBeNull();
  });
});
