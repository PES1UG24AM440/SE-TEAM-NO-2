// Helpers for the listing form used by the seller (#72) and admin (#73) dashboards.

export const emptyListing = { title: '', author: '', category: '', description: '', price: '', stock: '' };

// Same limits as ListingRequest.java. The server always re-checks; this only gives faster feedback.
export function validateListing(listing) {
  const errors = {};
  const text = (key) => String(listing[key] ?? '').trim();
  if (!text('title')) errors.title = 'Title is required.';
  else if (text('title').length > 200) errors.title = 'Title must be 200 characters or fewer.';
  if (!text('author')) errors.author = 'Author is required.';
  else if (text('author').length > 160) errors.author = 'Author must be 160 characters or fewer.';
  if (!text('category')) errors.category = 'Category is required.';
  else if (text('category').length > 100) errors.category = 'Category must be 100 characters or fewer.';
  if (text('description').length > 2000) errors.description = 'Description must be 2000 characters or fewer.';
  const price = text('price');
  if (!price) errors.price = 'Price is required.';
  else if (!/^\d+(\.\d{1,2})?$/.test(price) || Number(price) <= 0) errors.price = 'Enter a price greater than 0 with at most 2 decimals.';
  const stock = text('stock');
  if (!stock) errors.stock = 'Stock is required.';
  else if (!/^\d+$/.test(stock) || Number(stock) > 100000) errors.stock = 'Enter a whole number from 0 to 100000.';
  return errors;
}

export function toListingPayload(listing) {
  return {
    title: listing.title.trim(),
    author: listing.author.trim(),
    category: listing.category.trim(),
    description: listing.description.trim(),
    price: Number(listing.price),
    stock: Number(listing.stock),
  };
}

export function listingFromBook(book) {
  return {
    title: book.title ?? '', author: book.author ?? '', category: book.category ?? '',
    description: book.description ?? '', price: String(book.price ?? ''), stock: String(book.stock ?? ''),
  };
}
