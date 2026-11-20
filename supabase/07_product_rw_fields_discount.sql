-- rw parity migration: product barcode/unit/cost/track_stock + transaction
-- discount + per-item unit price override in create_pos_transaction.
--
-- Without these columns the app falls back to legacy payloads and silently
-- loses barcode, unit name, cost price, stock tracking, tier prices, and the
-- transaction discount on the remote side.
--
-- Idempotent: safe to run multiple times. Run after 01..06.

alter table if exists public.products
  add column if not exists barcode text,
  add column if not exists unit_name text not null default 'pcs',
  add column if not exists cost_price numeric(12, 2) not null default 0 check (cost_price >= 0),
  add column if not exists track_stock boolean not null default true;

alter table if exists public.transactions
  add column if not exists discount numeric(12, 2) not null default 0 check (discount >= 0);

-- Recreate create_pos_transaction:
--  * new trailing param p_discount (default 0) so older app builds that do not
--    send it keep working;
--  * transaction_items.price now honours an optional per-item "price" override
--    so tier/unit prices (e.g. RENTENG/KARTON) are stored instead of the
--    product's base ecer price.
-- The old 8-arg signature is dropped first so only one candidate remains.

drop function if exists public.create_pos_transaction(uuid, uuid, numeric, numeric, numeric, text, text, jsonb);

create or replace function public.create_pos_transaction(
  p_outlet_id uuid,
  p_cashier_id uuid,
  p_total numeric,
  p_pay numeric,
  p_change_amount numeric,
  p_device_id text,
  p_legacy_id text,
  p_items jsonb,
  p_discount numeric default 0
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
  v_item_price numeric(12,2);
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
    discount,
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
    greatest(coalesce(p_discount, 0), 0),
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

    -- Optional per-item price override carries the actual unit price charged
    -- (tier units like RENTENG/KARTON); falls back to the product base price.
    v_item_price := coalesce((v_item ->> 'price')::numeric(12,2), v_price);

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
      v_item_price,
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

grant execute on function public.create_pos_transaction(uuid, uuid, numeric, numeric, numeric, text, text, jsonb, numeric) to authenticated;
