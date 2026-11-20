-- Add business contact fields to outlets for sync from Store Info page.
-- Run this once on existing projects.

alter table public.outlets
  add column if not exists address text,
  add column if not exists phone text,
  add column if not exists email text,
  add column if not exists updated_at timestamptz not null default now();

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists trg_outlets_updated_at on public.outlets;
create trigger trg_outlets_updated_at
before update on public.outlets
for each row execute function public.set_updated_at();
