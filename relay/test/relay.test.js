import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

let store, sent, upstreamStatus;
const env = () => ({
  GEMINI_KEY: "secret-key",
  PER_IP_DAILY: "2",
  GLOBAL_DAILY: "3",
  USAGE: {
    get: async (k) => store.get(k) ?? null,
    put: async (k, v) => void store.set(k, v),
  },
});
const ctx = { waitUntil: (p) => p };

beforeEach(() => {
  store = new Map();
  sent = [];
  upstreamStatus = 200;
  globalThis.fetch = async (url, init) => {
    sent.push({ url, init });
    return new Response('{"steps":[]}', { status: upstreamStatus });
  };
});

const call = (body, ip = "1.2.3.4") =>
  worker.fetch(
    new Request("https://relay.test/v1/interactions", {
      method: "POST",
      headers: { "cf-connecting-ip": ip },
      body: typeof body === "string" ? body : JSON.stringify(body),
    }),
    env(),
    ctx,
  );
const ok = { model: "gemini-3.5-flash-lite", input: [{ type: "user_input", content: [] }], store: true, tools: [{}] };

test("adds the key server-side and strips unsafe fields", async () => {
  const r = await call(ok);
  await new Promise((r) => setTimeout(r, 0));
  assert.equal(r.status, 200);
  assert.equal(sent[0].init.headers["x-goog-api-key"], "secret-key");
  const fwd = JSON.parse(sent[0].init.body);
  assert.equal(fwd.store, false);
  assert.equal(fwd.tools, undefined);
});

test("rejects other models and bad JSON", async () => {
  assert.equal((await call({ ...ok, model: "gemini-4-argon" })).status, 400);
  assert.equal((await call("{nope")).status, 400);
  assert.equal(sent.length, 0);
});

test("enforces per-IP and global daily limits", async () => {
  for (let i = 0; i < 2; i++) { await call(ok); await new Promise((r) => setTimeout(r, 0)); }
  assert.equal((await call(ok)).status, 429); // IP limit 2
  await call(ok, "5.6.7.8"); await new Promise((r) => setTimeout(r, 0));
  assert.equal((await call(ok, "9.9.9.9")).status, 429); // global limit 3
});

test("hides upstream auth errors", async () => {
  upstreamStatus = 403;
  assert.equal((await call(ok)).status, 502);
});
