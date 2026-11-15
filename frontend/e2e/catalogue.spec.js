import { expect, test } from '@playwright/test';

// Runs against the Spring Boot API on :8080 with the seeded online_bookstore database.
const prices = async (page) => (await page.locator('.book-card .price').allTextContents())
  .map((text) => Number(text.replace(/[^0-9.]/g, '')));

test('customer can sort and filter the catalogue', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.book-card').first()).toBeVisible();
  const total = await page.locator('.book-card').count();

  await page.getByLabel('Sort by').selectOption('price-asc');
  const ascending = await prices(page);
  expect(ascending).toEqual([...ascending].sort((a, b) => a - b));

  await page.getByLabel('Sort by').selectOption('price-desc');
  const descending = await prices(page);
  expect(descending).toEqual([...descending].sort((a, b) => b - a));

  await page.getByLabel('Minimum price').fill('300');
  await page.getByLabel('Maximum price').fill('400');
  const ranged = await prices(page);
  expect(ranged.length).toBeGreaterThan(0);
  expect(ranged.length).toBeLessThan(total);
  expect(ranged.every((price) => price >= 300 && price <= 400)).toBe(true);
  await expect(page.locator('.book-count')).toContainText(`of ${total} books`);

  await page.getByLabel('Maximum price').fill('100');
  await expect(page.getByRole('alert')).toHaveText('Minimum price cannot be more than the maximum price.');

  await page.getByRole('button', { name: 'Reset sort & filters' }).click();
  await expect(page.locator('.book-card')).toHaveCount(total);

  await page.getByRole('button', { name: 'Fantasy', exact: true }).click();
  await page.getByLabel('In stock only').check();
  await expect(page.locator('.book-card').first()).toBeVisible();
  for (const tag of await page.locator('.book-card .category-tag').allTextContents()) expect(tag).toBe('Fantasy');
  await expect(page.locator('.book-card .stock.sold-out')).toHaveCount(0);
});

test('book details load from the API, are linkable and handle unknown books', async ({ page, request }) => {
  const book = await (await request.get('http://127.0.0.1:8080/api/books/3')).json();

  await page.goto('/');
  await page.locator('.book-card').filter({ hasText: book.title }).getByRole('button', { name: book.title, exact: true }).click();
  await expect(page).toHaveURL(/#book-3$/);
  const details = page.locator('.book-details');
  await expect(details.getByRole('heading', { level: 2, name: book.title })).toBeVisible();
  await expect(details).toContainText(`by ${book.author}`);
  await expect(details).toContainText(book.description);
  await expect(details).toContainText(`${book.stock} in stock`);

  await page.goBack();
  await expect(page.getByRole('heading', { name: 'Find your next favourite' })).toBeVisible();

  await page.goto('/#book-3');
  await expect(details.getByRole('heading', { level: 2, name: book.title })).toBeVisible();
  await page.getByRole('button', { name: '← Back to books' }).click();
  await expect(page).toHaveURL(/#shop$/);

  await page.goto('/#book-999999');
  await expect(page.getByRole('heading', { name: 'Book unavailable' })).toBeVisible();
  await expect(page.getByRole('alert')).toContainText('We could not find that book.');
});

for (const viewport of [{ width: 360, height: 780 }, { width: 768, height: 1024 }, { width: 1280, height: 800 }]) {
  test(`catalogue, filters and details fit a ${viewport.width}px screen`, async ({ page }) => {
    await page.setViewportSize(viewport);
    await page.goto('/');
    await expect(page.locator('.book-card').first()).toBeVisible();
    await expect(page.getByLabel('Sort by')).toBeVisible();
    await expect(page.getByLabel('Minimum price')).toBeVisible();
    const noOverflow = () => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
    expect(await noOverflow()).toBe(true);
    await page.goto('/#book-1');
    await expect(page.locator('.details-info h2')).toBeVisible();
    expect(await noOverflow()).toBe(true);
  });
}
