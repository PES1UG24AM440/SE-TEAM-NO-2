const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const repoRoot = path.resolve(__dirname, '..');
const schemaSql = fs.readFileSync(path.join(repoRoot, 'database', 'schema.sql'), 'utf8');
const additionalSql = fs.readFileSync(path.join(repoRoot, 'database', 'additional_books_seed.sql'), 'utf8');

// Parse initial books from SQL seed files
const booksMap = new Map();
const insertRegex = /\(\s*(\d+)\s*,\s*'((?:[^']|'')*)'\s*,\s*'((?:[^']|'')*)'\s*,\s*'((?:[^']|'')*)'\s*,\s*'((?:[^']|'')*)'\s*,\s*([\d.]+)\s*,\s*(\d+)\s*\)/g;

for (const sql of [schemaSql, additionalSql]) {
  let match;
  while ((match = insertRegex.exec(sql)) !== null) {
    const id = Number(match[1]);
    booksMap.set(id, {
      id,
      title: match[2].replace(/''/g, "'"),
      author: match[3].replace(/''/g, "'"),
      category: match[4].replace(/''/g, "'"),
      description: match[5].replace(/''/g, "'"),
      price: parseFloat(match[6]),
      stock: parseInt(match[7], 10),
    });
  }
}

// In-memory state
let cart = {}; // bookId -> quantity
const orders = new Map();
const idempotencyMap = new Map();

function getCartResponse() {
  const items = [];
  let subtotal = 0;
  for (const [idStr, quantity] of Object.entries(cart)) {
    const id = Number(idStr);
    const book = booksMap.get(id);
    if (!book) continue;
    const line_total = Number((book.price * quantity).toFixed(2));
    subtotal += line_total;
    items.push({
      book_id: book.id,
      title: book.title,
      author: book.author,
      category: book.category,
      description: book.description,
      quantity,
      stock: book.stock,
      unit_price: book.price,
      line_total,
    });
  }
  return { items, subtotal: Number(subtotal.toFixed(2)) };
}

function calculateTotals(items) {
  let subtotal = 0;
  let itemCount = 0;
  const lineItems = [];
  for (const item of items) {
    const book = booksMap.get(item.book_id);
    if (!book) continue;
    const qty = item.quantity;
    itemCount += qty;
    const lineTotal = Number((book.price * qty).toFixed(2));
    subtotal += lineTotal;
    lineItems.push({
      book_id: book.id,
      title: book.title,
      author: book.author,
      quantity: qty,
      unit_price: book.price,
      line_total: lineTotal,
    });
  }
  const shipping = 0;
  const tax = 0;
  const discount = 0;
  const total = Number((subtotal + shipping + tax - discount).toFixed(2));
  return {
    items: lineItems,
    item_count: itemCount,
    subtotal: Number(subtotal.toFixed(2)),
    shipping,
    tax,
    discount,
    total,
  };
}

const server = http.createServer((req, res) => {
  const parsedUrl = new URL(req.url, `http://${req.headers.host || '127.0.0.1:8080'}`);
  const pathname = parsedUrl.pathname;
  const method = req.method.toUpperCase();

  // CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Idempotency-Key');
  if (method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  let body = '';
  req.on('data', (chunk) => { body += chunk; });
  req.on('end', () => {
    let jsonBody = null;
    if (body) {
      try { jsonBody = JSON.parse(body); } catch (_) {}
    }

    const sendJson = (status, data) => {
      res.writeHead(status, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(data));
    };

    // 1. GET /api/books
    if (method === 'GET' && pathname === '/api/books') {
      const q = (parsedUrl.searchParams.get('q') || '').trim().toLowerCase();
      const category = (parsedUrl.searchParams.get('category') || '').trim();
      let list = Array.from(booksMap.values()).sort((a, b) => a.id - b.id);
      if (q) {
        list = list.filter((b) => b.title.toLowerCase().includes(q) || b.author.toLowerCase().includes(q));
      }
      if (category && category !== 'All') {
        list = list.filter((b) => b.category.toLowerCase() === category.toLowerCase());
      }
      return sendJson(200, list);
    }

    // 2. GET /api/books/:id
    const bookMatch = pathname.match(/^\/api\/books\/(\d+)$/);
    if (method === 'GET' && bookMatch) {
      const id = Number(bookMatch[1]);
      const book = booksMap.get(id);
      if (!book) {
        return sendJson(404, { status: 404, error: 'Not Found', message: 'Book not found' });
      }
      return sendJson(200, book);
    }

    // 3. GET /api/cart
    if (method === 'GET' && pathname === '/api/cart') {
      return sendJson(200, getCartResponse());
    }

    // 4. PUT /api/cart/items/:bookId
    const cartItemMatch = pathname.match(/^\/api\/cart\/items\/(\d+)$/);
    if (method === 'PUT' && cartItemMatch) {
      const bookId = Number(cartItemMatch[1]);
      const book = booksMap.get(bookId);
      if (!book) {
        return sendJson(404, { detail: { code: 'book_not_found', message: 'Book not found' } });
      }
      const qty = jsonBody?.quantity ?? 1;
      if (qty <= 0) {
        delete cart[bookId];
      } else {
        cart[bookId] = qty;
      }
      return sendJson(200, getCartResponse());
    }

    // 5. DELETE /api/cart/items/:bookId
    if (method === 'DELETE' && cartItemMatch) {
      const bookId = Number(cartItemMatch[1]);
      delete cart[bookId];
      return sendJson(200, getCartResponse());
    }

    // 6. DELETE /api/cart
    if (method === 'DELETE' && pathname === '/api/cart') {
      cart = {};
      return sendJson(200, getCartResponse());
    }

    // 7. POST /api/orders/quote
    if (method === 'POST' && pathname === '/api/orders/quote') {
      const items = jsonBody?.items || [];
      const quote = calculateTotals(items);
      return sendJson(200, quote);
    }

    // 8. POST /api/orders
    if (method === 'POST' && pathname === '/api/orders') {
      const idempotencyKey = req.headers['idempotency-key'];
      if (idempotencyKey && idempotencyMap.has(idempotencyKey)) {
        return sendJson(200, idempotencyMap.get(idempotencyKey));
      }

      const items = jsonBody?.items || [];
      const paymentScenario = jsonBody?.payment_scenario || 'success';
      const customer = jsonBody?.customer || {};

      // Check stock
      for (const item of items) {
        const book = booksMap.get(item.book_id);
        if (!book) {
          return sendJson(404, { detail: { code: 'book_not_found', message: `Book ID ${item.book_id} was not found.` } });
        }
        if (book.stock < item.quantity) {
          return sendJson(409, {
            detail: {
              code: 'insufficient_stock',
              message: `Only ${book.stock} copy/copies of '${book.title}' remain.`,
              book_id: book.id,
              available: book.stock,
            },
          });
        }
      }

      // Check payment simulation
      if (paymentScenario === 'failure') {
        return sendJson(402, {
          detail: {
            code: 'payment_failed',
            message: 'The payment simulator declined this payment. No order was confirmed.',
          },
        });
      }

      // Decrement stock
      for (const item of items) {
        const book = booksMap.get(item.book_id);
        book.stock -= item.quantity;
      }

      const totals = calculateTotals(items);
      const orderId = crypto.randomUUID();
      const orderResponse = {
        order_id: orderId,
        status: 'CONFIRMED',
        subtotal: totals.subtotal,
        shipping: totals.shipping,
        tax: totals.tax,
        discount: totals.discount,
        total: totals.total,
        items: totals.items.map((it) => ({
          book_id: it.book_id,
          title: it.title,
          quantity: it.quantity,
          unit_price: it.unit_price,
          line_total: it.line_total,
        })),
      };

      orders.set(orderId, orderResponse);
      if (idempotencyKey) {
        idempotencyMap.set(idempotencyKey, orderResponse);
      }

      return sendJson(201, orderResponse);
    }

    // 9. GET /api/orders
    if (method === 'GET' && pathname === '/api/orders') {
      return sendJson(200, Array.from(orders.values()));
    }

    // 10. GET /api/orders/:orderId
    const orderMatch = pathname.match(/^\/api\/orders\/([a-zA-Z0-9-]+)$/);
    if (method === 'GET' && orderMatch) {
      const orderId = orderMatch[1];
      const order = orders.get(orderId);
      if (!order) {
        return sendJson(404, { detail: { code: 'order_not_found', message: 'Order not found.' } });
      }
      return sendJson(200, order);
    }

    // Fallback 404
    sendJson(404, { detail: { message: 'Not found.' } });
  });
});

const PORT = 8080;
const HOST = '127.0.0.1';
server.listen(PORT, HOST, () => {
  console.log(`Mock backend listening on http://${HOST}:${PORT}`);
});

