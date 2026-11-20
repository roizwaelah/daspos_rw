# delete-account Edge Function

Fungsi ini melakukan hard delete user dari `auth.users` menggunakan service role key.

## Deploy

1. Login dan link project:

```bash
supabase login
supabase link --project-ref <PROJECT_REF>
```

2. Deploy function:

```bash
supabase functions deploy delete-account --no-verify-jwt=false
```

3. Set secret (jika belum):

```bash
supabase secrets set SUPABASE_URL=https://<PROJECT_REF>.supabase.co
supabase secrets set SUPABASE_ANON_KEY=<ANON_KEY>
supabase secrets set SUPABASE_SERVICE_ROLE_KEY=<SERVICE_ROLE_KEY>
```

4. (Opsional) test invoke:

```bash
supabase functions invoke delete-account --header "Authorization: Bearer <ACCESS_TOKEN>" --body '{"user_id":"<USER_ID>"}'
```

## Mode Purge Outlet

Kirim body berikut untuk menghapus outlet beserta semua data terkait (khusus role `admin/owner`):

```json
{
  "user_id": "<USER_ID_LOGIN>",
  "purge_outlet": true
}
```

Data yang dihapus:
- `transactions` (otomatis termasuk `transaction_items` via cascade)
- `cashier_shifts`
- `products`
- semua user pada outlet tersebut (`auth.users`)
- `outlets`

## Catatan

- Function hanya mengizinkan user menghapus akun dirinya sendiri.
- App Android memanggil endpoint:
  `https://<PROJECT_REF>.supabase.co/functions/v1/delete-account`
