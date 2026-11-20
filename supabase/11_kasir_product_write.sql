-- Allow kasir role to write products (add/edit/delete) within own outlet.
-- The Android app grants the product menu per-kasir via MenuAccessStore, so
-- kasir must be able to persist product changes just like admin/owner.
-- Idempotent: safe to run multiple times.

drop policy if exists products_write_admin_owner on public.products;
drop policy if exists products_write_staff on public.products;

create policy products_write_staff on public.products
for all to authenticated
using (
  outlet_id = public.current_outlet_id()
  and public.current_role() in ('owner', 'admin', 'kasir')
)
with check (
  outlet_id = public.current_outlet_id()
  and public.current_role() in ('owner', 'admin', 'kasir')
);
