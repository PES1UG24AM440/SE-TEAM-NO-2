import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SellerDashboard from './SellerDashboard.jsx';
import { activateListing, createListing, deactivateListing, getMyListings, updateListing } from './api.js';
import { validateListing } from './listing.js';

vi.mock('./api.js', () => ({
  activateListing: vi.fn(), createListing: vi.fn(), deactivateListing: vi.fn(), getMyListings: vi.fn(), updateListing: vi.fn(),
}));

const listed = { id: 11, title: 'Seller Book', author: 'A. Writer', category: 'Fiction', description: '', price: 250, stock: 4, active: true, sellerId: 7 };

beforeEach(() => {
  vi.clearAllMocks();
  getMyListings.mockResolvedValue([listed]);
});

async function fill(user, prefix, values) {
  for (const [label, value] of Object.entries(values)) {
    const input = screen.getByLabelText(label, { selector: `[id^="${prefix}"]` });
    await user.clear(input);
    await user.type(input, value);
  }
}

describe('SellerDashboard', () => {
  it('lists only what the server returns for this seller', async () => {
    render(<SellerDashboard />);
    const row = (await screen.findByText('Seller Book')).closest('tr');
    expect(within(row).getByText('Listed')).toBeInTheDocument();
    expect(within(row).getByText('4')).toBeInTheDocument();
  });

  it('creates a listing with numeric price and stock', async () => {
    createListing.mockResolvedValue({ ...listed, id: 12, title: 'New Book' });
    const onCatalogueChanged = vi.fn();
    const user = userEvent.setup();
    render(<SellerDashboard onCatalogueChanged={onCatalogueChanged} />);
    await screen.findByText('Seller Book');

    await fill(user, 'new-listing', { Title: 'New Book', Author: 'B. Writer', Category: 'Poetry', 'Price (₹)': '99.50', Stock: '3' });
    await user.click(screen.getByRole('button', { name: 'Add listing' }));

    expect(createListing).toHaveBeenCalledWith({ title: 'New Book', author: 'B. Writer', category: 'Poetry', description: '', price: 99.5, stock: 3 });
    expect(await screen.findByText('"New Book" is now listed.')).toBeInTheDocument();
    expect(onCatalogueChanged).toHaveBeenCalled();
  });

  it('blocks invalid input before calling the server', async () => {
    const user = userEvent.setup();
    render(<SellerDashboard />);
    await screen.findByText('Seller Book');
    await fill(user, 'new-listing', { 'Price (₹)': '-3', Stock: '2.5' });
    await user.click(screen.getByRole('button', { name: 'Add listing' }));
    expect(screen.getByText('Title is required.')).toBeInTheDocument();
    expect(screen.getByText('Enter a price greater than 0 with at most 2 decimals.')).toBeInTheDocument();
    expect(screen.getByText('Enter a whole number from 0 to 100000.')).toBeInTheDocument();
    expect(createListing).not.toHaveBeenCalled();
  });

  it('edits a listing', async () => {
    updateListing.mockResolvedValue({ ...listed, price: 300 });
    const user = userEvent.setup();
    render(<SellerDashboard />);
    await user.click(await screen.findByRole('button', { name: 'Edit Seller Book' }));
    await fill(user, 'edit-listing', { 'Price (₹)': '300' });
    await user.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(updateListing).toHaveBeenCalledWith(11, expect.objectContaining({ title: 'Seller Book', price: 300, stock: 4 }));
  });

  it('removes and relists a listing', async () => {
    deactivateListing.mockResolvedValue({ ...listed, active: false });
    const user = userEvent.setup();
    render(<SellerDashboard />);
    const removeButton = await screen.findByRole('button', { name: 'Remove Seller Book' });
    getMyListings.mockResolvedValue([{ ...listed, active: false }]);
    await user.click(removeButton);
    expect(deactivateListing).toHaveBeenCalledWith(11);
    expect(await screen.findByText('"Seller Book" was removed from the catalogue.')).toBeInTheDocument();
    expect(await screen.findByText('Removed')).toBeInTheDocument();

    activateListing.mockResolvedValue(listed);
    getMyListings.mockResolvedValue([listed]);
    await user.click(screen.getByRole('button', { name: 'Relist Seller Book' }));
    expect(activateListing).toHaveBeenCalledWith(11);
    expect(await screen.findByText('"Seller Book" is listed again.')).toBeInTheDocument();
  });

  it('shows the server error when a listing belongs to someone else', async () => {
    deactivateListing.mockRejectedValue(Object.assign(new Error('You can only change your own listings.'), { status: 403 }));
    const user = userEvent.setup();
    render(<SellerDashboard />);
    await user.click(await screen.findByRole('button', { name: 'Remove Seller Book' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('You can only change your own listings.');
  });
});

describe('validateListing', () => {
  it('accepts a complete listing', () => {
    expect(validateListing({ title: 'T', author: 'A', category: 'C', description: '', price: '10', stock: '0' })).toEqual({});
  });
  it('rejects zero price and over-long titles', () => {
    const errors = validateListing({ title: 'x'.repeat(201), author: 'A', category: 'C', description: '', price: '0', stock: '1' });
    expect(Object.keys(errors).sort()).toEqual(['price', 'title']);
  });
});
