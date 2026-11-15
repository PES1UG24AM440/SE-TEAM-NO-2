import { useCallback, useEffect, useState } from 'react';
import { activateListing, createListing, deactivateListing, getMyListings, updateListing } from './api.js';
import ListingForm from './ListingForm.jsx';
import { listingFromBook } from './listing.js';

const currency = (value) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(value);

// Seller dashboard (#72): create, edit, deactivate and reactivate your own listings.
export default function SellerDashboard({ onCatalogueChanged = () => {} }) {
  const [listings, setListings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [editing, setEditing] = useState(null);

  const load = useCallback(() => {
    setLoading(true);
    return getMyListings()
      .then((list) => { setListings(list); setError(''); })
      .catch((failure) => setError(failure.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  async function create(payload) {
    const created = await createListing(payload);
    setNotice(`"${created.title}" is now listed.`);
    await load(); onCatalogueChanged();
  }

  async function save(payload) {
    const updated = await updateListing(editing.id, payload);
    setEditing(null);
    setNotice(`"${updated.title}" was updated.`);
    await load(); onCatalogueChanged();
  }

  async function toggle(book) {
    setNotice(''); setError('');
    try {
      const updated = book.active ? await deactivateListing(book.id) : await activateListing(book.id);
      setNotice(`"${updated.title}" ${updated.active ? 'is listed again' : 'was removed from the catalogue'}.`);
      await load(); onCatalogueChanged();
    } catch (failure) {
      setError(failure.message);
    }
  }

  return <section className="dashboard" aria-labelledby="seller-title">
    <p className="eyebrow">Seller dashboard</p>
    <h2 id="seller-title">Your listings</h2>
    {notice && <p className="notice" role="status">{notice}</p>}
    {error && <p className="order-error" role="alert">{error}</p>}

    {editing
      ? <><h3>Edit “{editing.title}”</h3><ListingForm key={`edit-${editing.id}`} idPrefix="edit-listing" initial={listingFromBook(editing)} submitLabel="Save changes" onSubmit={save} onCancel={() => setEditing(null)} /></>
      : <><h3>Add a book</h3><ListingForm key="create" idPrefix="new-listing" submitLabel="Add listing" onSubmit={create} /></>}

    <h3>Listed books</h3>
    {loading ? <p className="loading" role="status">Loading your listings…</p>
      : listings.length === 0 ? <p className="account-meta">You have no listings yet. Add your first book above.</p>
        : <div className="table-scroll"><table className="data-table">
          <thead><tr><th>Title</th><th>Category</th><th>Price</th><th>Stock</th><th>Status</th><th>Actions</th></tr></thead>
          <tbody>{listings.map((book) => <tr key={book.id}>
            <td><strong>{book.title}</strong><br /><small>{book.author}</small></td>
            <td>{book.category}</td>
            <td>{currency(book.price)}</td>
            <td>{book.stock}</td>
            <td><span className={book.active ? 'status-pill' : 'status-pill off'}>{book.active ? 'Listed' : 'Removed'}</span></td>
            <td><div className="row-actions">
              <button className="text-button" onClick={() => { setEditing(book); setNotice(''); }} aria-label={`Edit ${book.title}`}>Edit</button>
              <button className="text-button" onClick={() => toggle(book)} aria-label={`${book.active ? 'Remove' : 'Relist'} ${book.title}`}>{book.active ? 'Remove' : 'Relist'}</button>
            </div></td>
          </tr>)}</tbody>
        </table></div>}
  </section>;
}
