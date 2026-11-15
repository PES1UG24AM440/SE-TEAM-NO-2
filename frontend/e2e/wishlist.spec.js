import { expect, test } from '@playwright/test';

const books = [{
  id: 1,
  title: 'The Alchemist',
  author: 'Paulo Coelho',
  category: 'Fiction',
  description: 'A shepherd follows his dream.',
  price: 299,
  stock: 2,
}];

test('wishlist saves across refresh and adds to cart independently', async ({ page }) => {
  const pageErrors = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  await page.route('**/api/books**', (route) => route.fulfill({ json: /\/api\/books\/\d+$/.test(new URL(route.request().url()).pathname) ? books[0] : books }));
  await page.goto('/');

  const wishlistButton = (saved) => page.getByRole('button', {
    name: `${saved ? 'Remove' : 'Add'} The Alchemist ${saved ? 'from' : 'to'} wishlist`,
  });
  await expect(wishlistButton(false)).toBeVisible();
  await wishlistButton(false).click();
  await expect(wishlistButton(true)).toHaveAttribute('aria-pressed', 'true');

  await page.getByRole('button', { name: 'The Alchemist', exact: true }).click();
  await expect(wishlistButton(true)).toBeVisible();
  await page.getByRole('button', { name: 'Wishlist, 1 saved book' }).click();
  await expect(page.getByRole('heading', { name: 'The Alchemist' })).toBeVisible();

  await page.reload();
  await page.getByRole('button', { name: 'Wishlist, 1 saved book' }).click();
  await expect(page.getByRole('heading', { name: 'The Alchemist' })).toBeVisible();
  await page.getByRole('button', { name: 'Add to Cart' }).click();
  await expect(page.getByRole('button', { name: 'Shopping bag, 1 items' })).toBeVisible();

  await wishlistButton(true).click();
  await expect(page.getByRole('heading', { name: 'Your Wishlist is Empty!' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Shopping bag, 1 items' })).toBeVisible();
  await page.getByRole('button', { name: /Shopping bag/ }).click();
  await expect(page.getByRole('heading', { name: 'Your bag' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'The Alchemist' })).toBeVisible();
  expect(pageErrors).toEqual([]);
});

test('wishlist details button and empty state work', async ({ page }) => {
  await page.route('**/api/books**', (route) => route.fulfill({ json: /\/api\/books\/\d+$/.test(new URL(route.request().url()).pathname) ? books[0] : books }));
  await page.goto('/');
  await page.getByRole('button', { name: 'Wishlist, 0 saved books' }).click();
  await expect(page.getByText(/haven't saved any books yet/i)).toBeVisible();
  await page.getByRole('button', { name: 'Explore Books' }).click();
  await page.getByRole('button', { name: 'The Alchemist', exact: true }).click();
  await page.getByRole('button', { name: 'Add The Alchemist to wishlist' }).click();
  await expect(page.getByRole('button', { name: 'Remove The Alchemist from wishlist' })).toHaveAttribute('aria-pressed', 'true');
});
