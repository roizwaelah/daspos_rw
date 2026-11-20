-- DasPos Supabase hardening:
-- 1) New signup account is auto-provisioned as admin with its own outlet.
-- 2) Cashier profile (role=kasir) can only be managed by admin/owner.
--
-- Run this after 01_schema.sql.

create or replace function public.bootstrap_profile_for_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_display_name text;
  v_outlet_name text;
  v_outlet_code text;
  v_outlet_id uuid;
  v_requested_role text;
  v_requested_outlet_id uuid;
begin
  if exists (select 1 from public.profiles p where p.id = new.id) then
    return new;
  end if;

  v_requested_role := lower(coalesce(new.raw_user_meta_data ->> 'role', ''));
  if nullif(new.raw_user_meta_data ->> 'outlet_id', '') is not null then
    begin
      v_requested_outlet_id := (new.raw_user_meta_data ->> 'outlet_id')::uuid;
    exception when others then
      raise exception 'outlet_id metadata tidak valid';
    end;
  end if;

  v_display_name := coalesce(
    nullif(btrim(new.raw_user_meta_data ->> 'display_name'), ''),
    split_part(coalesce(new.email, ''), '@', 1),
    'Admin'
  );

  -- If metadata explicitly requests cashier with an existing outlet, bind user to that outlet.
  if v_requested_role = 'kasir' and v_requested_outlet_id is not null then
    if not exists (select 1 from public.outlets o where o.id = v_requested_outlet_id) then
      raise exception 'Outlet kasir tidak ditemukan';
    end if;

    insert into public.profiles (id, full_name, role, outlet_id, is_active)
    values (new.id, v_display_name, 'kasir', v_requested_outlet_id, true);
    return new;
  end if;

  v_outlet_name := coalesce(
    nullif(btrim(new.raw_user_meta_data ->> 'outlet_name'), ''),
    nullif(btrim(new.raw_user_meta_data ->> 'store_name'), ''),
    v_display_name || ' Outlet'
  );

  v_outlet_code := 'OUT-' || upper(substr(replace(new.id::text, '-', ''), 1, 8));

  insert into public.outlets (code, name)
  values (v_outlet_code, v_outlet_name)
  returning id into v_outlet_id;

  insert into public.profiles (id, full_name, role, outlet_id, is_active)
  values (new.id, v_display_name, 'admin', v_outlet_id, true);

  return new;
end;
$$;

drop trigger if exists trg_auth_user_bootstrap_profile on auth.users;
create trigger trg_auth_user_bootstrap_profile
after insert on auth.users
for each row execute function public.bootstrap_profile_for_new_user();

-- Lock profile write access:
-- - self update policy removed
-- - only admin/owner can manage kasir profile rows in same outlet
drop policy if exists profiles_update_self on public.profiles;

drop policy if exists profiles_self_deactivate on public.profiles;
create policy profiles_self_deactivate on public.profiles
for update to authenticated
using (
  id = auth.uid()
)
with check (
  id = auth.uid()
  and outlet_id = public.current_outlet_id()
  and role = public.current_role()
  and is_active = false
);

drop policy if exists profiles_insert_kasir_by_admin on public.profiles;
create policy profiles_insert_kasir_by_admin on public.profiles
for insert to authenticated
with check (
  outlet_id = public.current_outlet_id()
  and public.current_role() in ('owner', 'admin')
  and role = 'kasir'
);

drop policy if exists profiles_update_kasir_by_admin on public.profiles;
create policy profiles_update_kasir_by_admin on public.profiles
for update to authenticated
using (
  outlet_id = public.current_outlet_id()
  and role = 'kasir'
  and public.current_role() in ('owner', 'admin')
)
with check (
  outlet_id = public.current_outlet_id()
  and role = 'kasir'
  and public.current_role() in ('owner', 'admin')
);

drop policy if exists profiles_delete_kasir_by_admin on public.profiles;
create policy profiles_delete_kasir_by_admin on public.profiles
for delete to authenticated
using (
  outlet_id = public.current_outlet_id()
  and role = 'kasir'
  and public.current_role() in ('owner', 'admin')
);
