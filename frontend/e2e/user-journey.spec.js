import { expect, test } from '@playwright/test';

async function fillValidShippingDetails(page) {
  await page.locator('#field-name').fill('Yogish K J');
  await page.locator('#field-email').fill('yogish@example.com');
  await page.locator('#field-address_line').fill('100 Test Avenue');
  await page.locator('#field-city').fill('Bengaluru');
  await page.locator('#field-state').fill('Karnataka');
  await page.locator('#field-postal_code').fill('560001');
  await page.locator('#field-country').fill('India');
}

test.describe('Issue #59 End-to-End User Journey Tests', () => {

  test('Criterion 1: Browse catalogue to book details, wishlist and deep linking', async ({ page }) => {
    await page.goto('/');
    const alchemistCard = page.locator('.book-card').filter({ hasText: 'The Alchemist' });
    await expect(alchemistCard).toBeVisible();
    await expect(alchemistCard.locator('.category-tag')).toContainText('Fiction');
    await expect(alchemistCard.locator('.price')).toBeVisible();

    // Click book title to navigate to book details
    await alchemistCard.getByRole('button', { name: 'The Alchemist', exact: true }).click();
    await expect(page).toHaveURL(/#book-1$/);

    const details = page.locator('.book-details');
    await expect(details.getByRole('heading', { level: 2, name: 'The Alchemist' })).toBeVisible();
    await expect(details.locator('.details-author')).toContainText('Paulo Coelho');
    await expect(details.locator('.details-description')).toBeVisible();
    await expect(details.locator('.stock')).toContainText('in stock');

    // Go back to shelf
    await details.getByRole('button', { name: '← Back to books' }).click();
    await expect(page).toHaveURL(/#shop$/);
    await expect(alchemistCard).toBeVisible();

    // Deep link directly to #book-1
    await page.goto('/#book-1');
    await expect(details.getByRole('heading', { level: 2, name: 'The Alchemist' })).toBeVisible();

    // Deep link to non-existent book
    await page.goto('/#book-999999');
    await expect(page.getByRole('heading', { name: 'Book unavailable' })).toBeVisible();
    await expect(page.getByRole('alert')).toContainText('We could not find that book.');

    // Wishlist view navigation
    await page.getByRole('button', { name: /Wishlist/ }).click();
    await expect(page.getByRole('heading', { name: /Wishlist/ })).toBeVisible();
  });

  test('Criterion 2: Cart management - adding, updating quantity, enforcing stock limit, removing, and empty cart', async ({ page }) => {
    await page.goto('/');
    const alchemistCard = page.locator('.book-card').filter({ hasText: 'The Alchemist' });
    await expect(alchemistCard).toBeVisible();

    // Add to bag
    await alchemistCard.getByRole('button', { name: /Add to bag/ }).click();
    await expect(page.getByRole('button', { name: /Shopping bag, 1 items/ })).toBeVisible();

    // Open cart
    await page.getByRole('button', { name: /Shopping bag/ }).click();
    await expect(page.getByRole('heading', { name: 'Your bag' })).toBeVisible();
    await expect(page.locator('.cart-row')).toContainText('The Alchemist');

    // Increase quantity
    await page.getByRole('button', { name: 'Increase The Alchemist quantity' }).click();
    await expect(page.locator('.quantity output')).toHaveText('2');
    await expect(page.locator('.line-total')).toHaveText('₹598.00');

    // Decrease quantity
    await page.getByRole('button', { name: 'Decrease The Alchemist quantity' }).click();
    await expect(page.locator('.quantity output')).toHaveText('1');
    await expect(page.locator('.line-total')).toHaveText('₹299.00');

    // Stock limit enforcement: Clean Code has stock 3
    await page.getByRole('button', { name: 'Browse books' }).click();
    const cleanCodeCard = page.locator('.book-card').filter({ hasText: 'Clean Code' });
    await cleanCodeCard.getByRole('button', { name: /Add to bag/ }).click();
    await page.getByRole('button', { name: /Shopping bag/ }).click();

    // Clean Code has 1 in bag now; click increase up to stock limit (stock = 3)
    const incCleanCode = page.getByRole('button', { name: 'Increase Clean Code quantity' });
    await incCleanCode.click(); // 2
    await incCleanCode.click(); // 3
    await expect(incCleanCode).toBeDisabled();

    // Remove Clean Code
    const cleanCodeRow = page.locator('.cart-row').filter({ hasText: 'Clean Code' });
    await cleanCodeRow.getByRole('button', { name: 'Remove' }).click();
    await expect(page.locator('.cart-row').filter({ hasText: 'Clean Code' })).toHaveCount(0);

    // Clear bag completely
    await page.getByRole('button', { name: 'Clear bag' }).click();
    await expect(page.getByText('Your bag is waiting for a story.')).toBeVisible();
    await expect(page.locator('.cart-row')).toHaveCount(0);
  });

  test('Criterion 3: Successful and failed checkout validation and payment simulation', async ({ page, request }) => {
    await page.goto('/');
    const alchemistCard = page.locator('.book-card').filter({ hasText: 'The Alchemist' });
    await alchemistCard.getByRole('button', { name: /Add to bag/ }).click();
    await page.getByRole('button', { name: /Shopping bag/ }).click();
    await page.getByRole('button', { name: /Continue to checkout/ }).click();

    // Failed Checkout: Empty required fields
    await page.getByRole('button', { name: /Place order/ }).click();
    await expect(page.getByText('Full name is required.')).toBeVisible();
    await expect(page.getByText('Address is required.')).toBeVisible();
    await expect(page.getByText('City is required.')).toBeVisible();
    await expect(page.getByText('State / region is required.')).toBeVisible();
    await expect(page.getByText('Postal code is required.')).toBeVisible();
    await expect(page.getByText('Country is required.')).toBeVisible();
    await expect(page.getByText('Thank you for your order.')).toHaveCount(0);

    // Failed Checkout: Invalid email format
    await page.locator('#field-email').fill('invalid-email-address');
    await page.getByRole('button', { name: /Place order/ }).click();
    await expect(page.getByText('Enter a valid email address.')).toBeVisible();

    // Failed Checkout: Declined payment simulation
    await fillValidShippingDetails(page);
    await page.getByLabel('Simulate declined payment').check();
    const failurePromise = page.waitForResponse((res) => res.url().endsWith('/api/orders') && res.request().method() === 'POST');
    await page.getByRole('button', { name: /Place order/ }).click();
    const failureResponse = await failurePromise;
    expect(failureResponse.status()).toBe(402);
    await expect(page.getByRole('status')).toContainText('payment simulator declined');
    // Ensure no false positive confirmation screen is shown
    await expect(page.getByText('Thank you for your order.')).toHaveCount(0);

    // Successful Checkout: Switch to success payment simulation and submit
    await page.getByLabel('Simulate successful payment').check();
    const successPromise = page.waitForResponse((res) => res.url().endsWith('/api/orders') && res.request().method() === 'POST');
    await page.getByRole('button', { name: /Place order/ }).click();
    const successResponse = await successPromise;
    expect(successResponse.status()).toBe(201);
    const order = await successResponse.json();

    // Verify order confirmation displayed
    await expect(page.getByText('Thank you for your order.')).toBeVisible();
    await expect(page.getByText(order.order_id)).toBeVisible();
    await expect(page.locator('.confirmation-card')).toContainText(`₹${order.total.toFixed(2)}`);
  });

  test('Criterion 4: Order confirmation details and order history lookup', async ({ page }) => {
    // 1. Place an order first to establish a confirmed order
    await page.goto('/');
    const alchemistCard = page.locator('.book-card').filter({ hasText: 'The Alchemist' });
    await alchemistCard.getByRole('button', { name: /Add to bag/ }).click();
    await page.getByRole('button', { name: /Shopping bag/ }).click();
    await page.getByRole('button', { name: /Continue to checkout/ }).click();
    await fillValidShippingDetails(page);
    await page.getByLabel('Simulate successful payment').check();

    const successPromise = page.waitForResponse((res) => res.url().endsWith('/api/orders') && res.request().method() === 'POST');
    await page.getByRole('button', { name: /Place order/ }).click();
    const successResponse = await successPromise;
    expect(successResponse.status()).toBe(201);
    const order = await successResponse.json();

    // Verify Order Confirmation elements
    await expect(page.getByRole('heading', { name: 'Thank you for your order.' })).toBeVisible();
    await expect(page.locator('.confirmation-card')).toContainText(order.order_id);
    await expect(page.locator('.confirmation-card')).toContainText('CONFIRMED');
    await expect(page.locator('.confirmation-card')).toContainText(`₹${order.total.toFixed(2)}`);
    await expect(page.locator('.confirmation-card')).toContainText('The Alchemist');

    // 2. Navigate to Order History view
    await page.getByRole('button', { name: 'Orders' }).click();
    await expect(page.getByRole('heading', { name: 'Order history' })).toBeVisible();

    // Verify recently placed order appears in order history
    await expect(page.locator('.order-card').filter({ hasText: order.order_id })).toBeVisible();

    // 3. Test Order Lookup functionality
    // Lookup empty order ID
    await page.locator('#order-lookup-id').fill('');
    await page.getByRole('button', { name: 'Find order' }).click();
    await expect(page.getByText('Enter an order ID.')).toBeVisible();

    // Lookup non-existent/invalid order ID
    await page.locator('#order-lookup-id').fill('invalid-order-99999');
    await page.getByRole('button', { name: 'Find order' }).click();
    await expect(page.locator('.order-error')).toContainText("We couldn't find an order with that ID.");

    // Lookup valid order ID
    await page.locator('#order-lookup-id').fill(order.order_id);
    await page.getByRole('button', { name: 'Find order' }).click();
    await expect(page.locator('.order-card').filter({ hasText: order.order_id })).toBeVisible();
  });

  test('Criterion 5 / Direct API Contract Check: Books, Cart, Orders, and Quote endpoints', async ({ request }) => {
    // 1. Books API
    const booksRes = await request.get('http://127.0.0.1:8080/api/books');
    expect(booksRes.status()).toBe(200);
    const books = await booksRes.json();
    expect(Array.isArray(books)).toBe(true);
    expect(books.length).toBeGreaterThan(0);
    const firstBook = books[0];
    expect(firstBook).toHaveProperty('id');
    expect(firstBook).toHaveProperty('title');
    expect(firstBook).toHaveProperty('author');
    expect(firstBook).toHaveProperty('price');
    expect(firstBook).toHaveProperty('stock');

    // Book by ID
    const bookRes = await request.get(`http://127.0.0.1:8080/api/books/${firstBook.id}`);
    expect(bookRes.status()).toBe(200);
    const book = await bookRes.json();
    expect(book.title).toBe(firstBook.title);

    // Unknown book ID
    const notFoundRes = await request.get('http://127.0.0.1:8080/api/books/999999');
    expect(notFoundRes.status()).toBe(404);

    // 2. Cart API
    const cartRes = await request.get('http://127.0.0.1:8080/api/cart');
    expect(cartRes.status()).toBe(200);
    const cartData = await cartRes.json();
    expect(cartData).toHaveProperty('items');
    expect(cartData).toHaveProperty('subtotal');

    // Update cart
    const updateCartRes = await request.put(`http://127.0.0.1:8080/api/cart/items/${firstBook.id}`, {
      data: { quantity: 2 },
    });
    expect(updateCartRes.status()).toBe(200);
    const updatedCart = await updateCartRes.json();
    expect(updatedCart.items.find((i) => i.book_id === firstBook.id)?.quantity).toBe(2);

    // Clear cart
    const clearRes = await request.delete('http://127.0.0.1:8080/api/cart');
    expect(clearRes.status()).toBe(200);
    const clearedCart = await clearRes.json();
    expect(clearedCart.items.length).toBe(0);

    // 3. Quote API
    const quoteRes = await request.post('http://127.0.0.1:8080/api/orders/quote', {
      data: { items: [{ book_id: firstBook.id, quantity: 2 }] },
    });
    expect(quoteRes.status()).toBe(200);
    const quote = await quoteRes.json();
    expect(quote.item_count).toBe(2);
    expect(quote.total).toBe(firstBook.price * 2);

    // 4. Orders API: Insufficient Stock
    const outOfStockRes = await request.post('http://127.0.0.1:8080/api/orders', {
      data: {
        items: [{ book_id: 3, quantity: 9999 }],
        customer: {
          name: 'Test',
          email: 'test@example.com',
          address_line: '1 Test St',
          city: 'City',
          state: 'State',
          postal_code: '12345',
          country: 'Country',
        },
      },
    });
    expect(outOfStockRes.status()).toBe(409);

    // Orders API: Payment declined simulation
    const declinedRes = await request.post('http://127.0.0.1:8080/api/orders', {
      data: {
        items: [{ book_id: 1, quantity: 1 }],
        customer: {
          name: 'Test',
          email: 'test@example.com',
          address_line: '1 Test St',
          city: 'City',
          state: 'State',
          postal_code: '12345',
          country: 'Country',
        },
        payment_scenario: 'failure',
      },
    });
    expect(declinedRes.status()).toBe(402);

    // Orders API: Successful creation
    const createRes = await request.post('http://127.0.0.1:8080/api/orders', {
      data: {
        items: [{ book_id: 1, quantity: 1 }],
        customer: {
          name: 'Test',
          email: 'test@example.com',
          address_line: '1 Test St',
          city: 'City',
          state: 'State',
          postal_code: '12345',
          country: 'Country',
        },
        payment_scenario: 'success',
      },
    });
    expect(createRes.status()).toBe(201);
    const createdOrder = await createRes.json();
    expect(createdOrder).toHaveProperty('order_id');
    expect(createdOrder.status).toBe('CONFIRMED');

    // Retrieve order by ID
    const getOrderRes = await request.get(`http://127.0.0.1:8080/api/orders/${createdOrder.order_id}`);
    expect(getOrderRes.status()).toBe(200);
    const fetchedOrder = await getOrderRes.json();
    expect(fetchedOrder.order_id).toBe(createdOrder.order_id);
  });

});

