/**
 * Cloudflare Workers Proxy for TryOn App - Vertex AI Edition
 *
 * Vertex AI로 전환하여 지역 제한 문제 해결
 * OAuth 2.0 Service Account 인증 사용
 */

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === 'OPTIONS') {
      return corsResponse(env, new Response(null, { status: 204 }));
    }

    try {
      if (path === '/health' && request.method === 'GET') {
        return await handleHealthCheck(env);
      }
      
      if (path === '/proxy/gemini/tag' && request.method === 'POST') {
        return await handleVertexGemini(request, env, 'gemini-2.5-flash-lite');
      }
      
      if (path === '/proxy/gemini/generateContent' && request.method === 'POST') {
        const model = url.searchParams.get('model') || 'gemini-2.5-flash-lite';
        return await handleVertexGemini(request, env, model);
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
      console.error('Proxy error:', e);
      return corsResponse(env, json({ code: 500, message: e.message }, 500));
    }
  },
};

async function handleHealthCheck(env) {
  const checks = {
    timestamp: new Date().toISOString(),
    status: 'ok',
    api_type: 'vertex-ai'
  };
  
  if (env.VERTEX_AI_SERVICE_ACCOUNT_KEY) {
    try {
      const sa = JSON.parse(env.VERTEX_AI_SERVICE_ACCOUNT_KEY);
      const token = await getVertexAIToken(env);
      checks.vertex = {
        status: token ? 'ok' : 'failed',
        project: sa.project_id,
        location: env.VERTEX_AI_LOCATION || 'us-central1'
      };
    } catch (e) {
      checks.vertex = { status: 'error', message: e.message };
    }
  }
  
  return corsResponse(env, json(checks, 200));
}

async function handleVertexGemini(request, env, model) {
  if (!env.VERTEX_AI_SERVICE_ACCOUNT_KEY) {
    return corsResponse(env, json({ code: 500, message: 'Vertex AI not configured' }, 500));
  }
  
  try {
    const token = await getVertexAIToken(env);
    if (!token) throw new Error('Token generation failed');
    
    const sa = JSON.parse(env.VERTEX_AI_SERVICE_ACCOUNT_KEY);
    const location = env.VERTEX_AI_LOCATION || 'us-central1';
    const endpoint = `https://${location}-aiplatform.googleapis.com/v1/projects/${sa.project_id}/locations/${location}/publishers/google/models/${model}:generateContent`;
    
    const body = await request.text();
    
    const res = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body
    });
    
    const responseText = await res.text();
    return corsResponse(env, new Response(responseText, {
      status: res.status,
      headers: { 'Content-Type': 'application/json' }
    }));
  } catch (e) {
    return corsResponse(env, json({ code: 500, message: e.message }, 500));
  }
}

async function getVertexAIToken(env) {
  try {
    const sa = JSON.parse(env.VERTEX_AI_SERVICE_ACCOUNT_KEY);
    const now = Math.floor(Date.now() / 1000);
    
    const header = { alg: 'RS256', typ: 'JWT' };
    const payload = {
      iss: sa.client_email,
      scope: 'https://www.googleapis.com/auth/cloud-platform',
      aud: 'https://oauth2.googleapis.com/token',
      iat: now,
      exp: now + 3600
    };
    
    const jwt = await signJWT(header, payload, sa.private_key);
    
    const tokenRes = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        assertion: jwt
      }).toString()
    });
    
    if (!tokenRes.ok) return null;
    const data = await tokenRes.json();
    return data.access_token;
  } catch (e) {
    console.error('Token error:', e);
    return null;
  }
}

async function signJWT(header, payload, privateKeyPem) {
  const base64url = (obj) => {
    const json = JSON.stringify(obj);
    const base64 = btoa(unescape(encodeURIComponent(json)));
    return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
  };
  
  const unsigned = `${base64url(header)}.${base64url(payload)}`;
  
  const pem = privateKeyPem
    .replace(/-----BEGIN PRIVATE KEY-----/, '')
    .replace(/-----END PRIVATE KEY-----/, '')
    .replace(/\s/g, '');
  
  const der = Uint8Array.from(atob(pem), c => c.charCodeAt(0));
  
  const key = await crypto.subtle.importKey(
    'pkcs8',
    der,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign']
  );
  
  const sig = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    key,
    new TextEncoder().encode(unsigned)
  );
  
  const sigBytes = new Uint8Array(sig);
  const sigBase64 = btoa(String.fromCharCode(...sigBytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '');
  
  return `${unsigned}.${sigBase64}`;
}

async function handleNaverShop(url, env) {
  const { NAVER_CLIENT_ID: id, NAVER_CLIENT_SECRET: secret } = env;
  if (!id || !secret) {
    return corsResponse(env, json({ code: 500, message: 'Naver not configured' }, 500));
  }
  
  const q = url.searchParams.get('query') || '';
  const display = url.searchParams.get('display') || '20';
  const start = url.searchParams.get('start') || '1';
  const sort = url.searchParams.get('sort') || 'sim';
  
  const target = `https://openapi.naver.com/v1/search/shop.json?query=${encodeURIComponent(q)}&display=${display}&start=${start}&sort=${sort}`;
  
  const res = await fetch(target, {
    headers: {
      'X-Naver-Client-Id': id,
      'X-Naver-Client-Secret': secret
    }
  });
  
  return corsResponse(env, await proxyResult(res));
}

async function handleGoogleCse(url, env) {
  const key = env.GOOGLE_CSE_KEY;
  const cx = env.GOOGLE_CSE_CX;
  
  if (!key || !cx) {
    return corsResponse(env, json({ code: 500, message: 'Google CSE not configured' }, 500));
  }
  
  const q = url.searchParams.get('q') || '';
  const num = url.searchParams.get('num') || '10';
  const start = url.searchParams.get('start') || '1';
  
  const target = `https://www.googleapis.com/customsearch/v1?key=${encodeURIComponent(key)}&cx=${encodeURIComponent(cx)}&q=${encodeURIComponent(q)}&num=${num}&start=${start}`;
  
  const res = await fetch(target);
  return corsResponse(env, await proxyResult(res));
}

async function handlePresign(url, env) {
  const key = url.searchParams.get('key');
  if (!key) {
    return corsResponse(env, json({ code: 400, message: 'key required' }, 400));
  }
  
  const cdn = (env.CDN_BASE || '').replace(/\/$/, '');
  if (!cdn) {
    return corsResponse(env, json({ code: 500, message: 'CDN_BASE not set' }, 500));
  }
  
  return corsResponse(env, json({ url: `${cdn}/${key}` }));
}

async function proxyResult(res) {
  const text = await res.text();
  return new Response(text, { status: res.status });
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { 'content-type': 'application/json' }
  });
}

function corsResponse(env, resp) {
  const headers = new Headers(resp.headers);
  headers.set('access-control-allow-methods', 'GET,POST,OPTIONS');
  headers.set('access-control-allow-headers', 'content-type,authorization');
  headers.set('access-control-allow-origin', env.ALLOWED_ORIGINS || '*');
  headers.set('access-control-max-age', '86400');
  return new Response(resp.body, { status: resp.status, headers });
}
