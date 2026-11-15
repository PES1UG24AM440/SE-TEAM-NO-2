import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AccountPanel from './AccountPanel.jsx';
import { login, logout, register } from './api.js';

vi.mock('./api.js', () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));

const seller = { id: 7, email: 'seller@example.com', display_name: 'Sam Seller', role: 'SELLER', active: true };

beforeEach(() => vi.clearAllMocks());

describe('AccountPanel', () => {
  it('signs in and reports the server-provided user', async () => {
    login.mockResolvedValue(seller);
    const onSignedIn = vi.fn();
    const user = userEvent.setup();
    render(<AccountPanel user={null} onSignedIn={onSignedIn} onSignedOut={vi.fn()} />);

    await user.type(screen.getByLabelText('Account email'), 'seller@example.com');
    await user.type(screen.getByLabelText('Password'), 'correct-horse-1');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(login).toHaveBeenCalledWith('seller@example.com', 'correct-horse-1');
    expect(onSignedIn).toHaveBeenCalledWith(seller);
  });

  it('shows the safe server error for bad credentials', async () => {
    login.mockRejectedValue(Object.assign(new Error('Email or password is incorrect.'), { status: 401 }));
    const user = userEvent.setup();
    render(<AccountPanel user={null} onSignedIn={vi.fn()} onSignedOut={vi.fn()} />);

    await user.type(screen.getByLabelText('Account email'), 'x@example.com');
    await user.type(screen.getByLabelText('Password'), 'nope-nope');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.');
  });

  it('registers a seller account, then signs in', async () => {
    register.mockResolvedValue(seller);
    login.mockResolvedValue(seller);
    const user = userEvent.setup();
    render(<AccountPanel user={null} onSignedIn={vi.fn()} onSignedOut={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: 'New here? Create an account' }));
    await user.type(screen.getByLabelText('Name'), 'Sam Seller');
    await user.type(screen.getByLabelText('Account email'), 'seller@example.com');
    await user.type(screen.getByLabelText('Password'), 'correct-horse-1');
    await user.click(screen.getByLabelText(/Seller/));
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(register).toHaveBeenCalledWith({ email: 'seller@example.com', password: 'correct-horse-1', display_name: 'Sam Seller', role: 'SELLER' });
    expect(login).toHaveBeenCalled();
  });

  it('shows field errors returned by server validation', async () => {
    register.mockRejectedValue(Object.assign(new Error('Please correct the invalid request fields.'), {
      status: 400, fields: [{ field: 'password', message: 'Password must be 8 to 72 characters.' }],
    }));
    const user = userEvent.setup();
    render(<AccountPanel user={null} onSignedIn={vi.fn()} onSignedOut={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'New here? Create an account' }));
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Password must be 8 to 72 characters.')).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it('signs out', async () => {
    logout.mockResolvedValue({});
    const onSignedOut = vi.fn();
    const user = userEvent.setup();
    render(<AccountPanel user={seller} onSignedIn={vi.fn()} onSignedOut={onSignedOut} />);
    expect(screen.getByText('seller@example.com · Seller')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Sign out' }));
    expect(onSignedOut).toHaveBeenCalled();
  });
});
