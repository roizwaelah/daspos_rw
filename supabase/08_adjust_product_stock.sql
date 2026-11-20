-- Atomic stock adjustment RPC.
--
-- Replaces the app's previous read-modify-write stock updates, which lost
-- updates when multiple devices adjusted the same product concurrently.
-- Security definer is required so cashier sessions (which cannot PATCH
-- products under the products_write_admin_owner RLS policy) can still restore
-- stock after deleting a transaction; authorization is enforced explicitly.

create or replace function public.adjust_product_stock(
  p_product_id uuid,
  p_outlet_id uuid,
  p_delta integer
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_product_outlet uuid;
  v_track_stock boolean;
  v_caller_role text;
  v_caller_outlet uuid;
begin
  if auth.uid() is null then
    raise exception 'Not authenticated';
  end if;

  if p_delta is null or p_delta = 0 then
    return;
  end if;

  select outlet_id, track_stock into v_product_outlet, v_track_stock
  from public.products
  where id = p_product_id;

  if v_product_outlet is null then
    raise exception 'Product % does not exist', p_product_id;
  end if;

  if v_product_outlet is distinct from p_outlet_id then
    raise exception 'Product % does not belong to outlet %', p_product_id, p_outlet_id;
  end if;

  select role, outlet_id into v_caller_role, v_caller_outlet
  from public.profiles
  where id = auth.uid();

  if v_caller_role is null
     or v_caller_role not in ('owner', 'admin', 'kasir')
     or v_caller_outlet is distinct from v_product_outlet then
    raise exception 'Role is not allowed';
  end if;

  if not coalesce(v_track_stock, true) then
    return;
  end if;

  update public.products
  set stock = greatest(0, stock + p_delta),
      updated_at = now()
  where id = p_product_id;
end;
$$;

revoke all on function public.adjust_product_stock(uuid, uuid, integer) from public;
grant execute on function public.adjust_product_stock(uuid, uuid, integer) to authenticated;
