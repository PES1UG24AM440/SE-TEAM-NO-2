import { useEffect, useMemo, useState } from 'react';
import { getBooks } from './api.js';

const currency = (value) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(value);

export default function App() {
  const [books, setBooks] = useState([]);
  const [cart, setCart] = useState({});
  const [view, setView] = useState('shop');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);
  const [checkoutBusy, setCheckoutBusy] = useState(false);
  const [checkoutError, setCheckoutError] = useState('');

  useEffect(() => {
    getBooks().then(setBooks).catch((error) => setNotice(error.message)).finally(() => setLoading(false));
  }, []);

  const items = useMemo(() => Object.entries(cart).map(([id, quantity]) => ({
    book: books.find((entry) => entry.id === Number(id)), quantity,
  })).filter(({ book }) => book), [books, cart]);
  const total = items.reduce((sum, { book, quantity }) => sum + book.price * quantity, 0);
  const itemCount = items.reduce((count, item) => count + item.quantity, 0);

  function add(book) {
    const quantity = cart[book.id] || 0;
    if (quantity >= book.stock) {
      setNotice(`Only ${book.stock} ${book.stock === 1 ? 'copy is' : 'copies are'} available for ${book.title}.`);
      return;
    }
    setCart((current) => ({ ...current, [book.id]: quantity + 1 }));
    setNotice(`${book.title} added to your bag.`);
  }

  function change(book, delta) {
    const next = (cart[book.id] || 0) + delta;
    if (next > book.stock) {
      setNotice(`Only ${book.stock} ${book.stock === 1 ? 'copy is' : 'copies are'} available for ${book.title}.`);
      return;
    }
    setCart((current) => {
      const updated = { ...current };
      if (next <= 0) delete updated[book.id];
      else updated[book.id] = next;
      return updated;
    });
    setNotice('');
  }

  function remove(book) {
    setCart((current) => {
      const updated = { ...current };
      delete updated[book.id];
      return updated;
    });
    setNotice(`${book.title} removed from your bag.`);
  }

  async function submitCheckout(event) {
    event.preventDefault();
    if (checkoutBusy || items.length === 0) return;
    setCheckoutBusy(true);
    setCheckoutError('Order service is not connected yet. Please try again later.');
    setCheckoutBusy(false);
  }

  return <div className="site-shell">
    <header className="topbar"><a className="brand" href="#shop" onClick={() => setView('shop')} aria-label="Fieldnotes Books home"><span className="brand-mark">F</span><span>fieldnotes<span className="brand-light">books</span></span></a>
      <nav aria-label="Main navigation"><button className={view === 'shop' ? 'nav-link active' : 'nav-link'} onClick={() => setView('shop')}>Browse books</button><button className="cart-link" onClick={() => setView('cart')} aria-label={`Shopping bag, ${itemCount} items`}><span>Bag</span><span className="cart-count">{itemCount}</span></button></nav>
    </header>
    <main>
      <section className="hero"><p className="eyebrow">A good book finds its way</p><h1>Stories for the<br /><em>in-between moments.</em></h1><p className="hero-copy">A small, thoughtful shelf of books to keep you curious.</p><div className="hero-stamp" aria-hidden="true">READ<br />MORE</div></section>
      {notice && <div className="notice" role="status">{notice}</div>}
      {loading && <p className="loading" role="status">Loading the bookshelf…</p>}
      {!loading && view === 'shop' && <section className="catalog" aria-labelledby="catalog-title"><div className="section-heading"><div><p className="eyebrow">The collection</p><h2 id="catalog-title">Find your next favourite</h2></div><span className="book-count">{books.length} books on the shelf</span></div>
        {books.length === 0 ? <p className="empty-catalog">The catalogue is unavailable right now. Please refresh and try again.</p> : <div className="book-grid">{books.map((book, index) => <article className="book-card" key={book.id}><div className={`cover cover-${index % 4}`} aria-hidden="true"><span className="cover-lines">{book.title.split(' ').slice(0, 3).join('\n')}</span><span className="cover-author">{book.author}</span><span className="cover-orbit" /></div><div className="book-meta"><div><h3>{book.title}</h3><p>{book.author}</p></div><span className="price">{currency(book.price)}</span></div><div className="book-actions"><span className={book.stock > 0 ? 'stock' : 'stock sold-out'}>{book.stock > 0 ? `${book.stock} in stock` : 'Out of stock'}</span><button className="add-button" onClick={() => add(book)} disabled={book.stock === 0}>Add to bag <span aria-hidden="true">+</span></button></div></article>)}</div>}
      </section>}
      {!loading && view === 'cart' && <section className="cart-layout"><div><p className="eyebrow">Your selection</p><h2>Your bag</h2>{items.length === 0 ? <div className="empty-state"><span className="empty-icon" aria-hidden="true">✳</span><h3>Your bag is waiting for a story.</h3><p>Browse the shelf and add a book you love.</p><button className="primary-button" onClick={() => setView('shop')}>Continue shopping</button></div> : <div className="cart-list">{items.map(({ book, quantity }) => <article className="cart-row" key={book.id}><div className={`mini-cover cover-${book.id % 4}`} aria-hidden="true">{book.title.slice(0, 1)}</div><div className="cart-description"><h3>{book.title}</h3><p>{book.author} · {currency(book.price)} each</p><button className="text-button" onClick={() => remove(book)}>Remove</button></div><div className="quantity" aria-label={`Quantity for ${book.title}`}><button aria-label={`Decrease ${book.title} quantity`} onClick={() => change(book, -1)}>−</button><output aria-label={`${book.title} quantity`}>{quantity}</output><button aria-label={`Increase ${book.title} quantity`} onClick={() => change(book, 1)} disabled={quantity >= book.stock}>+</button></div><strong className="line-total">{currency(book.price * quantity)}</strong></article>)}</div>}</div>
        {items.length > 0 && <aside className="summary-card"><p className="eyebrow">At a glance</p><h3>Order summary</h3>{items.map(({ book, quantity }) => <div className="summary-line" key={book.id}><span>{book.title} <small>× {quantity}</small></span><span>{currency(book.price * quantity)}</span></div>)}<div className="summary-total"><span>Cart total</span><strong>{currency(total)}</strong></div><button className="primary-button wide" onClick={() => setView('checkout')}>Continue to checkout <span aria-hidden="true">→</span></button><button className="back-link" onClick={() => setView('shop')}>← Continue shopping</button></aside>}
      </section>}
      {!loading && view === 'checkout' && <section className="checkout-layout"><div><button className="back-link" onClick={() => setView('cart')}>← Back to your bag</button><p className="eyebrow">Almost yours</p><h2>Checkout</h2>{items.length === 0 ? <div className="empty-state"><h3>Your bag is empty.</h3><p>Add a book before checking out.</p><button className="primary-button" onClick={() => setView('shop')}>Browse books</button></div> : <form className="checkout-form" onSubmit={submitCheckout}><label>Full name<input name="name" autoComplete="name" required /></label><label>Email address<input name="email" type="email" autoComplete="email" required /></label><label>Phone (optional)<input name="phone" type="tel" autoComplete="tel" /></label><label>Street address<input name="address" autoComplete="street-address" required /></label><div className="form-row"><label>City<input name="city" autoComplete="address-level2" required /></label><label>State / region<input name="state" autoComplete="address-level1" required /></label></div><div className="form-row"><label>Postal code<input name="postal_code" autoComplete="postal-code" required /></label><label>Country<input name="country" autoComplete="country-name" required /></label></div>{checkoutError && <p role="alert">{checkoutError}</p>}<button className="primary-button" type="submit" disabled={checkoutBusy}>{checkoutBusy ? 'Submitting…' : 'Place order'}</button></form>}</div></section>}
    </main>
    <footer><span>fieldnotes<span className="brand-light">books</span></span><span>Good books. Thoughtfully found.</span></footer>
  </div>;
}
