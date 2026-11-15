import { useState } from 'react';
import { login, logout, register } from './api.js';

const roleLabels = { CUSTOMER: 'Customer', SELLER: 'Seller', ADMIN: 'Administrator' };

function fieldErrors(error) {
  return Object.fromEntries((error.fields || []).map(({ field, message }) => [field, message]));
}

// Sign in, create an account, or sign out. The server decides the role; the UI only shows it.
export default function AccountPanel({ user, onSignedIn, onSignedOut }) {
  const [mode, setMode] = useState('signin');
  const [form, setForm] = useState({ email: '', password: '', display_name: '', role: 'CUSTOMER' });
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  const update = (key, value) => setForm((current) => ({ ...current, [key]: value }));

  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    setBusy(true); setErrors({}); setMessage('');
    try {
      if (mode === 'register') {
        await register({ email: form.email, password: form.password, display_name: form.display_name, role: form.role });
      }
      const signedIn = await login(form.email, form.password);
      setForm((current) => ({ ...current, password: '' }));
      onSignedIn(signedIn);
    } catch (error) {
      setErrors(fieldErrors(error));
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  async function signOut() {
    setBusy(true);
    try { await logout(); } catch { /* the session is cleared locally either way */ }
    setBusy(false);
    onSignedOut();
  }

  if (user) {
    return <section className="account-view" aria-labelledby="account-title">
      <p className="eyebrow">Your account</p>
      <h2 id="account-title">Signed in as {user.display_name}</h2>
      <p className="account-meta">{user.email} · {roleLabels[user.role] || user.role}</p>
      <button className="primary-button" onClick={signOut} disabled={busy}>Sign out</button>
    </section>;
  }

  return <section className="account-view" aria-labelledby="account-title">
    <p className="eyebrow">{mode === 'register' ? 'Join the shelf' : 'Welcome back'}</p>
    <h2 id="account-title">{mode === 'register' ? 'Create an account' : 'Sign in'}</h2>
    <form className="account-form" onSubmit={submit} noValidate>
      {mode === 'register' && <label className="field" htmlFor="account-name">Name
        <input id="account-name" value={form.display_name} onChange={(event) => update('display_name', event.target.value)} aria-invalid={Boolean(errors.display_name)} />
        {errors.display_name && <span className="field-error">{errors.display_name}</span>}</label>}
      <label className="field" htmlFor="account-email">Account email
        <input id="account-email" type="email" autoComplete="email" value={form.email} onChange={(event) => update('email', event.target.value)} aria-invalid={Boolean(errors.email)} />
        {errors.email && <span className="field-error">{errors.email}</span>}</label>
      <label className="field" htmlFor="account-password">Password
        <input id="account-password" type="password" autoComplete={mode === 'register' ? 'new-password' : 'current-password'} value={form.password} onChange={(event) => update('password', event.target.value)} aria-invalid={Boolean(errors.password)} />
        {errors.password && <span className="field-error">{errors.password}</span>}</label>
      {mode === 'register' && <fieldset className="payment-choice"><legend>Account type</legend>
        <label><input type="radio" name="account-role" checked={form.role === 'CUSTOMER'} onChange={() => update('role', 'CUSTOMER')} /> Customer</label>
        <label><input type="radio" name="account-role" checked={form.role === 'SELLER'} onChange={() => update('role', 'SELLER')} /> Seller (list books for sale)</label>
      </fieldset>}
      {message && <p className="order-error" role="alert">{message}</p>}
      <button className="primary-button" disabled={busy}>{busy ? 'Please wait…' : mode === 'register' ? 'Create account' : 'Sign in'}</button>
    </form>
    <button className="text-button" onClick={() => { setMode(mode === 'register' ? 'signin' : 'register'); setErrors({}); setMessage(''); }}>
      {mode === 'register' ? 'Already have an account? Sign in' : 'New here? Create an account'}
    </button>
  </section>;
}
