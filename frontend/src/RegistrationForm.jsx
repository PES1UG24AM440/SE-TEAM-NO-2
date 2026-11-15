import { useState } from 'react';
import { register } from './api.js';

const initialForm = { name: '', email: '', password: '' };
const passwordPattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s])\S+$/;

function validate(form) {
  const errors = {};
  const name = form.name.trim();
  const email = form.email.trim();

  if (!name) errors.name = 'Name is required.';
  else if (name.length > 120) errors.name = 'Name must be 120 characters or fewer.';

  if (!email) errors.email = 'Email is required.';
  else if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    errors.email = 'Enter a valid email address.';
  }

  if (!form.password) errors.password = 'Password is required.';
  else if (form.password.length < 8 || form.password.length > 72
    || new TextEncoder().encode(form.password).length > 72
    || !passwordPattern.test(form.password)) {
    errors.password = 'Use 8–72 characters with uppercase and lowercase letters, a number, and a symbol; no spaces.';
  }

  return errors;
}

export default function RegistrationForm({ onSuccess, onCancel }) {
  const [form, setForm] = useState(initialForm);
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function updateField(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => {
      if (!current[field]) return current;
      const next = { ...current };
      delete next[field];
      return next;
    });
    setMessage('');
  }

  async function submit(event) {
    event.preventDefault();
    const fieldErrors = validate(form);
    setErrors(fieldErrors);
    setMessage('');
    if (Object.keys(fieldErrors).length) return;

    setSubmitting(true);
    try {
      const user = await register({
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
      });
      onSuccess(user);
    } catch (error) {
      setErrors(error.fieldErrors || {});
      setMessage(error.message || 'We could not create your account. Please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  return <section className="registration-page" aria-labelledby="registration-title">
    <button type="button" className="back-link back-top" onClick={onCancel}>← Back to books</button>
    <p className="eyebrow">Join the bookshelf</p>
    <h2 id="registration-title">Create your account</h2>
    <p className="registration-copy">Save your details for your next visit.</p>
    <form className="registration-form" onSubmit={submit} noValidate>
      <label className="field" htmlFor="registration-name">Full name <span className="required-mark" aria-hidden="true">*</span>
        <input id="registration-name" name="name" autoComplete="name" maxLength={120} required value={form.name}
          aria-invalid={Boolean(errors.name)} aria-describedby={errors.name ? 'registration-name-error' : undefined}
          onChange={(event) => updateField('name', event.target.value)} />
        {errors.name && <span className="field-error" id="registration-name-error" role="alert">{errors.name}</span>}
      </label>
      <label className="field" htmlFor="registration-email">Email <span className="required-mark" aria-hidden="true">*</span>
        <input id="registration-email" name="email" type="email" autoComplete="email" maxLength={254} required value={form.email}
          aria-invalid={Boolean(errors.email)} aria-describedby={errors.email ? 'registration-email-error' : undefined}
          onChange={(event) => updateField('email', event.target.value)} />
        {errors.email && <span className="field-error" id="registration-email-error" role="alert">{errors.email}</span>}
      </label>
      <label className="field" htmlFor="registration-password">Password <span className="required-mark" aria-hidden="true">*</span>
        <input id="registration-password" name="password" type="password" autoComplete="new-password" minLength={8} maxLength={72} required value={form.password}
          aria-invalid={Boolean(errors.password)} aria-describedby={errors.password ? 'registration-password-error registration-password-hint' : 'registration-password-hint'}
          onChange={(event) => updateField('password', event.target.value)} />
        <span className="registration-hint" id="registration-password-hint">8–72 characters; include uppercase and lowercase letters, a number, and a symbol. Do not use spaces.</span>
        {errors.password && <span className="field-error" id="registration-password-error" role="alert">{errors.password}</span>}
      </label>
      {message && <p className="field-error registration-message" role="alert">{message}</p>}
      <button className="primary-button wide" type="submit" disabled={submitting}>
        {submitting ? 'Creating account…' : 'Create account'}
      </button>
    </form>
  </section>;
}
