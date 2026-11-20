-- Atomic transaction void/delete RPC.
--
-- Restores stock for all tracked items in the transaction and deletes the
-- transaction (plus cascading transaction_items) inside a single DB
-- transaction so partial stock restoration or double-restoration cannot occur.

drop function if exists public.void_pos_transaction(uuid, text);

create or replace function public.void_pos_transaction(
  p_outlet_id uuid,
  p_transaction_ref text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_caller_role text;
  v_caller_outlet uuid;
  v_ref text := btrim(coalesce(p_transaction_ref, ''));
  v_transaction_id uuid;
  v_trx_no text;
  v_item record;
begin
  if auth.uid() is null then
    raise exception 'Not authenticated';
  end if;

  if p_outlet_id is null or v_ref = '' then
    return jsonb_build_object('deleted', false);
  end if;

  select role, outlet_id into v_caller_role, v_caller_outlet
  from public.profiles
  where id = auth.uid();

  if v_caller_role is null
     or v_caller_role not in ('owner', 'admin')
     or v_caller_outlet is distinct from p_outlet_id then
    raise exception 'Role is not allowed';
  end if;

  -- Lock matching transaction row to prevent concurrent duplicate voids.
  select id, trx_no
  into v_transaction_id, v_trx_no
  from public.transactions
  where outlet_id = p_outlet_id
    and (id::text = v_ref or trx_no = v_ref)
  order by (id::text = v_ref) desc
  limit 1
  for update;

  if v_transaction_id is null then
    return jsonb_build_object('deleted', false);
  end if;

  for v_item in
    select product_id, sum(qty)::integer as total_qty
    from public.transaction_items
    where transaction_id = v_transaction_id
      and product_id is not null
      and qty > 0
    group by product_id
    order by product_id
  loop
    update public.products
    set stock = greatest(0, stock + v_item.total_qty),
        updated_at = now()
    where id = v_item.product_id
      and outlet_id = p_outlet_id
      and coalesce(track_stock, true);
  end loop;

  delete from public.transaction_items
  where transaction_id = v_transaction_id;

  delete from public.transactions
  where id = v_transaction_id
    and outlet_id = p_outlet_id;

  return jsonb_build_object(
    'deleted', true,
    'transaction_id', v_transaction_id,
    'trx_no', coalesce(v_trx_no, v_transaction_id::text)
  );
end;
$$;

revoke all on function public.void_pos_transaction(uuid, text) from public;
grant execute on function public.void_pos_transaction(uuid, text) to authenticated;
