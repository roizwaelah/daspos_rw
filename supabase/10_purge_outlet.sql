-- Atomic outlet purge used by the delete-account edge function.
--
-- PostgREST deletes are one-table-per-request, so the previous edge-function
-- implementation could leave an outlet half-deleted on partial failure. This
-- RPC performs every outlet-scoped delete inside a single transaction.
--
-- Auth-user deletion cannot join this transaction (it lives in the auth
-- schema and is only reachable via the admin API); the edge function deletes
-- auth users after this RPC succeeds.

create or replace function public.purge_outlet(p_outlet_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_caller_role text;
  v_caller_outlet uuid;
begin
  if auth.uid() is null then
    raise exception 'Not authenticated';
  end if;

  select role, outlet_id into v_caller_role, v_caller_outlet
  from public.profiles
  where id = auth.uid();

  if v_caller_role is null
     or v_caller_role not in ('owner', 'admin')
     or v_caller_outlet is distinct from p_outlet_id then
    raise exception 'Role is not allowed';
  end if;

  -- transactions cascades to transaction_items; cashier_id references
  -- profiles with ON DELETE RESTRICT, so transactions must go first.
  delete from public.transactions where outlet_id = p_outlet_id;
  -- cashier_shifts was dropped from the schema; keep this guard so the RPC
  -- still works on databases that have not re-run 01_schema.sql yet.
  if to_regclass('public.cashier_shifts') is not null then
    delete from public.cashier_shifts where outlet_id = p_outlet_id;
  end if;
  delete from public.products where outlet_id = p_outlet_id;
  delete from public.profiles where outlet_id = p_outlet_id;
  delete from public.outlets where id = p_outlet_id;
end;
$$;

revoke all on function public.purge_outlet(uuid) from public;
grant execute on function public.purge_outlet(uuid) to authenticated;
