# FitGhost 프록시 서버 설정 가이드

## 📋 개요

FitGhost 앱은 Cloudflare Workers 프록시를 통해 외부 API를 호출합니다.
이를 통해 API 키를 안전하게 관리하고 앱 바이너리에 키가 노출되지 않도록 합니다.

## 🔑 필요한 API 키

### 1. GEMINI_API_KEY (필수)
- **용도**: Gemini Flash Lite (자동 태깅)
- **값**: `REDACTED_GCP_API_KEY`
- **사용처**: `/proxy/gemini/tag` 엔드포인트

### 2. NANOBANANA_API_KEY (필수)
- **용도**: Gemini Image Preview (가상 피팅)
- **값**: `REDACTED_GCP_API_KEY`
- **사용처**: `/proxy/gemini/generateContent` 엔드포인트 (이미지 모델)

### 3. NAVER_CLIENT_ID & NAVER_CLIENT_SECRET (선택)
- **용도**: 네이버 쇼핑 검색
- **사용처**: `/proxy/naver/shop` 엔드포인트

### 4. GOOGLE_CSE_KEY & GOOGLE_CSE_CX (선택)
- **용도**: 구글 커스텀 검색
- **사용처**: `/proxy/google/cse` 엔드포인트

## 🚀 설정 방법

### 방법 1: Cloudflare 대시보드 (권장)

1. **Cloudflare 대시보드 접속**
   - https://dash.cloudflare.com/081a9810680543ee912eb54ae15876a3/workers-and-pages
   - Workers & Pages 섹션으로 이동

2. **fitghost-proxy Worker 선택**
   - 배포된 Worker 목록에서 `fitghost-proxy` 클릭

3. **Settings 탭 → Variables and Secrets**
   - "Add variable" 버튼 클릭
   - Type: "Secret" 선택

4. **시크릿 추가**
   
   **GEMINI_API_KEY**
   - Variable name: `GEMINI_API_KEY`
   - Value: `REDACTED_GCP_API_KEY`
   - "Encrypt" 버튼 클릭
   
   **NANOBANANA_API_KEY**
   - Variable name: `NANOBANANA_API_KEY`
   - Value: `REDACTED_GCP_API_KEY`
   - "Encrypt" 버튼 클릭

5. **Deploy 버튼 클릭**
   - 변경사항 저장 및 배포

### 방법 2: Wrangler CLI (API 토큰 권한 필요)

```bash
cd workers/proxy

# GEMINI_API_KEY 설정
echo "REDACTED_GCP_API_KEY" | wrangler secret put GEMINI_API_KEY

# NANOBANANA_API_KEY 설정
echo "REDACTED_GCP_API_KEY" | wrangler secret put NANOBANANA_API_KEY
```

**주의**: 현재 API 토큰에 Workers Scripts:Edit 권한이 없어 실패할 수 있습니다.
이 경우 방법 1(대시보드)을 사용하세요.

### 방법 3: 로컬 개발 (.dev.vars)

로컬 개발 시에는 `.dev.vars` 파일이 자동으로 사용됩니다.

```bash
# workers/proxy/.dev.vars 파일이 이미 생성되어 있습니다
cat workers/proxy/.dev.vars
```

## ✅ 설정 확인

### 1. 프록시 서버 테스트

```bash
# Gemini Tag 엔드포인트 테스트
curl -X POST 'https://fitghost-proxy.vinny4920-081.workers.dev/proxy/gemini/tag' \
  -H "Content-Type: application/json" \
  -d '{"contents":[{"role":"user","parts":[{"text":"test"}]}]}'

# Gemini Generate 엔드포인트 테스트
curl -X POST 'https://fitghost-proxy.vinny4920-081.workers.dev/proxy/gemini/generateContent?model=gemini-2.5-flash-image-preview' \
  -H "Content-Type: application/json" \
  -d '{"contents":[{"role":"user","parts":[{"text":"test"}]}]}'
```

### 2. 예상 응답

**성공 시**:
```json
{
  "candidates": [
    {
      "content": {
        "parts": [
          {
            "text": "..."
          }
        ]
      }
    }
  ]
}
```

**실패 시 (API 키 미설정)**:
```json
{
  "code": 500,
  "message": "GEMINI_API_KEY not set",
  "provider": "google-gemini-tag"
}
```

## 🔄 프록시 서버 재배포

코드 변경 후 재배포:

```bash
cd workers/proxy
wrangler deploy
```

## 📱 앱 설정

앱의 `local.properties`에 프록시 URL이 설정되어 있는지 확인:

```properties
PROXY_BASE_URL=https://fitghost-proxy.vinny4920-081.workers.dev
```

## 🎯 API 키 사용 로직

### Gemini Tag (자동 태깅)
- 엔드포인트: `/proxy/gemini/tag`
- 모델: `gemini-2.5-flash-lite`
- API 키: `GEMINI_API_KEY`
- 용도: 옷장 아이템 자동 태깅 (JSON 스키마 강제)

### Gemini Generate (가상 피팅)
- 엔드포인트: `/proxy/gemini/generateContent?model=gemini-2.5-flash-image-preview`
- 모델: `gemini-2.5-flash-image-preview`
- API 키: `NANOBANANA_API_KEY` (fallback: `GEMINI_API_KEY`)
- 용도: 가상 피팅 이미지 생성

### 모델 선택 로직

프록시 서버는 URL 파라미터의 `model`을 확인하여 적절한 API 키를 선택합니다:

```javascript
if (model.includes('image')) {
  // 이미지 생성/편집: NANOBANANA_API_KEY 사용
  apiKey = env.NANOBANANA_API_KEY || env.GEMINI_API_KEY;
} else {
  // 텍스트 모델: GEMINI_API_KEY 사용
  apiKey = env.GEMINI_API_KEY;
}
```

## 🔒 보안 주의사항

1. **API 키 노출 방지**
   - `.dev.vars` 파일은 `.gitignore`에 포함되어 있습니다
   - 절대 Git에 커밋하지 마세요

2. **프로덕션 환경**
   - Cloudflare 대시보드의 Secrets 기능 사용
   - 환경 변수로 관리하지 마세요

3. **API 키 교체**
   - 키가 노출된 경우 즉시 Google Cloud Console에서 키 삭제
   - 새 키 생성 후 프록시 서버 시크릿 업데이트

## 📞 문제 해결

### 1. "GEMINI_API_KEY not set" 에러
- Cloudflare 대시보드에서 시크릿이 올바르게 설정되었는지 확인
- 대시보드에서 Deploy 버튼을 눌러 변경사항 적용

### 2. "Authentication error [code: 10000]"
- API 토큰 권한 부족
- Cloudflare 대시보드에서 직접 시크릿 설정 (방법 1 사용)

### 3. 프록시 서버 응답 없음
- Worker가 정상 배포되었는지 확인
- https://fitghost-proxy.vinny4920-081.workers.dev 접속 테스트

## 📚 참고 문서

- [Cloudflare Workers Secrets](https://developers.cloudflare.com/workers/configuration/secrets/)
- [Wrangler CLI](https://developers.cloudflare.com/workers/wrangler/)
- [Google Gemini API](https://ai.google.dev/docs)

---

**마지막 업데이트**: 2025-10-29
