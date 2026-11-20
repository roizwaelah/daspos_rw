-- Add tier-pricing fields for cross-device sync (ecer/renteng/pak/karton).
-- Safe to run multiple times.

alter table if exists public.products
  add column if not exists price_ecer numeric(12, 2) not null default 0 check (price_ecer >= 0),
  add column if not exists price_renteng numeric(12, 2) not null default 0 check (price_renteng >= 0),
  add column if not exists price_pak numeric(12, 2) not null default 0 check (price_pak >= 0),
  add column if not exists price_karton numeric(12, 2) not null default 0 check (price_karton >= 0),
  add column if not exists factor_renteng integer not null default 10 check (factor_renteng > 0),
  add column if not exists factor_pak integer not null default 20 check (factor_pak > 0),
  add column if not exists factor_karton integer not null default 100 check (factor_karton > 0);

update public.products
set
  price_ecer = case when coalesce(price_ecer, 0) > 0 then price_ecer else coalesce(price, 0) end,
  factor_renteng = case when coalesce(factor_renteng, 0) > 0 then factor_renteng else 10 end,
  factor_pak = case when coalesce(factor_pak, 0) > 0 then factor_pak else 20 end,
  factor_karton = case when coalesce(factor_karton, 0) > 0 then factor_karton else 100 end;

update public.products
set
  price_renteng = case when coalesce(price_renteng, 0) > 0 then price_renteng else price_ecer * factor_renteng end,
  price_pak = case when coalesce(price_pak, 0) > 0 then price_pak else price_ecer * factor_pak end,
  price_karton = case when coalesce(price_karton, 0) > 0 then price_karton else price_ecer * factor_karton end;
