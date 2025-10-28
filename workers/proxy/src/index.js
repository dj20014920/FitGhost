export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;

    // CORS
    if (request.method === 'OPTIONS') {
      return corsResponse(env, new Response(null, { status: 204 }));
    }

    try {
      if (path === '/proxy/gemini/tag' && request.method === 'POST') {
        return await handleGeminiTag(request, env);
      }
      if (path === '/proxy/gemini/generateContent' && request.method === 'POST') {
        return await handleGeminiGenerate(request, env);
      }
      if (path === '/proxy/naver/shop' && request.method === 'GET') {
        return await handleNaverShop(url, env);
      }
      if (path === '/proxy/google/cse' && request.method === 'GET') {
        return await handleGoogleCse(url, env);
      }
      if (path === '/proxy/presign' && request.method === 'GET') {
        return await handlePresign(url, env);
      }
      return corsResponse(env, json({ code: 404, message: 'Not Found' }, 404));
    } catch (e) {
      return corsResponse(env, json({ code: 500, message: e.message || 'Internal Error' }, 500));
    }
  },
};

// --- Handlers ---

async function handleGeminiTag(request, env) {
  const provider = 'google-gemini';
  const model = 'gemini-2.5-flash-lite';
  const apiKey = env.GEMINI_API_KEY;
  if (!apiKey) return corsResponse(env, json({ code: 500, message: 'GEMINI_API_KEY not set', provider }, 500));

  const target = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;
  const body = await request.text();
  const res = await fetch(target, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-goog-api-key': apiKey,
    },
    body,
  });
  return corsResponse(env, await proxyResult(provider, res));
}

async function handleGeminiGenerate(request, env) {
  const provider = 'google-gemini';
  const model = new URL(request.url).searchParams.get('model') || 'gemini-2.5-flash';
  const apiKey = env.GEMINI_API_KEY;
  if (!apiKey) return corsResponse(env, json({ code: 500, message: 'GEMINI_API_KEY not set', provider }, 500));

  const target = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;
  const body = await request.text();
  const res = await fetch(target, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-goog-api-key': apiKey,
    },
    body,
  });
  return corsResponse(env, await proxyResult(provider, res));
}

async function handleNaverShop(url, env) {
  const provider = 'naver';
  const { NAVER_CLIENT_ID: id, NAVER_CLIENT_SECRET: secret } = env;
  if (!id || !secret) return corsResponse(json({ code: 500, message: 'Naver credentials not set', provider }, 500));
  const q = url.searchParams.get('query') || '';
  const display = url.searchParams.get('display') || '20';
  const start = url.searchParams.get('start') || '1';
  const sort = url.searchParams.get('sort') || 'sim';
  const target = `https://openapi.naver.com/v1/search/shop.json?query=${encodeURIComponent(q)}&display=${display}&start=${start}&sort=${sort}`;
  const res = await fetch(target, {
    headers: {
      'X-Naver-Client-Id': id,
      'X-Naver-Client-Secret': secret,
    },
  });
  return corsResponse(env, await proxyResult(provider, res));
}

async function handleGoogleCse(url, env) {
  const provider = 'google-cse';
  const key = env.GOOGLE_CSE_KEY;
  const cx = env.GOOGLE_CSE_CX;
  if (!key || !cx) return corsResponse(json({ code: 500, message: 'Google CSE credentials not set', provider }, 500));
  const q = url.searchParams.get('q') || '';
  const num = url.searchParams.get('num') || '10';
  const start = url.searchParams.get('start') || '1';
  const target = `https://www.googleapis.com/customsearch/v1?key=${encodeURIComponent(key)}&cx=${encodeURIComponent(cx)}&q=${encodeURIComponent(q)}&num=${encodeURIComponent(num)}&start=${encodeURIComponent(start)}`;
  const res = await fetch(target);
  return corsResponse(env, await proxyResult(provider, res));
}

async function handlePresign(url, env) {
  // For now, redirect/return CDN URL for a given key (no signing needed if CDN is public)
  const key = url.searchParams.get('key');
  if (!key) return corsResponse(env, json({ code: 400, message: 'key required' }, 400));
  const allowed = (env.ALLOWED_FILES || '*')
    .split(',')
    .map((v) => v.trim())
    .filter((v) => v.length > 0);
  if (!(allowed.length === 0 || allowed[0] === '*')) {
    const keyName = key.split('/').pop();
    const isAllowed = allowed.includes(key) || (keyName && allowed.includes(keyName));
    if (!isAllowed) {
      return corsResponse(
        env,
        json({ code: 403, message: 'Requested file not allowed', provider: 'presign' }, 403),
      );
    }
  }
  const cdn = (env.CDN_BASE || '').replace(/\/$/, '');
  if (!cdn) return corsResponse(env, json({ code: 500, message: 'CDN_BASE not set' }, 500));
  const href = `${cdn}/${key}`;
  return corsResponse(env, json({ url: href }));
}

// --- Helpers ---

async function proxyResult(provider, res) {
  if (res.ok) {
    // passthrough provider body, but normalize headers
    const text = await res.text();
    const out = new Response(text, { status: res.status });
    return out; // CORS will be applied by caller
  }
  // Map provider error → unified schema
  let message = 'Upstream error';
  try { message = (await res.text()) || message; } catch {}
  return json({ code: res.status, message, provider }, res.status);
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8' },
  });
}

function corsResponse(env, resp) {
  const allowed = env.ALLOWED_ORIGINS || '*';
  const headers = new Headers(resp.headers);
  headers.set('access-control-allow-methods', 'GET,POST,OPTIONS');
  headers.set('access-control-allow-headers', 'content-type');
  headers.set('access-control-allow-origin', allowed);
  headers.set('access-control-max-age', '86400');
  return new Response(resp.body, { status: resp.status, headers });
}
