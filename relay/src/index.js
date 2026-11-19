// IR Coach relay: the app sends Gemini Interactions requests here; this Worker checks them,
// enforces daily limits, adds the secret API key and forwards them to Google.

const GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/interactions";
const API_REVISION = "2026-05-20";
const MODELS = new Set(["gemini-4-hydrogen", "gemini-3.5-flash-lite"]);
const MAX_BYTES = 3_000_000; // one downscaled meal photo is well under 1 MB

const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json" } });
const fail = (status, message) => json({ error: { message } }, status);

async function count(env, key) {
  return Number((await env.USAGE.get(key)) || 0);
}

async function bump(env, key, current) {
  try {
    await env.USAGE.put(key, String(current + 1), { expirationTtl: 2 * 24 * 3600 });
  } catch {
    // KV allows ~1 write/sec per key; a missed increment only makes the limit slightly looser.
  }
}

export default {
  async fetch(req, env, ctx) {
    const url = new URL(req.url);
    if (url.pathname === "/health") return json({ ok: true });
    if (url.pathname !== "/v1/interactions") return fail(404, "Not found");
    if (req.method !== "POST") return fail(405, "Use POST");
    if (!env.GEMINI_KEY) return fail(500, "Relay is missing its Gemini key");

    const raw = await req.arrayBuffer();
    if (raw.byteLength > MAX_BYTES) return fail(413, "Request too large");
    let body;
    try {
      body = JSON.parse(new TextDecoder().decode(raw));
    } catch {
      return fail(400, "Invalid JSON");
    }
    if (!MODELS.has(body.model)) return fail(400, `Model not allowed: ${body.model}`);
    if (!Array.isArray(body.input) && typeof body.input !== "string") return fail(400, "Missing input");

    // Daily limits, per IP address and for the whole relay (protects the free quota).
    const day = new Date().toISOString().slice(0, 10);
    const ip = req.headers.get("cf-connecting-ip") || "unknown";
    const ipKey = `ip:${day}:${ip}`;
    const allKey = `all:${day}`;
    const [ipUsed, allUsed] = await Promise.all([count(env, ipKey), count(env, allKey)]);
    if (ipUsed >= Number(env.PER_IP_DAILY || 60)) {
      return fail(429, "Daily limit reached for this phone. It resets at midnight UTC.");
    }
    if (allUsed >= Number(env.GLOBAL_DAILY || 400)) {
      return fail(429, "The app's shared daily limit is used up. It resets at midnight UTC.");
    }
    ctx.waitUntil(Promise.all([bump(env, ipKey, ipUsed), bump(env, allKey, allUsed)]));

    // Forward only the fields the app uses; never let clients turn on server-side storage or tools.
    const clean = { model: body.model, input: body.input, store: false };
    for (const k of ["system_instruction", "response_format", "generation_config"]) {
      if (body[k] !== undefined) clean[k] = body[k];
    }

    let upstream;
    try {
      upstream = await fetch(GEMINI_URL, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "x-goog-api-key": env.GEMINI_KEY,
          "api-revision": API_REVISION,
        },
        body: JSON.stringify(clean),
      });
    } catch {
      return fail(502, "Couldn't reach Gemini");
    }
    // Don't pass Google's auth errors through verbatim; they're about the relay's key, not the user.
    if (upstream.status === 401 || upstream.status === 403) return fail(502, "The relay's Gemini key was rejected");
    return new Response(upstream.body, {
      status: upstream.status,
      headers: { "content-type": "application/json" },
    });
  },
};
