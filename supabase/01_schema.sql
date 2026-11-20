-- DasPos Supabase schema for multi-device and multi-cashier use.
-- Run this first in Supabase SQL Editor.

create extension if not exists pgcrypto;

create table if not exists public.outlets (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  name text not null,
  address text,
  phone text,
  email text,
  updated_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text,
  role text not null check (role in ('owner', 'admin', 'kasir')),
  outlet_id uuid not null references public.outlets(id) on delete restrict,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.products (
  id uuid primary key default gen_random_uuid(),
  outlet_id uuid not null references public.outlets(id) on delete restrict,
  legacy_id text,
  code text,
  name text not null,
  category text,
  price numeric(12, 2) not null check (price >= 0),
  price_ecer numeric(12, 2) not null default 0 check (price_ecer >= 0),
  price_renteng numeric(12, 2) not null default 0 check (price_renteng >= 0),
  price_pak numeric(12, 2) not null default 0 check (price_pak >= 0),
  price_karton numeric(12, 2) not null default 0 check (price_karton >= 0),
  factor_renteng integer not null default 10 check (factor_renteng > 0),
  factor_pak integer not null default 20 check (factor_pak > 0),
  factor_karton integer not null default 100 check (factor_karton > 0),
  stock integer not null default 0 check (stock >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (outlet_id, legacy_id)
);

create unique index if not exists products_outlet_code_uq
  on public.products (outlet_id, code)
  where code is not null and btrim(code) <> '';

create table if not exists public.cashier_shifts (
  id uuid primary key default gen_random_uuid(),
  outlet_id uuid not null references public.outlets(id) on delete restrict,
  cashier_id uuid not null references public.profiles(id) on delete restrict,
  opened_at timestamptz not null default now(),
  closed_at timestamptz,
  opening_cash numeric(12, 2) not null default 0,
  closing_cash numeric(12, 2),
  status text not null default 'open' check (status in ('open', 'closed'))
);

create table if not exists public.transactions (
  id uuid primary key default gen_random_uuid(),
  outlet_id uuid not null references public.outlets(id) on delete restrict,
  legacy_id text,
  cashier_id uuid not null references public.profiles(id) on delete restrict,
  shift_id uuid references public.cashier_shifts(id) on delete set null,
  trx_no text,
  trx_at timestamptz not null default now(),
  total numeric(12, 2) not null check (total >= 0),
  pay numeric(12, 2) not null default 0 check (pay >= 0),
  change_amount numeric(12, 2) not null default 0,
  device_id text,
  sync_source text not null default 'supabase',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (outlet_id, legacy_id)
);

create index if not exists transactions_outlet_trx_at_idx
  on public.transactions (outlet_id, trx_at desc);

create table if not exists public.transaction_items (
  id uuid primary key default gen_random_uuid(),
  transaction_id uuid not null references public.transactions(id) on delete cascade,
  product_id uuid references public.products(id) on delete set null,
  legacy_product_id text,
  product_name text not null,
  price numeric(12, 2) not null check (price >= 0),
  qty integer not null check (qty > 0),
  created_at timestamptz not null default now()
);

create index if not exists transaction_items_transaction_id_idx
  on public.transaction_items (transaction_id);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists trg_profiles_updated_at on public.profiles;
create trigger trg_profiles_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

drop trigger if exists trg_outlets_updated_at on public.outlets;
create trigger trg_outlets_updated_at
before update on public.outlets
for each row execute function public.set_updated_at();

drop trigger if exists trg_products_updated_at on public.products;
create trigger trg_products_updated_at
before update on public.products
for each row execute function public.set_updated_at();

drop trigger if exists trg_transactions_updated_at on public.transactions;
create trigger trg_transactions_updated_at
before update on public.transactions
for each row execute function public.set_updated_at();

create or replace function public.current_role()
returns text
language sql
stable
security definer
set search_path = public
as $$
  select p.role from public.profiles p where p.id = auth.uid()
$$;

create or replace function public.current_outlet_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select p.outlet_id from public.profiles p where p.id = auth.uid()
$$;

grant execute on function public.current_role() to authenticated;
grant execute on function public.current_outlet_id() to authenticated;

alter table public.outlets enable row level security;
alter table public.profiles enable row level security;
alter table public.products enable row level security;
alter table public.cashier_shifts enable row level security;
alter table public.transactions enable row level security;
alter table public.transaction_items enable row level security;

drop policy if exists outlets_select on public.outlets;
create policy outlets_select on public.outlets
for select to authenticated
using (id = public.current_outlet_id());

drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles
for select to authenticated
using (outlet_id = public.current_outlet_id());

drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self on public.profiles
for update to authenticated
using (id = auth.uid())
with check (id = auth.uid() and outlet_id = public.current_outlet_id());

drop policy if exists products_select on public.products;
create policy products_select on public.products
for select to authenticated
using (outlet_id = public.current_outlet_id());

drop policy if exists products_write_admin_owner on public.products;
create policy products_write_admin_owner on public.products
for all to authenticated
using (
  outlet_id = public.current_outlet_id()
  and public.current_role() in ('owner', 'admin')
)
with check (
  outlet_id = public.current_outlet_id()
  and public.current_role() in ('owner', 'admin')
);

drop policy if exists shifts_select on public.cashier_shifts;
create policy shifts_select on public.cashier_shifts
for select to authenticated
using (outlet_id = public.current_outlet_id());

drop policy if exists shifts_insert_kasir on public.cashier_shifts;
create policy shifts_insert_kasir on public.cashier_shifts
for insert to authenticated
with check (
  outlet_id = public.current_outlet_id()
  and cashier_id = auth.uid()
  and public.current_role() in ('owner', 'admin', 'kasir')
);

drop policy if exists shifts_update_kasir_owner_admin on public.cashier_shifts;
create policy shifts_update_kasir_owner_admin on public.cashier_shifts
for update to authenticated
using (
  outlet_id = public.current_outlet_id()
  and (
    public.current_role() in ('owner', 'admin')
    or cashier_id = auth.uid()
  )
)
with check (
  outlet_id = public.current_outlet_id()
  and (
    public.current_role() in ('owner', 'admin')
    or cashier_id = auth.uid()
  )
);

drop policy if exists transactions_select on public.transactions;
create policy transactions_select on public.transactions
for select to authenticated
using (outlet_id = public.current_outlet_id());

drop policy if exists transactions_insert on public.transactions;
create policy transactions_insert on public.transactions
for insert to authenticated
with check (
  outlet_id = public.current_outlet_id()
  and cashier_id = auth.uid()
  and public.current_role() in ('owner', 'admin', 'kasir')
);

drop policy if exists transactions_update_delete_admin_owner on public.transactions;
create policy transactions_update_delete_admin_owner on public.transactions
for all to authenticated
using (
  outlet_id = public.current_outlet_id()
  and public.current_role() in ('owner', 'admin')
)
with check (
  outlet_id = public.current_outlet_id()
  and public.current_role() in ('owner', 'admin')
);

drop policy if exists transaction_items_select on public.transaction_items;
create policy transaction_items_select on public.transaction_items
for select to authenticated
using (
  exists (
    select 1
    from public.transactions t
    where t.id = transaction_items.transaction_id
      and t.outlet_id = public.current_outlet_id()
  )
);

drop policy if exists transaction_items_insert on public.transaction_items;
create policy transaction_items_insert on public.transaction_items
for insert to authenticated
with check (
  exists (
    select 1
    from public.transactions t
    where t.id = transaction_items.transaction_id
      and t.outlet_id = public.current_outlet_id()
      and t.cashier_id = auth.uid()
  )
);

grant select, insert, update, delete on public.outlets to authenticated;
grant select, insert, update, delete on public.profiles to authenticated;
grant select, insert, update, delete on public.products to authenticated;
grant select, insert, update, delete on public.cashier_shifts to authenticated;
grant select, insert, update, delete on public.transactions to authenticated;
grant select, insert, update, delete on public.transaction_items to authenticated;
