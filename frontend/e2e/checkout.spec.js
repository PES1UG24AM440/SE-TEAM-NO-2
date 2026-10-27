import { expect, test } from '@playwright/test';

async function fillShippingDetails(page) {
  await page.locator('#field-name').fill('A Reader');
  await page.locator('#field-email').fill('reader@example.com');
  await page.locator('#field-address_line').fill('1 Story Lane');
  await page.locator('#field-city').fill('Mysuru');
  await page.locator('#field-state').fill('Karnataka');
  await page.locator('#field-postal_code').fill('570001');
  await page.locator('#field-country').fill('India');
}

test('customer can edit a cart, validate checkout, place an order and see API failure states', async ({ page, request }) => {
  await page.goto('/');
  const alchemistCard = page.locator('.book-card').filter({ hasText: 'The Alchemist' });
  await expect(alchemistCard).toBeVisible();
  const stockBefore = (await (await request.get('http://127.0.0.1:8000/api/books')).json()).find((book) => book.id === 1).stock;
  expect(stockBefore).toBeGreaterThan(1);

  await alchemistCard.getByRole('button', { name: /Add to bag/ }).click();
  await page.getByRole('button', { name: /Shopping bag/ }).click();
  await page.getByRole('button', { name: 'Increase The Alchemist quantity' }).click();
  await expect(page.locator('.line-total')).toHaveText('₹598.00');
  await page.getByRole('button', { name: 'Remove' }).click();
  await expect(page.getByText('Your bag is waiting for a story.')).toBeVisible();

  await page.getByRole('button', { name: 'Continue shopping' }).click();
  await page.locator('.book-card').filter({ hasText: 'The Alchemist' }).getByRole('button', { name: /Add to bag/ }).click();
  await page.getByRole('button', { name: /Shopping bag/ }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.getByRole('button', { name: /Continue to checkout/ }).click();
  await page.locator('#field-email').fill('not-an-email');
  await page.getByRole('button', { name: /Place order/ }).click();
  await expect(page.getByText('Full name is required.')).toBeVisible();
  await expect(page.getByText('Address is required.')).toBeVisible();
  await expect(page.getByText('Enter a valid email address.')).toBeVisible();

  await fillShippingDetails(page);
  const orderResponsePromise = page.waitForResponse((response) => response.url().endsWith('/api/orders') && response.request().method() === 'POST');
  await page.getByRole('button', { name: /Place order/ }).click();
  const orderResponse = await orderResponsePromise;
  expect(orderResponse.status()).toBe(201);
  const order = await orderResponse.json();
  await expect(page.getByText('Thank you for your order.')).toBeVisible();
  await expect(page.getByText(order.order_id)).toBeVisible();
  await expect(page.locator('.confirmation-card')).toContainText(`₹${order.total.toFixed(2)}`);
  const stockAfter = (await (await request.get('http://127.0.0.1:8000/api/books')).json()).find((book) => book.id === 1).stock;
  expect(stockAfter).toBe(stockBefore - 1);

  const beforeFailure = (await (await request.get('http://127.0.0.1:8000/api/books')).json()).find((book) => book.id === 2).stock;
  const insufficient = await request.post('http://127.0.0.1:8000/api/orders', { data: {
    items: [{ book_id: 3, quantity: 4 }], customer: {
      name: 'A Reader', email: 'reader@example.com', address_line: '1 Story Lane', city: 'Mysuru',
      state: 'Karnataka', postal_code: '570001', country: 'India',
    },
  } });
  expect(insufficient.status()).toBe(409);

  await page.getByRole('button', { name: 'Browse books' }).click();
  await page.locator('.book-card').filter({ hasText: 'Atomic Habits' }).getByRole('button', { name: /Add to bag/ }).click();
  await page.getByRole('button', { name: /Shopping bag/ }).click();
  await page.getByRole('button', { name: /Continue to checkout/ }).click();
  await fillShippingDetails(page);
  await page.getByLabel('Simulate declined payment').check();
  const failureResponsePromise = page.waitForResponse((response) => response.url().endsWith('/api/orders') && response.request().method() === 'POST');
  await page.getByRole('button', { name: /Place order/ }).click();
  const failureResponse = await failureResponsePromise;
  expect(failureResponse.status()).toBe(402);
  await expect(page.getByRole('status')).toContainText('payment simulator declined');
  await expect(page.getByText('Thank you for your order.')).toHaveCount(0);
  const afterFailure = (await (await request.get('http://127.0.0.1:8000/api/books')).json()).find((book) => book.id === 2).stock;
  expect(afterFailure).toBe(beforeFailure);
});
