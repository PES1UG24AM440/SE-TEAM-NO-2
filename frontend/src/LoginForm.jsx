import { useState } from 'react';
import { login } from './api.js';

export default function LoginForm({ onSuccess, onCancel }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setMessage('');
    setSubmitting(true);
    try {
      const user = await login({ email: email.trim(), password });
      onSuccess(user);
    } catch (error) {
      setMessage(error.message || 'We could not sign you in. Please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  return <section className="registration-page" aria-labelledby="login-title">
    <button type="button" className="back-link back-top" onClick={onCancel}>← Back to books</button>
    <p className="eyebrow">Welcome back</p>
    <h2 id="login-title">Sign in to your account</h2>
    <form className="registration-form" onSubmit={submit}>
      <label className="field" htmlFor="login-email">Email
        <input id="login-email" name="email" type="email" autoComplete="email" maxLength={254} required
          value={email} onChange={(event) => setEmail(event.target.value)} />
      </label>
      <label className="field" htmlFor="login-password">Password
        <input id="login-password" name="password" type="password" autoComplete="current-password" required
          value={password} onChange={(event) => setPassword(event.target.value)} />
      </label>
      {message && <p className="field-error registration-message" role="alert">{message}</p>}
      <button className="primary-button wide" type="submit" disabled={submitting}>
        {submitting ? 'Signing in…' : 'Sign in'}
      </button>
    </form>
  </section>;
}
