import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App.jsx';
import { getBooks } from './api.js';

vi.mock('./api.js', () => ({ getBooks: vi.fn() }));
const catalogue = [{ id: 1, title: 'The Alchemist', author: 'Paulo Coelho', price: 299, stock: 2 }];

beforeEach(() => { vi.clearAllMocks(); getBooks.mockResolvedValue(catalogue); });

describe('cart', () => {
  it('adds a book and updates quantity, subtotal, and total', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: /Add to bag/ }));
    await user.click(screen.getByRole('button', { name: /Shopping bag/ }));
    await user.click(screen.getByRole('button', { name: 'Increase The Alchemist quantity' }));
    expect(screen.getAllByText('₹598.00')).toHaveLength(3);
    expect(screen.getByText('× 2')).toBeInTheDocument();
  });

  it('removes an item and shows the empty-cart state', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    await user.click(screen.getByRole('button', { name: /Add to bag/ }));
    await user.click(screen.getByRole('button', { name: /Shopping bag/ }));
    await user.click(screen.getByRole('button', { name: 'Remove' }));
    expect(screen.getByText('Your bag is waiting for a story.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Continue shopping' })).toBeInTheDocument();
  });

  it('prevents adding beyond stock and disables increment at the limit', async () => {
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', { name: 'The Alchemist' });
    const addButton = screen.getByRole('button', { name: /Add to bag/ });
    await user.click(addButton);
    await user.click(addButton);
    await user.click(addButton);
    expect(screen.getByRole('status')).toHaveTextContent('Only 2 copies are available');
    await user.click(screen.getByRole('button', { name: /Shopping bag/ }));
    expect(screen.getByRole('button', { name: 'Increase The Alchemist quantity' })).toBeDisabled();
  });
});
