import { useCallback, useEffect, useMemo, useState } from 'react';
import { clearCart as clearCartRequest, getBook, getBooks, getCart, getOrder, placeOrder, removeCartItem, updateCartItem } from './api.js';
import { bookHash, bookIdFromHash, emptyFilters, filterBooks, hasActiveFilters, priceFilterError, sortBooks, sortOptions } from './catalogue.js';

const currency = (value) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(value);
const emptyForm = { name: '', email: '', phone: '', address_line: '', city: '', state: '', postal_code: '', country: '' };
const required = ['name', 'email', 'address_line', 'city', 'state', 'postal_code', 'country'];
const labels = { name: 'Full name', email: 'Email', phone: 'Phone (optional)', address_line: 'Address', city: 'City', state: 'State / region', postal_code: 'Postal code', country: 'Country' };
const wishlistStorageKey = 'fieldnotes-wishlist';
const legacyWishlistStorageKey = 'fieldnotes-books-wishlist';
function isBookRecord(book) {
  return book && (typeof book.id === 'number' || typeof book.id === 'string')
    && typeof book.title === 'string' && typeof book.author === 'string'
    && Number.isFinite(book.price) && Number.isFinite(book.stock);
}
function loadWishlist() {
  try {
    const stored = window.localStorage.getItem(wishlistStorageKey) ?? window.localStorage.getItem(legacyWishlistStorageKey);
    if (!stored) return [];
    const books = JSON.parse(stored);
    if (!Array.isArray(books)) throw new TypeError('Saved wishlist data must be an array.');
    return [...new Map(books.filter(isBookRecord).map((book) => [String(book.id), book])).values()];
  } catch (error) {
    console.error('Unable to load the saved wishlist.', error);
    return [];
  }
}
function renderBookCover(book, variant = 0) {
  return <div className={`cover cover-${variant % 4}`} role="img" aria-label={`Cover of ${book.title} by ${book.author}`}>
    <span className="cover-lines">{book.title.split(' ').slice(0, 3).join('\n')}</span>
    <span className="cover-author">{book.author}</span><span className="cover-orbit" aria-hidden="true" />
  </div>;
}
function renderWishlistButton(book, saved, onToggle) {
  return <button type="button" className={`wishlist-toggle${saved ? ' saved' : ''}`}
    aria-label={`${saved ? 'Remove' : 'Add'} ${book.title} ${saved ? 'from' : 'to'} wishlist`}
    aria-pressed={saved} onClick={() => onToggle(book)}>
    <span aria-hidden="true">{saved ? '♥' : '♡'}</span>
  </button>;
}
const orderIdsKey = 'fieldnotes-order-ids';
const readOrderIds = () => {
  try { const saved = JSON.parse(localStorage.getItem(orderIdsKey) || '[]'); return Array.isArray(saved) ? saved.filter((id) => typeof id === 'string' && id) : []; }
  catch { return []; }
};
const saveOrderId = (id) => {
  try { const saved = JSON.parse(localStorage.getItem(orderIdsKey) || '[]'); const ids = Array.isArray(saved) ? saved.filter((value) => typeof value === 'string' && value !== id) : []; localStorage.setItem(orderIdsKey, JSON.stringify([id, ...ids])); }
  catch { /* Order placement should still succeed if browser storage is unavailable. */ }
};

function validate(form) {
  const errors = {};
  required.forEach((key) => { if (!form[key].trim()) errors[key] = `${labels[key]} is required.`; });
  if (form.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) errors.email = 'Enter a valid email address.';
  if (form.phone.trim() && !/^\+?[0-9 ()-]{7,20}$/.test(form.phone.trim())) errors.phone = 'Enter a valid phone number.';
  return errors;
}

export default function App() {
  const [books, setBooks] = useState([]);
  const [known, setKnown] = useState({});
  const [categories, setCategories] = useState([]);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('All');
  const [sort, setSort] = useState('featured');
  const [filters, setFilters] = useState(emptyFilters);
  const [cart, setCart] = useState({});
  const [cartSubtotal, setCartSubtotal] = useState(0);
  const [wishlist, setWishlist] = useState(loadWishlist);
  const [view, setView] = useState('shop');
  const [selectedBook, setSelectedBook] = useState(null);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [detailsError, setDetailsError] = useState('');
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);
  const [cartLoading, setCartLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [paymentScenario, setPaymentScenario] = useState('success');
  const [confirmation, setConfirmation] = useState(null);
  const [orders, setOrders] = useState([]);
  const [ordersLoading, setOrdersLoading] = useState(false);
  const [lookupId, setLookupId] = useState('');
  const [lookupError, setLookupError] = useState('');
  const [lookupLoading, setLookupLoading] = useState(false);

  const remember = useCallback((list) => {
    setKnown((current) => ({ ...current, ...Object.fromEntries(list.map((book) => [book.id, book])) }));
    setCategories((current) => [...new Set([...current, ...list.map((book) => book.category).filter(Boolean)])].sort());
    setWishlist((current) => current.map((savedBook) => {
      const currentBook = list.find((book) => String(book.id) === String(savedBook.id));
      return currentBook ? { ...savedBook, ...currentBook } : savedBook;
    }));
  }, []);

  const applyCart = useCallback((response) => {
    const cartBooks = response.items.map((item) => ({
      id: item.book_id,
      title: item.title,
      author: item.author,
      category: item.category,
      description: item.description,
      price: item.unit_price,
      stock: item.stock,
    }));
    remember(cartBooks);
    setCart(Object.fromEntries(response.items.map((item) => [item.book_id, item.quantity])));
    setCartSubtotal(response.subtotal);
  }, [remember]);

  useEffect(() => {
    getCart()
      .then(applyCart)
      .catch((error) => setNotice(error.message))
      .finally(() => setCartLoading(false));
  }, [applyCart]);

  useEffect(() => {
    let active = true;
    const timer = setTimeout(() => {
      getBooks({ q: query, category })
        .then((list) => { if (active) { setBooks(list); remember(list); } })
        .catch((error) => { if (active) setNotice(error.message); })
        .finally(() => { if (active) setLoading(false); });
    }, query ? 250 : 0);
    return () => { active = false; clearTimeout(timer); };
  }, [query, category, remember]);

  useEffect(() => {
    try { window.localStorage.setItem(wishlistStorageKey, JSON.stringify(wishlist)); }
    catch (error) {
      console.error('Unable to save the wishlist on this device.', error);
      setNotice('Your wishlist could not be saved on this device.');
    }
  }, [wishlist]);

  // Cart lines are looked up in `known` so items stay in the bag when a search or category filter hides them from the shelf.
  const items = useMemo(() => Object.entries(cart).map(([id, quantity]) => ({
    book: known[id], quantity,
  })).filter(({ book }) => book), [known, cart]);
  const total = cartSubtotal;
  const filterError = priceFilterError(filters);
  const visibleBooks = useMemo(() => sortBooks(filterBooks(books, filters), sort), [books, filters, sort]);
  const filtersActive = hasActiveFilters(filters) || sort !== 'featured';
  const updateFilter = (key, value) => setFilters((current) => ({ ...current, [key]: value }));
  function resetFilters() { setFilters(emptyFilters); setSort('featured'); }

  function toggleWishlist(book) {
    const saved = wishlist.some((savedBook) => String(savedBook.id) === String(book.id));
    setWishlist((current) => saved
      ? current.filter((savedBook) => String(savedBook.id) !== String(book.id))
      : current.some((savedBook) => String(savedBook.id) === String(book.id)) ? current : [...current, book]);
    setNotice(`${book.title} ${saved ? 'removed from' : 'added to'} your wishlist.`);
  }

  // Book details are deep-linkable (#book-<id>) and always refreshed from GET /api/books/{id}
  // so price and stock reflect the server, not a stale card.
  const openBook = useCallback((id, preview = null) => {
    setSelectedBook(preview); setDetailsError(''); setDetailsLoading(true); setView('details');
    getBook(id)
      .then((book) => {
        if (!isBookRecord(book)) { if (!preview) throw Object.assign(new Error('Not found.'), { status: 404 }); return; }
        setSelectedBook(book); remember([book]);
      })
      .catch((error) => {
        setSelectedBook(null);
        setDetailsError(error.status === 404 || /not found/i.test(error.message) ? 'We could not find that book. It may have been removed from the shelf.' : error.message);
      })
      .finally(() => setDetailsLoading(false));
  }, [remember]);

  function showDetails(book) {
    if (window.location.hash !== bookHash(book.id)) window.history.pushState(null, '', bookHash(book.id));
    openBook(book.id, book);
  }
  function backToShelf() {
    if (bookIdFromHash(window.location.hash) !== null) window.history.pushState(null, '', '#shop');
    setView('shop');
  }

  useEffect(() => {
    const syncFromHash = () => {
      const id = bookIdFromHash(window.location.hash);
      if (id !== null) openBook(id);
      else setView((current) => (current === 'details' ? 'shop' : current));
    };
    if (bookIdFromHash(window.location.hash) !== null) syncFromHash();
    window.addEventListener('popstate', syncFromHash);
    return () => window.removeEventListener('popstate', syncFromHash);
  }, [openBook]);

  useEffect(() => {
    // Leaving the details page by any other route must not leave a stale #book-<id> in the address bar.
    if (view !== 'details' && bookIdFromHash(window.location.hash) !== null) window.history.replaceState(null, '', '#shop');
  }, [view]);

  function add(book) {
    setNotice('');
    const quantity = cart[book.id] || 0;
    if (quantity >= book.stock) { setNotice(`Only ${book.stock} ${book.stock === 1 ? 'copy is' : 'copies are'} available for ${book.title}.`); return; }
    updateCartItem(book.id, quantity + 1)
      .then(applyCart)
      .then(() => setNotice(`${book.title} added to your cart.`))
      .catch((error) => setNotice(error.message));
  }
  function change(book, delta) {
    setNotice('');
    const current = cart[book.id] || 0;
    const next = current + delta;
    if (next > book.stock) { setNotice(`Only ${book.stock} ${book.stock === 1 ? 'copy is' : 'copies are'} available for ${book.title}.`); return; }
    const mutation = next <= 0
      ? removeCartItem(book.id)
      : updateCartItem(book.id, next);
    mutation.then(applyCart).catch((error) => setNotice(error.message));
  }
  function remove(book) {
    removeCartItem(book.id)
      .then(applyCart)
      .then(() => setNotice(`${book.title} removed from your cart.`))
      .catch((error) => setNotice(error.message));
  }
  function clearBag() {
    clearCartRequest()
      .then(applyCart)
      .then(() => setNotice('Your bag has been cleared.'))
      .catch((error) => setNotice(error.message));
  }
  function startCheckout() { setErrors({}); setNotice(''); setView('checkout'); }
  function submit(event) {
    event.preventDefault();
    if (!items.length) { setNotice('Your cart is empty. Add a book before checking out.'); setView('shop'); return; }
    const nextErrors = validate(form);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) { setNotice('Please correct the highlighted fields.'); return; }
    setSubmitting(true); setNotice('');
    const key = crypto.randomUUID();
    placeOrder({ items: items.map(({ book, quantity }) => ({ book_id: book.id, quantity })), customer: form, payment_scenario: paymentScenario }, key)
      .then((order) => {
        if (order.order_id) saveOrderId(order.order_id);
        setConfirmation(order); setCart({}); setView('confirmation'); setForm(emptyForm);
        setCartSubtotal(0);
        return clearCartRequest().catch((error) => {
          setNotice(`Order placed, but the cart could not be cleared: ${error.message}`);
        });
      })
      .then(() => {
        return getBooks({ q: query, category }).then((list) => { setBooks(list); remember(list); }).catch(() => {});
      })
      .catch((error) => setNotice(error.message))
      .finally(() => setSubmitting(false));
  }

  useEffect(() => {
    if (view !== 'orders') return undefined;
    let active = true;
    const ids = readOrderIds();
    setOrdersLoading(true);
    Promise.all(ids.map((id) => getOrder(id)
      .then((order) => ({ id, order }))
      .catch((error) => ({ id, error: error.message || 'Could not retrieve this order.' }))))
      .then((results) => { if (active) setOrders(results); })
      .finally(() => { if (active) setOrdersLoading(false); });
    return () => { active = false; };
  }, [view]);

  function lookupOrder(event) {
    event.preventDefault();
    const id = lookupId.trim();
    if (!id) { setLookupError('Enter an order ID.'); return; }
    setLookupLoading(true); setLookupError('');
    getOrder(id).then((order) => {
      setOrders((current) => [{ id, order }, ...current.filter((entry) => entry.id !== id)]);
      saveOrderId(order.order_id || id);
    }).catch((error) => {
      setLookupError(error.message === 'Order not found.' || /not found/i.test(error.message)
        ? "We couldn't find an order with that ID."
        : error.message || 'Could not retrieve this order.');
    }).finally(() => setLookupLoading(false));
  }

  function orderCard(order, key) {
    return <article className="order-card" key={key}><div className="order-heading"><div><span>Order ID</span><strong>{order.order_id}</strong></div><div><span>Status</span><strong>{order.status}</strong></div><div><span>Total</span><strong>{currency(order.total)}</strong></div></div><div className="order-items">{(order.items || []).map((item) => <div className="summary-line" key={item.book_id}><span>{item.title} <small>× {item.quantity}</small></span><span>{currency(item.line_total)}</span></div>)}</div></article>;
  }

  return <div className="site-shell">
    <header className="topbar"><a className="brand" href="#shop" onClick={() => setView('shop')} aria-label="Fieldnotes Books home"><span className="brand-mark">F</span><span>fieldnotes<span className="brand-light">books</span></span></a>
      <nav aria-label="Main navigation"><button className={view === 'shop' ? 'nav-link active' : 'nav-link'} onClick={backToShelf}>Browse books</button><button className={view === 'wishlist' ? 'nav-link wishlist-nav active' : 'nav-link wishlist-nav'} onClick={() => setView('wishlist')} aria-label={`Wishlist, ${wishlist.length} saved ${wishlist.length === 1 ? 'book' : 'books'}`}><span>Wishlist</span><span className="cart-count">{wishlist.length}</span></button><button className={view === 'orders' ? 'nav-link active' : 'nav-link'} onClick={() => setView('orders')}>Orders</button><button className="cart-link" onClick={() => setView('cart')} aria-label={`Shopping bag, ${items.reduce((n, item) => n + item.quantity, 0)} items`}><span>Bag</span><span className="cart-count">{items.reduce((n, item) => n + item.quantity, 0)}</span></button></nav>
    </header>
    <main>
      <section className="hero"><p className="eyebrow">A good book finds its way</p><h1>Stories for the<br /><em>in-between moments.</em></h1><p className="hero-copy">A small, thoughtful shelf of books to keep you curious.</p><div className="hero-stamp" aria-hidden="true">READ<br />MORE</div></section>
      {notice && <div className="notice" role="status">{notice}</div>}
      {(loading || cartLoading) && <p className="loading" role="status">Loading the bookshelf…</p>}
      {!loading && !cartLoading && view === 'shop' && <section className="catalog" aria-labelledby="catalog-title"><div className="section-heading"><div><p className="eyebrow">The collection</p><h2 id="catalog-title">Find your next favourite</h2></div><span className="book-count" role="status">{visibleBooks.length === books.length ? `${books.length} ${books.length === 1 ? 'book' : 'books'} on the shelf` : `Showing ${visibleBooks.length} of ${books.length} books`}</span></div>
        <div className="catalog-tools"><label className="search-box" htmlFor="book-search"><span className="visually-hidden">Search books</span><input id="book-search" type="search" placeholder="Search by title, author or topic" value={query} onChange={(event) => setQuery(event.target.value)} /></label>
          <div className="category-filter" role="group" aria-label="Filter by category">{['All', ...categories].map((name) => <button key={name} className={name === category ? 'chip active' : 'chip'} aria-pressed={name === category} onClick={() => setCategory(name)}>{name}</button>)}</div></div>
        <div className="refine-bar" role="group" aria-label="Sort and filter books">
          <label className="refine-field" htmlFor="book-sort">Sort by<select id="book-sort" value={sort} onChange={(event) => setSort(event.target.value)}>{sortOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
          <fieldset className="price-range"><legend>Price (₹)</legend>
            <label htmlFor="min-price"><span className="visually-hidden">Minimum price</span><input id="min-price" type="number" inputMode="numeric" min="0" step="1" placeholder="Min" value={filters.minPrice} aria-invalid={Boolean(filterError)} aria-describedby={filterError ? 'price-error' : undefined} onChange={(event) => updateFilter('minPrice', event.target.value)} /></label>
            <span aria-hidden="true">–</span>
            <label htmlFor="max-price"><span className="visually-hidden">Maximum price</span><input id="max-price" type="number" inputMode="numeric" min="0" step="1" placeholder="Max" value={filters.maxPrice} aria-invalid={Boolean(filterError)} aria-describedby={filterError ? 'price-error' : undefined} onChange={(event) => updateFilter('maxPrice', event.target.value)} /></label>
          </fieldset>
          <label className="stock-toggle" htmlFor="in-stock-only"><input id="in-stock-only" type="checkbox" checked={filters.inStockOnly} onChange={(event) => updateFilter('inStockOnly', event.target.checked)} />In stock only</label>
          {filtersActive && <button type="button" className="text-button" onClick={resetFilters}>Reset sort &amp; filters</button>}
        </div>
        {filterError && <p className="field-error filter-error" id="price-error" role="alert">{filterError}</p>}
        {visibleBooks.length === 0 ? <p className="empty-catalog">{books.length > 0 ? 'No books match these filters. Try widening the price range or resetting the filters.' : query || category !== 'All' ? 'No books match your search. Try a different word or category.' : 'The catalogue is unavailable right now. Please refresh and try again.'}</p> : <div className="book-grid">{visibleBooks.map((book, index) => <article className="book-card" key={book.id}><div className={`cover cover-${index % 4}`} aria-hidden="true"><span className="cover-lines">{book.title.split(' ').slice(0, 3).join('\n')}</span><span className="cover-author">{book.author}</span><span className="cover-orbit" /></div><div className="book-meta"><div>{book.category && <span className="category-tag">{book.category}</span>}<h3><button className="book-title" onClick={() => showDetails(book)}>{book.title}</button></h3><p>{book.author}</p>{book.description && <p className="book-description">{book.description}</p>}</div><span className="price">{currency(book.price)}</span></div><div className="book-actions"><span className={book.stock > 0 ? 'stock' : 'stock sold-out'}>{book.stock > 0 ? `${book.stock} in stock` : 'Out of stock'}</span><div className="card-actions">{renderWishlistButton(book, wishlist.some((savedBook) => String(savedBook.id) === String(book.id)), toggleWishlist)}<button className="add-button" onClick={() => add(book)} disabled={book.stock === 0}>Add to bag <span aria-hidden="true">+</span></button></div></div></article>)}</div>}
      </section>}
      {!loading && view === 'wishlist' && <section className="wishlist-page" aria-labelledby="wishlist-title">
        <div className="section-heading"><div><p className="eyebrow">Saved for later</p><h2 id="wishlist-title">{wishlist.length ? 'Your Wishlist' : 'Your Wishlist is Empty!'}</h2></div><span className="book-count">{wishlist.length} {wishlist.length === 1 ? 'book' : 'books'} saved</span></div>
        {wishlist.length === 0 ? <div className="empty-state wishlist-empty"><span className="empty-icon" aria-hidden="true">♡</span><p>You haven't saved any books yet. Explore our collection and save your favourite books.</p><button className="primary-button" onClick={() => setView('shop')}>Explore Books</button></div>
          : <div className="wishlist-list">{wishlist.map((book, index) => <article className="wishlist-row" key={book.id}>
            {renderBookCover(book, index)}
            <div className="wishlist-book-info">{book.category && <span className="category-tag">{book.category}</span>}<h3><button className="book-title" onClick={() => showDetails(book)}>{book.title}</button></h3><p>{book.author}</p><p className="price">{currency(book.price)}</p><span className={book.stock > 0 ? 'stock' : 'stock sold-out'}>{book.stock > 0 ? `${book.stock} in stock` : 'Out of stock'}</span></div>
            <div className="wishlist-actions"><button className="primary-button" onClick={() => add(book)} disabled={book.stock === 0}>Add to Cart</button><button className="text-button" aria-label={`Remove ${book.title} from wishlist`} onClick={() => toggleWishlist(book)}>Remove</button></div>
          </article>)}</div>}
      </section>}
      {view === 'details' && <section className="book-details" aria-labelledby="details-title" aria-busy={detailsLoading}>
        <button className="back-link back-top" onClick={backToShelf}>← Back to books</button>
        {detailsLoading && !selectedBook && <p className="loading" role="status">Opening this book…</p>}
        {detailsError && <div className="empty-state details-missing"><h2 id="details-title">Book unavailable</h2><p role="alert">{detailsError}</p><button className="primary-button" onClick={backToShelf}>Browse books</button></div>}
        {selectedBook && <div className="details-content">{renderBookCover(selectedBook, selectedBook.id)}
          <div className="details-info">{selectedBook.category && <span className="category-tag">{selectedBook.category}</span>}<p className="eyebrow">A book to keep</p><h2 id="details-title">{selectedBook.title}</h2><p className="details-author">by {selectedBook.author}</p>{selectedBook.description && <p className="details-description">{selectedBook.description}</p>}<strong className="details-price">{currency(selectedBook.price)}</strong><p className={selectedBook.stock > 0 ? 'stock' : 'stock sold-out'}>{selectedBook.stock > 0 ? `${selectedBook.stock} in stock` : 'Out of stock'}</p><div className="details-actions">{renderWishlistButton(selectedBook, wishlist.some((book) => String(book.id) === String(selectedBook.id)), toggleWishlist)}<button className="primary-button" onClick={() => add(selectedBook)} disabled={selectedBook.stock === 0}>Add to bag</button></div>{detailsLoading && <p className="details-refresh" role="status">Checking latest price and stock…</p>}</div>
        </div>}
      </section>}
      {!loading && !cartLoading && view === 'cart' && <section className="checkout-layout"><div><p className="eyebrow">Your selection</p><h2>Your bag</h2>{items.length === 0 ? <div className="empty-state"><span className="empty-icon">✳</span><h3>Your bag is waiting for a story.</h3><p>Browse the shelf and add a book you love.</p><button className="primary-button" onClick={() => setView('shop')}>Continue shopping</button></div> : <><div className="cart-list">{items.map(({ book, quantity }) => <article className="cart-row" key={book.id}><div className={`mini-cover cover-${book.id % 4}`} aria-hidden="true">{book.title.slice(0, 1)}</div><div className="cart-description"><h3>{book.title}</h3><p>{book.author} · {currency(book.price)} each</p><button className="text-button" onClick={() => remove(book)}>Remove</button></div><div className="quantity" aria-label={`Quantity for ${book.title}`}><button aria-label={`Decrease ${book.title} quantity`} onClick={() => change(book, -1)}>−</button><output aria-label={`${book.title} quantity`}>{quantity}</output><button aria-label={`Increase ${book.title} quantity`} onClick={() => change(book, 1)} disabled={quantity >= book.stock}>+</button></div><strong className="line-total">{currency(book.price * quantity)}</strong></article>)}</div><button className="text-button" onClick={clearBag}>Clear bag</button></>}</div>
        {items.length > 0 && <aside className="summary-card"><p className="eyebrow">At a glance</p><h3>Order summary</h3>{items.map(({ book, quantity }) => <div className="summary-line" key={book.id}><span>{book.title} <small>× {quantity}</small></span><span>{currency(book.price * quantity)}</span></div>)}<div className="summary-total"><span>Estimated total</span><strong>{currency(total)}</strong></div><p className="summary-note">Final total is calculated by the bookstore at checkout.</p><button className="primary-button wide" onClick={startCheckout}>Continue to checkout <span aria-hidden="true">→</span></button><button className="back-link" onClick={() => setView('shop')}>← Continue shopping</button></aside>}</section>}
      {!loading && !cartLoading && view === 'checkout' && <section className="checkout-layout"><div><button className="back-link back-top" onClick={() => setView('cart')}>← Back to your bag</button><p className="eyebrow">Almost yours</p><h2>Delivery details</h2>{items.length === 0 ? <div className="empty-state"><h3>Your bag is empty.</h3><button className="primary-button" onClick={() => setView('shop')}>Browse books</button></div> : <form className="checkout-form" onSubmit={submit} noValidate><div className="form-grid">{Object.keys(emptyForm).map((key) => <label className={`field ${key === 'address_line' ? 'full-field' : ''}`} key={key} htmlFor={`field-${key}`}>{labels[key]}{key !== 'phone' && <span className="required-mark" aria-hidden="true"> *</span>}<input id={`field-${key}`} type={key === 'email' ? 'email' : 'text'} autoComplete={key === 'name' ? 'name' : key === 'email' ? 'email' : key === 'phone' ? 'tel' : key === 'address_line' ? 'street-address' : 'address-level2'} value={form[key]} aria-invalid={Boolean(errors[key])} aria-describedby={errors[key] ? `error-${key}` : undefined} onChange={(event) => setForm((value) => ({ ...value, [key]: event.target.value }))} />{errors[key] && <span className="field-error" id={`error-${key}`}>{errors[key]}</span>}</label>)}</div><fieldset className="payment-choice"><legend>Payment simulator</legend><p>This demo uses a simulator. No card details are requested or stored.</p><label><input type="radio" name="payment" value="success" checked={paymentScenario === 'success'} onChange={() => setPaymentScenario('success')} /> Simulate successful payment</label><label><input type="radio" name="payment" value="failure" checked={paymentScenario === 'failure'} onChange={() => setPaymentScenario('failure')} /> Simulate declined payment</label></fieldset><button className="primary-button submit-button" disabled={submitting}>{submitting ? 'Placing your order…' : `Place order · ${currency(total)}`}</button></form>}</div>
        {items.length > 0 && <aside className="summary-card"><p className="eyebrow">Your books</p><h3>Order summary</h3>{items.map(({ book, quantity }) => <div className="summary-line" key={book.id}><span>{book.title} <small>× {quantity}</small></span><span>{currency(book.price * quantity)}</span></div>)}<div className="summary-total"><span>Estimated total</span><strong>{currency(total)}</strong></div><p className="summary-note">The server confirms the final amount and stock.</p></aside>}</section>}
      {!loading && !cartLoading && view === 'confirmation' && confirmation && <section className="confirmation"><div className="success-mark" aria-hidden="true">✓</div><p className="eyebrow">All set</p><h2>Thank you for your order.</h2><p>Your payment was approved by the simulator. Your books are on their way to the address you provided.</p><div className="confirmation-card"><div><span>Order ID</span><strong>{confirmation.order_id}</strong></div><div><span>Status</span><strong>{confirmation.status}</strong></div><div><span>Confirmed total</span><strong>{currency(confirmation.total)}</strong></div>{confirmation.items.map((item) => <div key={item.book_id}><span>{item.title} × {item.quantity}</span><strong>{currency(item.line_total)}</strong></div>)}</div><button className="primary-button" onClick={() => setView('shop')}>Back to the bookshelf</button></section>}
      {!loading && !cartLoading && view === 'orders' && <section className="orders-view"><div className="section-heading"><div><p className="eyebrow">Your bookshelf, in progress</p><h2>Order history</h2></div></div><form className="order-lookup" onSubmit={lookupOrder}><label htmlFor="order-lookup-id">Look up an order</label><div><input id="order-lookup-id" aria-label="Order ID" value={lookupId} onChange={(event) => setLookupId(event.target.value)} /><button className="primary-button" disabled={lookupLoading}>{lookupLoading ? 'Looking up…' : 'Find order'}</button></div>{lookupError && <p className="order-error" role="status">{lookupError}</p>}</form>{ordersLoading ? <p className="loading" role="status">Loading your orders…</p> : orders.length === 0 ? <div className="empty-state"><h3>No orders yet.</h3><button className="primary-button" onClick={() => setView('shop')}>Back to the shelf</button></div> : <div className="order-list">{orders.map(({ id, order, error }) => order ? orderCard(order, id) : <p className="order-error" role="status" key={id}>Could not load order {id}: {error}</p>)}</div>}</section>}
    </main>
    <footer><span>fieldnotes<span className="brand-light">books</span></span><span>Good books. Thoughtfully found.</span></footer>
  </div>;
}
