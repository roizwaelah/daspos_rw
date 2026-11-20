# Remote Provider Edge Bridge Contract

Dokumen ini menjelaskan kontrak request/response untuk provider remote non-Supabase (`firebase`, `appwrite`, `pocketbase`, `custom_api`) pada DasPos.

## Endpoint

- URL diisi dari menu `Pilih Database > Remote > URL Edge Function/Bridge`
- Method: `POST`
- Content-Type: `application/json`

## Header opsional

- `x-api-key`: dari field `API Key`
- `x-edge-secret`: dari field `Edge Secret`
- `Authorization: Bearer <token>`: akan dikirim jika ada access token aktif

## Body umum

```json
{
  "provider": "firebase",
  "action": "health.check",
  "outlet_id": "optional",
  "user_id": "optional",
  "base_url": "optional",
  "payload": {}
}
```

## Format response yang direkomendasikan

```json
{
  "success": true,
  "message": "optional",
  "data": {}
}
```

Jika gagal:

```json
{
  "success": false,
  "message": "alasan error"
}
```

## Daftar action yang dipanggil aplikasi

- `health.check`
- `auth.login`
- `auth.register`
- `products.add`
- `products.update`
- `products.delete`
- `products.has_stock`
- `products.reduce_stock`
- `products.increase_stock`
- `products.list`
- `products.get_by_id`
- `transactions.save`
- `transactions.list`
- `transactions.get_by_id`
- `transactions.delete`
- `transactions.report_period`
- `cashiers.create`
- `cashiers.list`

## Shape payload penting

### Product payload

`products.add` dan `products.update`:

```json
{
  "product": {
    "id": "uuid",
    "name": "AIR MINERAL",
    "price": 3000,
    "stock": 100,
    "price_ecer": 3000,
    "price_renteng": 28000,
    "price_pak": 55000,
    "price_karton": 260000,
    "factor_renteng": 10,
    "factor_pak": 20,
    "factor_karton": 100
  }
}
```

### Transaction save payload

```json
{
  "items": [
    {
      "product_id": "uuid",
      "product_name": "AIR MINERAL",
      "qty": 2,
      "unit_code": "karton",
      "unit_factor_to_base": 100,
      "unit_price": 260000,
      "subtotal": 520000,
      "base_qty": 200
    }
  ],
  "total": 520000,
  "pay": 600000,
  "change": 80000
}
```

### auth.register payload

```json
{
  "username": "owner@toko.com",
  "email": "owner@toko.com",
  "password": "rahasia",
  "display_name": "Toko Maju",
  "store_name": "Toko Maju",
  "outlet_name": "Toko Maju",
  "role": "admin"
}
```

`data` response opsional:
- `requires_email_verification: boolean`
- `activation_email_sent: boolean`

### transactions.report_period payload

```json
{
  "monthly": true,
  "year": 2026,
  "month": 4,
  "day": 29
}
```

## Shape data response yang dipakai parser aplikasi

- Product list/get:
  - `id`, `name`, `price`, `stock`
  - opsional: `price_ecer`, `price_renteng`, `price_pak`, `price_karton`, `factor_renteng`, `factor_pak`, `factor_karton`
- Transaction list/get:
  - `id` atau `transaction_id`
  - `date`, `time`, `total`, `pay`, `change`
  - `items[]` dengan `product_id`, `product_name`, `qty`, `unit_code`, `unit_factor_to_base`, `unit_price`
