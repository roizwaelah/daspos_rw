-- DasPos staging import for legacy backup JSON payload.
-- Run after 01_schema.sql.

create table if not exists public.migration_users_stg (
  batch_id uuid not null,
  legacy_username text not null,
  role text,
  password_hash text,
  created_at timestamptz not null default now()
);

create table if not exists public.migration_products_stg (
  batch_id uuid not null,
  legacy_id text not null,
  name text not null,
  price numeric(12, 2) not null,
  stock integer not null,
  created_at timestamptz not null default now()
);

create table if not exists public.migration_transactions_stg (
  batch_id uuid not null,
  legacy_id text not null,
  trx_at timestamptz not null,
  total numeric(12, 2) not null,
  pay numeric(12, 2) not null,
  change_amount numeric(12, 2) not null,
  created_at timestamptz not null default now()
);

create table if not exists public.migration_transaction_items_stg (
  batch_id uuid not null,
  transaction_legacy_id text not null,
  line_no integer not null,
  legacy_product_id text,
  product_name text not null,
  price numeric(12, 2) not null,
  qty integer not null,
  created_at timestamptz not null default now()
);

create table if not exists public.migration_user_map (
  batch_id uuid not null,
  legacy_username text not null,
  profile_id uuid not null references public.profiles(id) on delete cascade,
  primary key (batch_id, legacy_username)
);

create or replace function public.apply_migration_batch(
  p_batch_id uuid,
  p_outlet_id uuid,
  p_default_cashier_id uuid,
  p_device_id text default 'legacy-import'
)
returns void
language plpgsql
security definer
as $$
begin
  if not exists (select 1 from public.outlets where id = p_outlet_id) then
    raise exception 'Outlet % does not exist', p_outlet_id;
  end if;

  if not exists (
    select 1
    from public.profiles
    where id = p_default_cashier_id
      and outlet_id = p_outlet_id
  ) then
    raise exception 'Default cashier % is not valid in outlet %', p_default_cashier_id, p_outlet_id;
  end if;

  insert into public.products (outlet_id, legacy_id, name, price, stock)
  select
    p_outlet_id,
    s.legacy_id,
    s.name,
    s.price,
    s.stock
  from public.migration_products_stg s
  where s.batch_id = p_batch_id
  on conflict (outlet_id, legacy_id) do update
    set name = excluded.name,
        price = excluded.price,
        stock = excluded.stock,
        updated_at = now();

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
  select
    p_outlet_id,
    t.legacy_id,
    p_default_cashier_id,
    t.legacy_id,
    t.trx_at,
    t.total,
    t.pay,
    t.change_amount,
    p_device_id,
    'legacy-import'
  from public.migration_transactions_stg t
  where t.batch_id = p_batch_id
  on conflict (outlet_id, legacy_id) do update
    set total = excluded.total,
        pay = excluded.pay,
        change_amount = excluded.change_amount,
        trx_at = excluded.trx_at,
        updated_at = now();

  insert into public.transaction_items (
    transaction_id,
    product_id,
    legacy_product_id,
    product_name,
    price,
    qty
  )
  select
    trx.id,
    p.id,
    i.legacy_product_id,
    i.product_name,
    i.price,
    i.qty
  from public.migration_transaction_items_stg i
  join public.transactions trx
    on trx.outlet_id = p_outlet_id
   and trx.legacy_id = i.transaction_legacy_id
  left join public.products p
    on p.outlet_id = p_outlet_id
   and p.legacy_id = i.legacy_product_id
  where i.batch_id = p_batch_id
    and not exists (
      select 1
      from public.transaction_items ti
      where ti.transaction_id = trx.id
        and coalesce(ti.legacy_product_id, '') = coalesce(i.legacy_product_id, '')
        and ti.product_name = i.product_name
        and ti.price = i.price
        and ti.qty = i.qty
    );
end;
$$;

comment on function public.apply_migration_batch(uuid, uuid, uuid, text)
is 'Apply one migration batch from staging to final tables.';

