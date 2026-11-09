import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { getBooks, getOrderQuote, placeOrder } from './api.js';

const currency = (value) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(value);
const emptyForm = { name: '', email: '', phone: '', address_line: '', city: '', state: '', postal_code: '', country: '' };
const required = ['name', 'email', 'address_line', 'city', 'state', 'postal_code', 'country'];
const labels = { name: 'Full name', email: 'Email', phone: 'Phone (optional)', address_line: 'Address', city: 'City', state: 'State / region', postal_code: 'Postal code', country: 'Country' };

function validateField(key, rawValue) {
  const value = rawValue.trim();
  if (required.includes(key) && !value) return `${labels[key]} is required.`;
  if (key === 'name' && value.length > 120) return 'Full name must be 120 characters or fewer.';
  if (key === 'email' && value && (value.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value))) return 'Enter a valid email address.';
  if (key === 'phone' && value) {
    const digits = value.replace(/\D/g, '').length;
    if (!/^\+?[0-9 ()-]+$/.test(value) || digits < 7 || digits > 15 || value.length > 25) return 'Enter a valid phone number.';
  }
  if (key === 'address_line' && value.length > 200) return 'Address must be 200 characters or fewer.';
  if (['city', 'state'].includes(key) && value.length > 100) return `${labels[key]} must be 100 characters or fewer.`;
  if (key === 'country' && value.length > 100) return 'Country must be 100 characters or fewer.';
  if (key === 'postal_code' && (value.length > 20 || !/^[\p{L}\p{N}](?:[\p{L}\p{N} -]{0,18}[\p{L}\p{N}])?$/u.test(value))) return 'Enter a valid postal code.';
  return '';
}

function validate(form) {
  return Object.keys(form).reduce((errors, key) => {
    const message = validateField(key, form[key]);
    if (message) errors[key] = message;
    return errors;
  }, {});
}

export default function App() {
  const [books, setBooks] = useState([]);
  const [known, setKnown] = useState({});
  const [categories, setCategories] = useState([]);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('All');
  const [cart, setCart] = useState({});
  const [view, setView] = useState('shop');
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [quote, setQuote] = useState(null);
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [quoteError, setQuoteError] = useState('');
  const [quoteRefresh, setQuoteRefresh] = useState(0);
  const [paymentScenario, setPaymentScenario] = useState('success');
  const [confirmation, setConfirmation] = useState(null);
  const submitLock = useRef(false);
  const idempotencyAttempt = useRef(null);

  const remember = useCallback((list) => {
    setKnown((current) => ({ ...current, ...Object.fromEntries(list.map((book) => [book.id, book])) }));
    setCategories((current) => [...new Set([...current, ...list.map((book) => book.category).filter(Boolean)])].sort());
  }, []);

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

  // Cart lines are looked up in `known` so items stay in the bag when a search or category filter hides them from the shelf.
  const items = useMemo(() => Object.entries(cart).map(([id, quantity]) => ({
    book: known[id], quantity,
  })).filter(({ book }) => book), [known, cart]);
  const total = items.reduce((sum, { book, quantity }) => sum + book.price * quantity, 0);

  const quoteItems = useMemo(() => items.map(({ book, quantity }) => ({ book_id: book.id, quantity })), [items]);
  useEffect(() => {
    if (view !== 'checkout' || quoteItems.length === 0) {
      setQuote(null);
      setQuoteError('');
      setQuoteLoading(false);
      return undefined;
    }
    let active = true;
    setQuote(null);
    setQuoteError('');
    setQuoteLoading(true);
    getOrderQuote(quoteItems)
      .then((result) => { if (active) setQuote(result); })
      .catch((error) => { if (active) setQuoteError(error.message); })
      .finally(() => { if (active) setQuoteLoading(false); });
    return () => { active = false; };
  }, [view, quoteItems, quoteRefresh]);

  function add(book) {
    setNotice('');
    const quantity = cart[book.id] || 0;
    if (quantity >= book.stock) { setNotice(`Only ${book.stock} ${book.stock === 1 ? 'copy is' : 'copies are'} available for ${book.title}.`); return; }
    setCart((current) => ({ ...current, [book.id]: quantity + 1 }));
    setNotice(`${book.title} added to your cart.`);
  }
  function change(book, delta) {
    setNotice('');
    const current = cart[book.id] || 0;
    const next = current + delta;
    if (next > book.stock) { setNotice(`Only ${book.stock} ${book.stock === 1 ? 'copy is' : 'copies are'} available for ${book.title}.`); return; }
    setCart((value) => { const updated = { ...value }; if (next <= 0) delete updated[book.id]; else updated[book.id] = next; return updated; });
  }
  function remove(book) {
    setCart((current) => { const updated = { ...current }; delete updated[book.id]; return updated; });
    setNotice(`${book.title} removed from your cart.`);
  }
  function updateField(key, value) {
    setForm((current) => ({ ...current, [key]: value }));
    setErrors((current) => {
      if (!current[key]) return current;
      const message = validateField(key, value);
      if (message) return { ...current, [key]: message };
      const next = { ...current };
      delete next[key];
      return next;
    });
  }
  function startCheckout() { setErrors({}); setNotice(''); setView('checkout'); }
  function submit(event) {
    event.preventDefault();
    if (submitLock.current || submitting) return;
    if (!items.length) { setNotice('Your cart is empty. Add a book before checking out.'); setView('shop'); return; }
    const nextErrors = validate(form);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) { setNotice('Please correct the highlighted fields.'); return; }
    if (!quote || quoteLoading || quoteError) { setNotice('Please wait for the server to calculate your order total.'); return; }
    const customer = Object.fromEntries(Object.entries(form).map(([key, value]) => [key, value.trim()]));
    setForm(customer);
    const order = { items: quoteItems, customer, payment_scenario: paymentScenario };
    const signature = JSON.stringify(order);
    if (idempotencyAttempt.current?.signature !== signature) {
      idempotencyAttempt.current = { signature, key: crypto.randomUUID() };
    }
    submitLock.current = true;
    setSubmitting(true); setNotice('');
    placeOrder(order, idempotencyAttempt.current.key)
      .then((order) => {
        idempotencyAttempt.current = null;
        setConfirmation(order); setCart({}); setView('confirmation'); setForm(emptyForm);
        return getBooks({ q: query, category }).then((list) => { setBooks(list); remember(list); }).catch(() => {});
      })
      .catch((error) => setNotice(error.message))
      .finally(() => { submitLock.current = false; setSubmitting(false); });
  }

  return <div className="site-shell">
    <header className="topbar"><a className="brand" href="#shop" onClick={() => setView('shop')} aria-label="Fieldnotes Books home"><span className="brand-mark">F</span><span>fieldnotes<span className="brand-light">books</span></span></a>
      <nav aria-label="Main navigation"><button className={view === 'shop' ? 'nav-link active' : 'nav-link'} onClick={() => setView('shop')}>Browse books</button><button className="cart-link" onClick={() => setView('cart')} aria-label={`Shopping bag, ${items.reduce((n, item) => n + item.quantity, 0)} items`}><span>Bag</span><span className="cart-count">{items.reduce((n, item) => n + item.quantity, 0)}</span></button></nav>
    </header>
    <main>
      <section className="hero"><p className="eyebrow">A good book finds its way</p><h1>Stories for the<br /><em>in-between moments.</em></h1><p className="hero-copy">A small, thoughtful shelf of books to keep you curious.</p><div className="hero-stamp" aria-hidden="true">READ<br />MORE</div></section>
      {notice && <div className="notice" role="status">{notice}</div>}
      {loading && <p className="loading" role="status">Loading the bookshelf…</p>}
      {!loading && view === 'shop' && <section className="catalog" aria-labelledby="catalog-title"><div className="section-heading"><div><p className="eyebrow">The collection</p><h2 id="catalog-title">Find your next favourite</h2></div><span className="book-count">{books.length} {books.length === 1 ? 'book' : 'books'} on the shelf</span></div>
        <div className="catalog-tools"><label className="search-box" htmlFor="book-search"><span className="visually-hidden">Search books</span><input id="book-search" type="search" placeholder="Search by title, author or topic" value={query} onChange={(event) => setQuery(event.target.value)} /></label>
          <div className="category-filter" role="group" aria-label="Filter by category">{['All', ...categories].map((name) => <button key={name} className={name === category ? 'chip active' : 'chip'} aria-pressed={name === category} onClick={() => setCategory(name)}>{name}</button>)}</div></div>
        {books.length === 0 ? <p className="empty-catalog">{query || category !== 'All' ? 'No books match your search. Try a different word or category.' : 'The catalogue is unavailable right now. Please refresh and try again.'}</p> : <div className="book-grid">{books.map((book, index) => <article className="book-card" key={book.id}><div className={`cover cover-${index % 4}`} aria-hidden="true"><span className="cover-lines">{book.title.split(' ').slice(0, 3).join('\n')}</span><span className="cover-author">{book.author}</span><span className="cover-orbit" /></div><div className="book-meta"><div>{book.category && <span className="category-tag">{book.category}</span>}<h3>{book.title}</h3><p>{book.author}</p>{book.description && <p className="book-description">{book.description}</p>}</div><span className="price">{currency(book.price)}</span></div><div className="book-actions"><span className={book.stock > 0 ? 'stock' : 'stock sold-out'}>{book.stock > 0 ? `${book.stock} in stock` : 'Out of stock'}</span><button className="add-button" onClick={() => add(book)} disabled={book.stock === 0}>Add to bag <span aria-hidden="true">+</span></button></div></article>)}</div>}
      </section>}
      {!loading && view === 'cart' && <section className="checkout-layout"><div><p className="eyebrow">Your selection</p><h2>Your bag</h2>{items.length === 0 ? <div className="empty-state"><span className="empty-icon">✳</span><h3>Your bag is waiting for a story.</h3><p>Browse the shelf and add a book you love.</p><button className="primary-button" onClick={() => setView('shop')}>Continue shopping</button></div> : <div className="cart-list">{items.map(({ book, quantity }) => <article className="cart-row" key={book.id}><div className={`mini-cover cover-${book.id % 4}`} aria-hidden="true">{book.title.slice(0, 1)}</div><div className="cart-description"><h3>{book.title}</h3><p>{book.author} · {currency(book.price)} each</p><button className="text-button" onClick={() => remove(book)}>Remove</button></div><div className="quantity" aria-label={`Quantity for ${book.title}`}><button aria-label={`Decrease ${book.title} quantity`} onClick={() => change(book, -1)}>−</button><output aria-label={`${book.title} quantity`}>{quantity}</output><button aria-label={`Increase ${book.title} quantity`} onClick={() => change(book, 1)} disabled={quantity >= book.stock}>+</button></div><strong className="line-total">{currency(book.price * quantity)}</strong></article>)}</div>}</div>
        {items.length > 0 && <aside className="summary-card"><p className="eyebrow">At a glance</p><h3>Order summary</h3>{items.map(({ book, quantity }) => <div className="summary-line" key={book.id}><span>{book.title} <small>× {quantity}</small></span><span>{currency(book.price * quantity)}</span></div>)}<div className="summary-total"><span>Estimated total</span><strong>{currency(total)}</strong></div><p className="summary-note">Final total is calculated by the bookstore at checkout.</p><button className="primary-button wide" onClick={startCheckout}>Continue to checkout <span aria-hidden="true">→</span></button><button className="back-link" onClick={() => setView('shop')}>← Continue shopping</button></aside>}</section>}
      {!loading && view === 'checkout' && <section className="checkout-layout"><div><button className="back-link back-top" onClick={() => setView('cart')}>← Back to your bag</button><p className="eyebrow">Almost yours</p><h2>Delivery details</h2>{items.length === 0 ? <div className="empty-state"><h3>Your bag is empty.</h3><button className="primary-button" onClick={() => setView('shop')}>Browse books</button></div> : <form className="checkout-form" onSubmit={submit} noValidate><div className="form-grid">{Object.keys(emptyForm).map((key) => <label className={`field ${key === 'address_line' ? 'full-field' : ''}`} key={key} htmlFor={`field-${key}`}>{labels[key]}{key !== 'phone' && <span className="required-mark" aria-hidden="true"> *</span>}<input id={`field-${key}`} type={key === 'email' ? 'email' : 'text'} autoComplete={key === 'name' ? 'name' : key === 'email' ? 'email' : key === 'phone' ? 'tel' : key === 'address_line' ? 'street-address' : 'address-level2'} value={form[key]} aria-invalid={Boolean(errors[key])} aria-describedby={errors[key] ? `error-${key}` : undefined} onChange={(event) => updateField(key, event.target.value)} onBlur={() => { const message = validateField(key, form[key]); setErrors((current) => { const next = { ...current }; if (message) next[key] = message; else delete next[key]; return next; }); }} />{errors[key] && <span className="field-error" id={`error-${key}`} role="alert">{errors[key]}</span>}</label>)}</div><fieldset className="payment-choice"><legend>Payment simulator</legend><p>This demo uses a simulator. No card details are requested or stored.</p><label><input type="radio" name="payment" value="success" checked={paymentScenario === 'success'} onChange={() => setPaymentScenario('success')} /> Simulate successful payment</label><label><input type="radio" name="payment" value="failure" checked={paymentScenario === 'failure'} onChange={() => setPaymentScenario('failure')} /> Simulate declined payment</label></fieldset><button className="primary-button submit-button" disabled={submitting || quoteLoading || Boolean(quoteError) || !quote}>{submitting ? 'Placing your order…' : quoteLoading ? 'Calculating order total…' : quoteError ? 'Order total unavailable' : `Place order · ${currency(quote?.total ?? total)}`}</button></form>}</div>
        {items.length > 0 && <aside className="summary-card" aria-label="Order summary"><p className="eyebrow">Your books</p><h3>Order summary</h3>{quoteLoading && <p role="status">Calculating your order total…</p>}{quoteError && <div className="quote-error" role="alert"><p>{quoteError}</p><button type="button" className="back-link" onClick={() => setQuoteRefresh((current) => current + 1)}>Retry total calculation</button></div>}{quote && <><p className="summary-count">{quote.item_count} {quote.item_count === 1 ? 'item' : 'items'}</p>{quote.items.map((item) => <div className="summary-line" key={item.book_id}><span>{item.title}<small className="summary-author">{item.author}</small><small>× {item.quantity} · {currency(item.unit_price)} each</small></span><strong>{currency(item.line_total)}</strong></div>)}<div className="summary-line"><span>Subtotal</span><strong>{currency(quote.subtotal)}</strong></div>{quote.shipping > 0 && <div className="summary-line"><span>Shipping</span><strong>{currency(quote.shipping)}</strong></div>}{quote.tax > 0 && <div className="summary-line"><span>Tax</span><strong>{currency(quote.tax)}</strong></div>}{quote.discount > 0 && <div className="summary-line"><span>Discount</span><strong>−{currency(quote.discount)}</strong></div>}<div className="summary-total"><span>Final payable amount</span><strong>{currency(quote.total)}</strong></div><p className="summary-note">Prices and totals calculated by the bookstore server.</p></>}</aside>}</section>}
      {view === 'confirmation' && confirmation && <section className="confirmation"><div className="success-mark" aria-hidden="true">✓</div><p className="eyebrow">All set</p><h2>Thank you for your order.</h2><p>Your payment was approved by the simulator. Your books are on their way to the address you provided.</p><div className="confirmation-card"><div><span>Order ID</span><strong>{confirmation.order_id}</strong></div><div><span>Status</span><strong>{confirmation.status}</strong></div><div><span>Confirmed total</span><strong>{currency(confirmation.total)}</strong></div>{confirmation.items.map((item) => <div key={item.book_id}><span>{item.title} × {item.quantity}</span><strong>{currency(item.line_total)}</strong></div>)}</div><button className="primary-button" onClick={() => setView('shop')}>Back to the bookshelf</button></section>}
    </main>
    <footer><span>fieldnotes<span className="brand-light">books</span></span><span>Good books. Thoughtfully found.</span></footer>
  </div>;
}
