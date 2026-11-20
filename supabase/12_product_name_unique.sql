-- Enforce one product name per outlet (case-insensitive) on existing DBs.
-- Idempotent: safe to run multiple times.
--
-- IMPORTANT: if duplicate names already exist, the index creation will fail.
-- Run the audit query below first, rename/remove duplicates manually, then
-- re-run this script.
--
-- Audit query (run before applying):
--   select outlet_id, lower(name) as lname, count(*)
--   from public.products
--   group by outlet_id, lower(name)
--   having count(*) > 1;

do $$
declare
  v_dupes int;
begin
  select count(*) into v_dupes from (
    select 1
    from public.products
    group by outlet_id, lower(name)
    having count(*) > 1
  ) d;

  if v_dupes > 0 then
    raise exception 'Cannot create unique index: % duplicate product name group(s) exist. Resolve them first (see audit query in file header).', v_dupes;
  end if;
end $$;

create unique index if not exists products_outlet_name_uq
  on public.products (outlet_id, lower(name));
