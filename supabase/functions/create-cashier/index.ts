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

  const adminClient = createClient(supabaseUrl, serviceRoleKey);
  const { data: profileRow } = await adminClient
    .from("profiles")
    .select("outlet_id, role")
    .eq("id", user.id)
    .maybeSingle();

  const adminOutletId = (profileRow?.outlet_id ?? "").toString().trim();
  const role = (profileRow?.role ?? "").toString().trim().toLowerCase();
  if (!adminOutletId) return json({ error: "Outlet admin tidak ditemukan" }, 400);
  if (role !== "admin" && role !== "owner") {
    return json({ error: "Hanya admin/owner yang boleh menambah kasir" }, 403);
  }

  let action = "create";
  let email = "";
  let password = "";
  let displayName = "";
  let requestedOutletId = "";
  let allowedMenus: string[] = [];
  try {
    const body = await req.json();
    action = (body?.action ?? "create").toString().trim().toLowerCase();
    email = (body?.email ?? "").toString().trim();
    password = (body?.password ?? "").toString();
    displayName = (body?.display_name ?? "").toString().trim();
    requestedOutletId = (body?.outlet_id ?? "").toString().trim();
    allowedMenus = normalizeAllowedMenus(body?.allowed_menus);
  } catch (_) {
    return json({ error: "Payload tidak valid" }, 400);
  }

  if (action === "list") {
    const users = await listCashiers(adminClient, adminOutletId);
    return json({ success: true, users }, 200);
  }
  if (action !== "create") return json({ error: "Action tidak valid" }, 400);

  if (!email.includes("@")) return json({ error: "Email tidak valid" }, 400);
  if (!password.trim()) return json({ error: "Password wajib diisi" }, 400);

  const outletId = requestedOutletId || adminOutletId;
  if (outletId !== adminOutletId) {
    return json({ error: "Tidak boleh menambah kasir ke outlet lain" }, 403);
  }

  const fullName = displayName || email.split("@")[0] || "Kasir";
  const { data, error } = await adminClient.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: {
      display_name: fullName,
      role: "kasir",
      outlet_id: outletId,
      allowed_menus: allowedMenus,
    },
  });

  if (!error) {
    return json({ success: true, user_id: data.user?.id ?? "" }, 200);
  }

  const lowerError = (error.message ?? "").toLowerCase();
  const looksDuplicateEmail =
    lowerError.includes("already been registered") ||
    lowerError.includes("already registered") ||
    lowerError.includes("duplicate") ||
    lowerError.includes("email exists");

  if (!looksDuplicateEmail) {
    return json({ error: error.message }, 400);
  }

  // Legacy case: akun pernah dibuat via flow lama (waiting verification).
  // Re-activate akun existing agar bisa dipakai kasir tanpa verifikasi email.
  // Guard: hanya boleh reaktivasi akun yang sudah terdaftar sebagai kasir di
  // outlet yang sama — mencegah takeover akun lintas outlet / non-kasir.
  const existing = await findUserByEmail(adminClient, email);
  if (!existing) {
    return json({ error: "Email sudah terdaftar tetapi user tidak ditemukan" }, 400);
  }

  const existingRole = (existing.user_metadata?.role ?? "").toString().trim().toLowerCase();
  const existingOutletId = (existing.user_metadata?.outlet_id ?? "").toString().trim();
  if (existingRole !== "kasir" || existingOutletId !== outletId) {
    return json({ error: "Email sudah terdaftar untuk akun lain" }, 400);
  }

  const { data: updated, error: updateError } = await adminClient.auth.admin.updateUserById(existing.id, {
    password,
    email_confirm: true,
    user_metadata: {
      display_name: fullName,
      role: "kasir",
      outlet_id: outletId,
      allowed_menus: allowedMenus,
    },
  });
  if (updateError) {
    return json({ error: updateError.message }, 400);
  }

  return json({ success: true, user_id: updated.user?.id ?? existing.id }, 200);
});

function json(payload: unknown, status: number): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function normalizeAllowedMenus(raw: unknown): string[] {
  if (!Array.isArray(raw)) return [];
  const known = new Set(["transaction", "product", "best_seller", "report", "backup"]);
  const dedup = new Set<string>();
  for (const item of raw) {
    const menu = (item ?? "").toString().trim().toLowerCase();
    if (!known.has(menu)) continue;
    dedup.add(menu);
  }
  return Array.from(dedup);
}

async function listCashiers(
  adminClient: ReturnType<typeof createClient>,
  outletId: string,
): Promise<Array<{ email: string; role: string; allowed_menus: string[] }>> {
  const out: Array<{ email: string; role: string; allowed_menus: string[] }> = [];
  let page = 1;
  while (page <= 20) {
    const { data, error } = await adminClient.auth.admin.listUsers({ page, perPage: 100 });
    if (error) break;
    const users = data?.users ?? [];
    for (const user of users) {
      const role = (user.user_metadata?.role ?? "").toString().trim().toLowerCase();
      const userOutletId = (user.user_metadata?.outlet_id ?? "").toString().trim();
      const email = (user.email ?? "").toString().trim();
      if (!email || role !== "kasir" || userOutletId !== outletId) continue;
      out.push({
        email,
        role: "kasir",
        allowed_menus: normalizeAllowedMenus(user.user_metadata?.allowed_menus),
      });
    }
    if (users.length < 100) break;
    page += 1;
  }
  out.sort((a, b) => a.email.localeCompare(b.email));
  return out;
}

async function findUserByEmail(
  adminClient: ReturnType<typeof createClient>,
  email: string,
): Promise<{ id: string; user_metadata?: Record<string, unknown> | null } | null> {
  let page = 1;
  const target = email.trim().toLowerCase();
  while (page <= 10) {
    const { data, error } = await adminClient.auth.admin.listUsers({ page, perPage: 100 });
    if (error) return null;
    const users = data?.users ?? [];
    const found = users.find((u) => (u.email ?? "").toLowerCase() === target);
    if (found?.id) return { id: found.id, user_metadata: found.user_metadata };
    if (users.length < 100) break;
    page += 1;
  }
  return null;
}
