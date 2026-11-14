// Client-side sorting and filtering for the catalogue shelf.
// The Spring API already handles text search and category; these helpers refine that result.

export const sortOptions = [
  { value: 'featured', label: 'Featured' },
  { value: 'title-asc', label: 'Title: A to Z' },
  { value: 'title-desc', label: 'Title: Z to A' },
  { value: 'price-asc', label: 'Price: low to high' },
  { value: 'price-desc', label: 'Price: high to low' },
  { value: 'author-asc', label: 'Author: A to Z' },
];

export const emptyFilters = { minPrice: '', maxPrice: '', inStockOnly: false };

const byText = (key) => (a, b) => a[key].localeCompare(b[key], 'en', { sensitivity: 'base' });
const comparators = {
  'title-asc': byText('title'),
  'title-desc': (a, b) => byText('title')(b, a),
  'price-asc': (a, b) => a.price - b.price || byText('title')(a, b),
  'price-desc': (a, b) => b.price - a.price || byText('title')(a, b),
  'author-asc': (a, b) => byText('author')(a, b) || byText('title')(a, b),
};

export function sortBooks(books, sort) {
  const compare = comparators[sort];
  return compare ? [...books].sort(compare) : books;
}

const toBound = (value) => {
  if (value === '' || value === null || value === undefined) return null;
  const number = Number(value);
  return Number.isFinite(number) && number >= 0 ? number : null;
};

export function priceFilterError({ minPrice, maxPrice }) {
  if (minPrice !== '' && toBound(minPrice) === null) return 'Minimum price must be zero or more.';
  if (maxPrice !== '' && toBound(maxPrice) === null) return 'Maximum price must be zero or more.';
  const min = toBound(minPrice);
  const max = toBound(maxPrice);
  if (min !== null && max !== null && min > max) return 'Minimum price cannot be more than the maximum price.';
  return '';
}

export function filterBooks(books, filters) {
  if (priceFilterError(filters)) return books;
  const min = toBound(filters.minPrice);
  const max = toBound(filters.maxPrice);
  return books.filter((book) => (min === null || book.price >= min)
    && (max === null || book.price <= max)
    && (!filters.inStockOnly || book.stock > 0));
}

export const hasActiveFilters = (filters) => filters.minPrice !== '' || filters.maxPrice !== '' || filters.inStockOnly;

const detailsHashPattern = /^#book-(\d+)$/;
export const bookHash = (id) => `#book-${id}`;
export function bookIdFromHash(hash) {
  const match = detailsHashPattern.exec(hash || '');
  return match ? Number(match[1]) : null;
}
