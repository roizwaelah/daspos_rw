import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!supabaseUrl || !anonKey || !serviceRoleKey) {
    return json({ error: "Missing Supabase env vars" }, 500);
  }

  const authHeader = req.headers.get("Authorization") ?? "";
  if (!authHeader.toLowerCase().startsWith("bearer ")) {
    return json({ error: "Missing bearer token" }, 401);
  }

  let requestedUserId = "";
  let purgeOutlet = false;
  let targetEmail = "";
  try {
    const body = await req.json();
    requestedUserId = (body?.user_id ?? "").toString().trim();
    purgeOutlet = Boolean(body?.purge_outlet);
    targetEmail = (body?.target_email ?? "").toString().trim();
  } catch (_) {
    requestedUserId = "";
    purgeOutlet = false;
    targetEmail = "";
  }

  const userClient = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authHeader } },
  });

  const {
    data: { user },
    error: userError,
  } = await userClient.auth.getUser();
  if (userError || !user) {
    return json({ error: "Unauthorized" }, 401);
  }

  if (requestedUserId && requestedUserId !== user.id) {
    return json({ error: "User mismatch" }, 403);
  }

  const adminClient = createClient(supabaseUrl, serviceRoleKey);

  const { data: profileRow } = await adminClient
    .from("profiles")
    .select("outlet_id, role")
    .eq("id", user.id)
    .maybeSingle();
  const outletId = (profileRow?.outlet_id ?? "").toString();
  const role = (profileRow?.role ?? "").toString().toLowerCase();

  // Admin/owner deletes another cashier account in the same outlet.
  if (targetEmail) {
    if (role !== "admin" && role !== "owner") {
      return json({ error: "Hanya admin/owner yang boleh menghapus kasir" }, 403);
    }
    if (!outletId) {
      return json({ error: "Outlet user tidak ditemukan" }, 400);
    }
    const target = await findUserByEmail(adminClient, targetEmail);
    if (!target) {
      return json({ error: "User tidak ditemukan" }, 404);
    }
    if (target.id === user.id) {
      return json({ error: "Tidak bisa menghapus akun sendiri lewat jalur ini" }, 400);
    }
    const { data: targetProfile } = await adminClient
      .from("profiles")
      .select("outlet_id, role")
      .eq("id", target.id)
      .maybeSingle();
    const targetOutletId = (targetProfile?.outlet_id ?? "").toString();
    const targetRole = (targetProfile?.role ?? "").toString().toLowerCase();
    if (targetOutletId !== outletId || targetRole !== "kasir") {
      return json({ error: "Hanya kasir pada outlet yang sama yang bisa dihapus" }, 403);
    }
    const { error: deleteTargetError } = await adminClient.auth.admin.deleteUser(target.id, false);
    if (deleteTargetError) {
      return json({ error: deleteTargetError.message }, 400);
    }
    return json({ success: true }, 200);
  }

  if (purgeOutlet) {
    if (!outletId) {
      return json({ error: "Outlet user tidak ditemukan" }, 400);
    }
    if (role !== "admin" && role !== "owner") {
      return json({ error: "Hanya admin/owner yang boleh purge outlet" }, 403);
    }
    const purgeResult = await purgeOutletAndUsers(userClient, adminClient, outletId);
    if (!purgeResult.success) {
      return json({ error: purgeResult.error ?? "Gagal purge outlet" }, 400);
    }
    return json({ success: true, purged: true }, 200);
  }

  const { error: deleteError } = await adminClient.auth.admin.deleteUser(user.id, false);
  if (deleteError) {
    return json({ error: deleteError.message }, 400);
  }

  if (outletId) {
    await cleanupOrphanOutlet(adminClient, outletId);
  }

  return json({ success: true }, 200);
});

function json(payload: unknown, status: number): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

type PurgeResult = { success: true } | { success: false; error: string };

async function purgeOutletAndUsers(
  userClient: ReturnType<typeof createClient>,
  adminClient: ReturnType<typeof createClient>,
  outletId: string,
): Promise<PurgeResult> {
  // Ambil daftar auth user dulu — RPC purge_outlet menghapus baris profiles,
  // jadi user id harus dikumpulkan sebelum purge.
  const { data: usersInOutlet, error: usersErr } = await adminClient
    .from("profiles")
    .select("id")
    .eq("outlet_id", outletId);
  if (usersErr) return { success: false, error: usersErr.message };

  const userIds = (usersInOutlet ?? [])
    .map((row: { id?: string | null }) => (row.id ?? "").toString())
    .filter((id: string) => id.length > 0);

  // purge_outlet dijalankan dengan JWT caller (bukan service role) supaya
  // auth.uid() terisi dan role check di dalam RPC tetap berlaku. Semua
  // penghapusan data outlet terjadi dalam satu transaksi DB.
  const { error: purgeErr } = await userClient.rpc("purge_outlet", {
    p_outlet_id: outletId,
  });
  if (purgeErr) return { success: false, error: purgeErr.message };

  // Auth user tidak bisa ikut transaksi RPC — hapus setelah purge sukses.
  for (const uid of userIds) {
    const { error: delUserErr } = await adminClient.auth.admin.deleteUser(uid, false);
    if (delUserErr) return { success: false, error: delUserErr.message };
  }

  return { success: true };
}

async function findUserByEmail(
  adminClient: ReturnType<typeof createClient>,
  email: string,
): Promise<{ id: string } | null> {
  let page = 1;
  const target = email.trim().toLowerCase();
  while (page <= 10) {
    const { data, error } = await adminClient.auth.admin.listUsers({ page, perPage: 100 });
    if (error) return null;
    const users = data?.users ?? [];
    const found = users.find((u) => (u.email ?? "").toLowerCase() === target);
    if (found?.id) return { id: found.id };
    if (users.length < 100) break;
    page += 1;
  }
  return null;
}

async function cleanupOrphanOutlet(adminClient: ReturnType<typeof createClient>, outletId: string) {
  const [profilesCount, productsCount, transactionsCount, shiftsCount] = await Promise.all([
    countRows(adminClient, "profiles", "outlet_id", outletId),
    countRows(adminClient, "products", "outlet_id", outletId),
    countRows(adminClient, "transactions", "outlet_id", outletId),
    countRows(adminClient, "cashier_shifts", "outlet_id", outletId),
  ]);

  // countRows mengembalikan null saat error — null !== 0 sehingga outlet
  // tidak akan dianggap kosong kalau ada query yang gagal.
  const isEmpty =
    profilesCount === 0 &&
    productsCount === 0 &&
    transactionsCount === 0 &&
    shiftsCount === 0;
  if (!isEmpty) return;

  await adminClient.from("outlets").delete().eq("id", outletId);
}

async function countRows(
  adminClient: ReturnType<typeof createClient>,
  table: string,
  column: string,
  value: string,
): Promise<number | null> {
  const { count, error } = await adminClient
    .from(table)
    .select("*", { head: true, count: "exact" })
    .eq(column, value);
  if (error) return null;
  return Number(count ?? 0);
}
