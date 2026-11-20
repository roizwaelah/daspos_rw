# create-cashier Edge Function

Fungsi ini membuat user kasir menggunakan `auth.admin.createUser` (service role), sehingga akun kasir **langsung terverifikasi email** (`email_confirm=true`) dan bisa login tanpa menunggu verifikasi.

## Deploy

1. Login dan link project:

```bash
supabase login
supabase link --project-ref <PROJECT_REF>
```

2. Deploy function:

```bash
supabase functions deploy create-cashier --no-verify-jwt=false
```

3. Set secret (jika belum):

```bash
supabase secrets set SUPABASE_URL=https://<PROJECT_REF>.supabase.co
supabase secrets set SUPABASE_ANON_KEY=<ANON_KEY>
supabase secrets set SUPABASE_SERVICE_ROLE_KEY=<SERVICE_ROLE_KEY>
```

## Request Body

Semua request memakai method `POST`. Function tidak lagi menerima `GET` untuk operasi admin.

### Buat kasir

```json
{
  "action": "create",
  "email": "kasir@example.com",
  "password": "rahasia",
  "display_name": "Kasir 01",
  "outlet_id": "<UUID_OUTLET_ADMIN>"
}
```

`outlet_id` opsional. Jika tidak diisi, function akan memakai outlet admin yang memanggil function.

### List kasir

```json
{
  "action": "list"
}
```

## Catatan

- Hanya role `admin/owner` yang bisa membuat kasir.
- Tidak bisa membuat kasir untuk outlet lain.
- App Android memanggil endpoint:
  `https://<PROJECT_REF>.supabase.co/functions/v1/create-cashier`
