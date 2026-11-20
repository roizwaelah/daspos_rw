-- Atomic checkout RPC: create transaction + items + stock deduction in one DB transaction.
-- Run this in Supabase SQL Editor after 01_schema.sql.
-- NOTE: superseded by 07_product_rw_fields_discount.sql, which recreates this
-- function with p_discount and a per-item price override. Run files in order.

create or replace function public.create_pos_transaction(
  p_outlet_id uuid,
  p_cashier_id uuid,
  p_total numeric,
  p_pay numeric,
  p_change_amount numeric,
  p_device_id text,
  p_legacy_id text,
  p_items jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_now timestamptz := now();
  v_month_start timestamptz;
  v_next_month_start timestamptz;
  v_seq integer;
  v_trx_no text;
  v_transaction_id uuid;
  v_existing_trx_no text;
  v_role text;
  v_item jsonb;
  v_product_id uuid;
  v_qty integer;
  v_product_name text;
  v_price numeric(12,2);
  v_stock integer;
begin
  if auth.uid() is null then
    raise exception 'Not authenticated';
  end if;

  select role into v_role from public.profiles where id = auth.uid();
  if v_role not in ('owner', 'admin', 'kasir') then
    raise exception 'Role is not allowed';
  end if;

  if p_cashier_id <> auth.uid() then
    raise exception 'cashier_id must match auth.uid()';
  end if;

  if p_outlet_id <> public.current_outlet_id() then
    raise exception 'outlet_id is not allowed';
  end if;

  if p_items is null or jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then
    raise exception 'items must be non-empty JSON array';
  end if;

  -- Serialize checkout creation per outlet/month, including idempotency checks.
  perform pg_advisory_xact_lock(hashtext(p_outlet_id::text || to_char(v_now, 'YYYYMM')));

  if p_legacy_id is not null and btrim(p_legacy_id) <> '' then
    select id, trx_no
    into v_transaction_id, v_existing_trx_no
    from public.transactions
    where outlet_id = p_outlet_id
      and legacy_id = btrim(p_legacy_id)
    limit 1;

    if v_transaction_id is not null then
      return jsonb_build_object(
        'id', v_transaction_id,
        'trx_no', coalesce(v_existing_trx_no, v_transaction_id::text)
      );
    end if;
  end if;

  v_month_start := date_trunc('month', v_now);
  v_next_month_start := v_month_start + interval '1 month';

  select count(*)
  into v_seq
  from public.transactions
  where outlet_id = p_outlet_id
    and trx_at >= v_month_start
    and trx_at < v_next_month_start;

  v_trx_no := to_char(v_now, 'YYMMDD') || lpad((v_seq + 1)::text, 4, '0');

  insert into public.transactions (
    outlet_id,
    legacy_id,
    cashier_id,
    trx_no,
    trx_at,
    total,
    pay,
    change_amount,
    device_id,
    sync_source
  )
  values (
    p_outlet_id,
    nullif(btrim(coalesce(p_legacy_id, '')), ''),
    p_cashier_id,
    v_trx_no,
    v_now,
    coalesce(p_total, 0),
    coalesce(p_pay, 0),
    coalesce(p_change_amount, 0),
    coalesce(p_device_id, 'android-app'),
    'supabase'
  )
  returning id into v_transaction_id;

  for v_item in select value from jsonb_array_elements(p_items)
  loop
    v_product_id := (v_item ->> 'product_id')::uuid;
    v_qty := coalesce((v_item ->> 'qty')::integer, 0);
    if v_qty <= 0 then
      raise exception 'Invalid qty for product %', v_product_id;
    end if;

    select name, price, stock
      into v_product_name, v_price, v_stock
    from public.products
    where id = v_product_id
      and outlet_id = p_outlet_id
    for update;

    if not found then
      raise exception 'Product not found: %', v_product_id;
    end if;

    if v_stock < v_qty then
      raise exception 'Insufficient stock for product %', v_product_id;
    end if;

    update public.products
    set stock = stock - v_qty
    where id = v_product_id
      and outlet_id = p_outlet_id;

    insert into public.transaction_items (
      transaction_id,
      product_id,
      legacy_product_id,
      product_name,
      price,
      qty
    )
    values (
      v_transaction_id,
      v_product_id,
      v_product_id::text,
      v_product_name,
      v_price,
      v_qty
    );
  end loop;

  return jsonb_build_object(
    'transaction_id', v_transaction_id,
    'trx_no', v_trx_no,
    'trx_at', v_now
  );
end;
$$;

grant execute on function public.create_pos_transaction(uuid, uuid, numeric, numeric, numeric, text, text, jsonb) to authenticated;
