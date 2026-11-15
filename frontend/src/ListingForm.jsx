import { useState } from 'react';
import { emptyListing, toListingPayload, validateListing } from './listing.js';

const fields = [
  ['title', 'Title'], ['author', 'Author'], ['category', 'Category'],
  ['price', 'Price (₹)'], ['stock', 'Stock'], ['description', 'Description (optional)'],
];

// Shared by the seller dashboard (#72) and the admin listing editor (#73).
export default function ListingForm({ initial = emptyListing, submitLabel, onSubmit, onCancel, idPrefix = 'listing' }) {
  const [listing, setListing] = useState(initial);
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    const nextErrors = validateListing(listing);
    setErrors(nextErrors); setMessage('');
    if (Object.keys(nextErrors).length) return;
    setBusy(true);
    try {
      await onSubmit(toListingPayload(listing));
      setListing(initial);
    } catch (error) {
      setErrors(Object.fromEntries((error.fields || []).map(({ field, message: text }) => [field, text])));
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  return <form className="listing-form" onSubmit={submit} noValidate>
    {fields.map(([key, label]) => <label key={key} className={`field${key === 'description' ? ' full-field' : ''}`} htmlFor={`${idPrefix}-${key}`}>{label}
      {key === 'description'
        ? <textarea id={`${idPrefix}-${key}`} rows={3} value={listing[key]} onChange={(event) => setListing({ ...listing, [key]: event.target.value })} />
        : <input id={`${idPrefix}-${key}`} inputMode={key === 'price' ? 'decimal' : key === 'stock' ? 'numeric' : undefined} value={listing[key]} aria-invalid={Boolean(errors[key])} onChange={(event) => setListing({ ...listing, [key]: event.target.value })} />}
      {errors[key] && <span className="field-error">{errors[key]}</span>}
    </label>)}
    {message && <p className="order-error full-field" role="alert">{message}</p>}
    <div className="form-actions">
      <button className="primary-button" disabled={busy}>{busy ? 'Saving…' : submitLabel}</button>
      {onCancel && <button type="button" className="text-button" onClick={onCancel}>Cancel</button>}
    </div>
  </form>;
}
