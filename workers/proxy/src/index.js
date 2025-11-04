/**
 * Cloudflare Workers Proxy for FitGhost App
 * 
 * 이 프록시는 앱에서 외부 API 호출 시 API 키를 안전하게 관리합니다.
 * 
 * 엔드포인트:
 * - /health: 헬스체크 (API 키 검증)
 * - /proxy/gemini/tag: Gemini Flash (자동 태깅)
 * - /proxy/gemini/generateContent: Gemini Image Preview (가상 피팅)
 * - /proxy/naver/shop: 네이버 쇼핑 검색
 * - /proxy/google/cse: 구글 커스텀 검색
 * - /proxy/presign: CDN 파일 URL 생성
 */

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;

    // CORS preflight
    if (request.method === 'OPTIONS') {
      return corsResponse(env, new Response(null, { status: 204 }));
    }

    try {
      // 헬스체크 (API 키 유효성 검증)
      if (path === '/health' && request.method === 'GET') {
        return await handleHealthCheck(env);
      }
      
      // Gemini Flash (자동 태깅)
      if (path === '/proxy/gemini/tag' && request.method === 'POST') {
        return await handleGeminiTag(request, env);
      }
      
      // Gemini Image Preview (가상 피팅)
      if (path === '/proxy/gemini/generateContent' && request.method === 'POST') {
        return await handleGeminiGenerate(request, env);
      }
      
      // 네이버 쇼핑 검색
      if (path === '/proxy/naver/shop' && request.method === 'GET') {
        return await handleNaverShop(url, env);
      }
      
      // 구글 커스텀 검색
      if (path === '/proxy/google/cse' && request.method === 'GET') {
        return await handleGoogleCse(url, env);
      }
      
      // CDN 파일 URL 생성
      if (path === '/proxy/presign' && request.method === 'GET') {
        return await handlePresign(url, env);
      }
      
      return corsResponse(env, json({ code: 404, message: 'Not Found' }, 404));
    } catch (e) {
      console.error('Proxy error:', e);
      return corsResponse(env, json({ code: 500, message: e.message || 'Internal Error' }, 500));
    }
  },
};

// --- Handlers ---

/**
 * 헬스체크 및 API 키 검증
 */
async function handleHealthCheck(env) {
  const checks = {
    timestamp: new Date().toISOString(),
    status: 'ok',
    keys: {
      gemini: !!env.GEMINI_API_KEY,
      naver: !!env.NAVER_CLIENT_ID && !!env.NAVER_CLIENT_SECRET,
      google_cse: !!(env.GEMINI_API_KEY || env.GOOGLE_CSE_KEY) && !!env.GOOGLE_CSE_CX,
    }
  };
  
  // Gemini API 키 유효성 빠른 검증
  if (env.GEMINI_API_KEY) {
    try {
      // v1 API로 간단한 텍스트 생성 테스트
      const testRes = await fetch(
        'https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash-lite:generateContent',
        {
          method: 'POST',
          headers: {
            'content-type': 'application/json',
            'x-goog-api-key': env.GEMINI_API_KEY,
          },
          body: JSON.stringify({
            contents: [{ role: 'user', parts: [{ text: 'hi' }] }]
          }),
        }
      );
      
      checks.gemini_api_test = {
        status: testRes.status,
        valid: testRes.ok || testRes.status === 400 // 400도 키는 유효 (요청만 잘못됨)
      };
      
      if (!testRes.ok) {
        const errorText = await testRes.text();
        checks.gemini_api_test.error_sample = errorText.substring(0, 200);
      }
    } catch (e) {
      checks.gemini_api_test = { error: e.message };
    }
  }
  
  return corsResponse(env, json(checks, 200));
}

/**
 * Gemini 2.5 Flash-Lite 자동 태깅 핸들러
 * - 멀티모달: 텍스트, 이미지, 동영상, 오디오, PDF 지원
 * - 입력: 1,048,576 토큰 / 출력: 65,536 토큰
 * - 함수 호출, 구조화된 출력, 캐싱 지원
 */
async function handleGeminiTag(request, env) {
  const provider = 'google-gemini-tag';
  const model = 'gemini-2.5-flash-lite';
  
  const apiKey = env.GEMINI_API_KEY;
  if (!apiKey) {
    console.error('[GeminiTag] GEMINI_API_KEY not set');
    return corsResponse(env, json({ 
      code: 500, 
      message: 'GEMINI_API_KEY가 설정되지 않았습니다. wrangler secret put GEMINI_API_KEY 명령으로 설정하세요.', 
      provider 
    }, 500));
  }
  
  // v1 API 사용 (gemini-2.5-flash-lite는 v1에서 지원)
  const target = `https://generativelanguage.googleapis.com/v1/models/${model}:generateContent`;
  const body = await request.text();
  
  console.log('[GeminiTag] Request to:', target);
  console.log('[GeminiTag] Model:', model);
  console.log('[GeminiTag] API Key length:', apiKey.length);
  
  const res = await fetch(target, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-goog-api-key': apiKey,
    },
    body,
  });
  
  console.log('[GeminiTag] Response status:', res.status);
  
  if (!res.ok) {
    const errorText = await res.text();
    console.error('[GeminiTag] Error response:', errorText);
  }
  
  return corsResponse(env, await proxyResult(provider, res));
}

/**
 * Gemini Image Preview 가상 피팅 핸들러
 * 나노바나나 대체: 이미지 생성 및 편집
 * GEMINI_API_KEY 단일 키 사용 (Google Cloud 통합)
 */
async function handleGeminiGenerate(request, env) {
  const provider = 'google-gemini-generate';
  
  // URL 파라미터에서 모델 선택
  const model = new URL(request.url).searchParams.get('model') || 'gemini-2.5-flash';
  
  // GEMINI_API_KEY 단일 키 사용 (모든 Gemini 모델 통합)
  const apiKey = env.GEMINI_API_KEY;
  
  if (!apiKey) {
    console.error('[GeminiGenerate] GEMINI_API_KEY not set');
    return corsResponse(env, json({ 
      code: 500, 
      message: 'GEMINI_API_KEY가 설정되지 않았습니다. wrangler secret put GEMINI_API_KEY 명령으로 설정하세요.', 
      provider 
    }, 500));
  }
  
  const target = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;
  const body = await request.text();
  
  console.log('[GeminiGenerate] Request to:', target);
  console.log('[GeminiGenerate] Model:', model);
  console.log('[GeminiGenerate] API Key length:', apiKey.length);
  
  const res = await fetch(target, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-goog-api-key': apiKey,
    },
    body,
  });
  
  console.log('[GeminiGenerate] Response status:', res.status);
  
  if (!res.ok) {
    const errorText = await res.text();
    console.error('[GeminiGenerate] Error response:', errorText);
  }
  
  return corsResponse(env, await proxyResult(provider, res));
}

/**
 * 네이버 쇼핑 검색 핸들러
 */
async function handleNaverShop(url, env) {
  const provider = 'naver';
  const { NAVER_CLIENT_ID: id, NAVER_CLIENT_SECRET: secret } = env;
  
  if (!id || !secret) {
    return corsResponse(env, json({ 
      code: 500, 
      message: 'Naver credentials not set', 
      provider 
    }, 500));
  }
  
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

/**
 * 구글 커스텀 검색 핸들러
 * GEMINI_API_KEY를 사용 (Google Cloud 통합 키)
 */
async function handleGoogleCse(url, env) {
  const provider = 'google-cse';
  
  // GEMINI_API_KEY를 우선 사용 (Google Cloud 통합)
  const key = env.GEMINI_API_KEY || env.GOOGLE_CSE_KEY;
  const cx = env.GOOGLE_CSE_CX;
  
  if (!key) {
    return corsResponse(env, json({ 
      code: 500, 
      message: 'GEMINI_API_KEY not set. Google CSE requires Google Cloud API key.', 
      provider 
    }, 500));
  }
  
  if (!cx) {
    return corsResponse(env, json({ 
      code: 500, 
      message: 'GOOGLE_CSE_CX (Search Engine ID) not set', 
      provider 
    }, 500));
  }
  
  const q = url.searchParams.get('q') || '';
  const num = url.searchParams.get('num') || '10';
  const start = url.searchParams.get('start') || '1';
  
  const target = `https://www.googleapis.com/customsearch/v1?key=${encodeURIComponent(key)}&cx=${encodeURIComponent(cx)}&q=${encodeURIComponent(q)}&num=${encodeURIComponent(num)}&start=${encodeURIComponent(start)}`;
  
  console.log('[GoogleCSE] Request to:', target.replace(key, 'REDACTED'));
  console.log('[GoogleCSE] Search Engine ID:', cx);
  
  const res = await fetch(target);
  
  console.log('[GoogleCSE] Response status:', res.status);
  
  if (!res.ok) {
    const errorText = await res.text();
    console.error('[GoogleCSE] Error response:', errorText);
  }
  
  return corsResponse(env, await proxyResult(provider, res));
}

/**
 * CDN 파일 URL 생성 핸들러
 */
async function handlePresign(url, env) {
  const key = url.searchParams.get('key');
  
  if (!key) {
    return corsResponse(env, json({ code: 400, message: 'key required' }, 400));
  }
  
  // 허용된 파일 체크
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
        json({ code: 403, message: 'Requested file not allowed', provider: 'presign' }, 403)
      );
    }
  }
  
  const cdn = (env.CDN_BASE || '').replace(/\/$/, '');
  if (!cdn) {
    return corsResponse(env, json({ code: 500, message: 'CDN_BASE not set' }, 500));
  }
  
  const href = `${cdn}/${key}`;
  return corsResponse(env, json({ url: href }));
}

// --- Helpers ---

/**
 * 프록시 결과 처리
 */
async function proxyResult(provider, res) {
  if (res.ok) {
    // 성공 응답: 원본 응답 그대로 전달
    const text = await res.text();
    return new Response(text, { status: res.status });
  }
  
  // 에러 응답: 통합 스키마로 변환
  let message = 'Upstream error';
  try {
    message = (await res.text()) || message;
  } catch {}
  
  return json({ code: res.status, message, provider }, res.status);
}

/**
 * JSON 응답 생성
 */
function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8' },
  });
}

/**
 * CORS 헤더 추가
 */
function corsResponse(env, resp) {
  const allowed = env.ALLOWED_ORIGINS || '*';
  const headers = new Headers(resp.headers);
  
  headers.set('access-control-allow-methods', 'GET,POST,OPTIONS');
  headers.set('access-control-allow-headers', 'content-type');
  headers.set('access-control-allow-origin', allowed);
  headers.set('access-control-max-age', '86400');
  
  return new Response(resp.body, { status: resp.status, headers });
}
