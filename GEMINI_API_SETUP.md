# Gemini API 설정 가이드

## 문제 상황
```
"User location is not supported for the API use."
또는
"API key not valid. Please pass a valid API key."
```

## 근본 원인
1. **Cloudflare Workers의 GEMINI_API_KEY가 유효하지 않음**
2. API 키가 설정되지 않았거나 잘못된 키가 설정됨

## 해결 방법

### 1️⃣ Google AI Studio에서 API 키 발급

1. [Google AI Studio](https://aistudio.google.com/) 접속
2. "Get API Key" 클릭
3. 새 API 키 생성
4. API 키 복사 (예: `AIzaSyABC123...`)

### 2️⃣ Cloudflare Workers에 API 키 설정

```bash
cd /Users/dj20014920/Desktop/ghostfit/workers/proxy

# Gemini API 키 설정
npx wrangler secret put GEMINI_API_KEY
# 프롬프트가 나오면 위에서 복사한 API 키 붙여넣기
```

### 3️⃣ Workers 재배포

```bash
npx wrangler deploy
```

### 4️⃣ 헬스체크로 검증

```bash
curl https://fitghost-proxy.vinny4920-081.workers.dev/health
```

정상 응답:
```json
{
  "timestamp": "2025-10-31T...",
  "status": "ok",
  "keys": {
    "gemini": true,
    "naver": true,
    "google_cse": true
  },
  "gemini_api_test": {
    "status": 200,
    "valid": true
  }
}
```

### 5️⃣ 앱에서 테스트

의류 이미지를 업로드하여 자동 태깅 테스트

## 주의사항

### ❌ 잘못된 API 키
- Cloudflare API 토큰 (`REDACTED_CLOUDFLARE_API_TOKEN`)
- R2 액세스 키 (`008dcc84e74268ed100f51d709e18dec`)
- 이들은 Gemini API 키가 **아닙니다**

### ✅ 올바른 API 키
- Google AI Studio에서 발급받은 키
- 형식: `AIzaSy...` (39자)
- 권한: Gemini API 사용 가능

## 개선 사항

### 코드 변경
1. **GeminiTagger.kt**
   - 모델 변경: `gemini-2.5-flash-lite` → `gemini-1.5-flash` (더 안정적)
   - 상세 로깅 추가 (URL, 에러 진단)
   - 명확한 에러 메시지

2. **Workers Proxy**
   - `/health` 엔드포인트 추가 (API 키 검증)
   - 로깅 개선
   - 에러 메시지 한글화

## 트러블슈팅

### 여전히 에러가 발생하면

1. **API 키 확인**
   ```bash
   npx wrangler secret list
   # GEMINI_API_KEY가 목록에 있는지 확인
   ```

2. **로그 확인**
   ```bash
   npx wrangler tail
   # 앱에서 요청 보내고 실시간 로그 확인
   ```

3. **Android 로그 확인**
   ```
   adb logcat | grep -E "GeminiTagger|WardrobeAutoComplete"
   ```

## 참고

- [Google AI Studio](https://aistudio.google.com/)
- [Gemini API 문서](https://ai.google.dev/docs)
- [Cloudflare Workers 문서](https://developers.cloudflare.com/workers/)
