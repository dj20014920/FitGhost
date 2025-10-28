FitGhost — Android MVP 통합 PRD (Unified, Cloud-only)

역할/목표
•	역할: Android 시니어 개발자 에이전트
•	목표: 본 PRD를 단일 소스 오브 트루스로 삼아 Kotlin + Jetpack Compose MVP를 즉시 빌드 가능한 전체 소스와 문서/테스트/QA 체크리스트까지 납품

⸻
	0.	참고
•	본 PRD에 명시된 기능·비기능 요구, 화면/플로우, 데이터 모델, AI 파이프라인, QA를 유연하게 참고할 것

⸻
	1.	핵심 사용자 플로우

[1단계: 최초 설정 — 내 옷장 채우기]
1.	앱 시작 → 내 옷장 탭
2.	+ 버튼으로 아이템 추가
3.	(A) 직접 촬영, (B) 웹 모델 착샷 가져오기
4.	자동 태깅 결과 확인(카테고리/속성) → 필요 시 수정/저장

[2단계: 매일 사용 — 아침 코디 추천]
1.	홈에서 오늘 날씨 + AI 추천 코디 TOP 3
2.	카드 선택 → 가상 피팅 버튼으로 내 사진에 오버레이

[3단계: 탐색/구매]
1.	상점 탭에서 추천/검색
2.	찜/장바구니/구매
3.	몰별 순차 결제(Chrome Custom Tabs)

⸻
	2.	산출물(Deliverables)
	1.	Android 앱 전체 소스(Gradle KTS/리소스)
	2.	README.md (빌드/실행/테스트/광고 테스트 ID/UMP)
	3.	모듈/패키지 구조 문서(간단 아키/DI/교체 포인트)
	4.	테스트: 유닛(크레딧/추천 로직), 간단 UI(그리드 칼럼 적응)
	5.	QA 체크리스트(본 PRD 시나리오 체크박스)
	6.	샘플 데이터(JSON: 옷장/상점)
	7.	라이선스/주석(KDoc)
	8.	클라우드 태깅/추천 JSON 스키마 및 검증 규칙
	9.	성능 로그 리포트(지연/성공률, PII 미수집)
	10.	AI 경로: Cloud-only(Gemini 2.5 Flash Lite → JSON 강제)
  11.	서버리스 우선(로컬 저장). 외부 API: Open-Meteo(키 불요), Naver Shopping, Google Programmable Search(JSON API) — 모든 키 기반 API는 Cloudflare Workers 프록시를 통해 호출(앱 내 키 저장/전달 금지)
	12.	광고: 개발 중 테스트 유닗 ID (README에 실제 키 교체 위치)
	13.	보안: API 키/시크릿 클라 포함 금지, PII/HW 식별자 수집 금지
	14.	성능 목표(Cloud-only): 태깅 응답 600–1500ms, 추천 400–1200ms, 성공률 ≥ 98%, 재시도 ≤ 2회

⸻
	3.	구현 범위(필수)

A. Try-On(가상 피팅, 클라우드 API — NanoBanana/Gemini)
•	CompositeTryOnEngine: NanoBananaTryOnEngine 우선(Generative Language API), 필요 시 CloudTryOnEngine
•	인증: 앱은 Cloudflare Workers 프록시 엔드포인트를 호출하며, 실제 공급자 API 키/시크릿은 Worker Secrets로 보관/주입(앱 내 키 없음)
•	저장 경로: getExternalFilesDir(Pictures)/tryon/*.png (FileProvider 공유)
•	교체점: TryOnEngine 인터페이스 유지
•	상태: 구현 완료(FittingScreen, CompositeTryOnEngine, GeminiApiHelper)

B. 크레딧/광고
•	주 10회 무료 + 리워드 광고 시 +1
•	DataStore(keys: week, used, bonus)
•	NO_CREDIT → 광고 유도 UI

C. 내 옷장(로컬 DB)
•	Room 엔티티 Garment(id, type[T/B/O], color, pattern?, fabric?, warmth1~5, waterResist, imageUri, tags[])
•	CRUD + 필터(타입/색/태그) + 간단 색상 추출(옵션)

D. 날씨 기반 추천
•	Open-Meteo로 기온/강수/풍속
•	OutfitRecommender: 기온-보온, 강수-방수, 풍속-겉옷, 태그 호환 → TOP3 카드

E. 상품 추천·구매(딥링크)
•	Shop: 검색 → 카드(찜/장바구니/구매)
•	Cart: 몰별 그룹 + 순차 결제(Custom Tabs)

F. 갤러리 & 폴더블
•	Try-On 결과 그리드(GridCells.Adaptive)
•	폴더블에서 칼럼 자동 증가

G. 자동 태깅(Auto-Tagging, Cloud-only)
•	기본: Cloud models/gemini-2.5-flash-lite → JSON 강제(스키마 검증/재시도) → Wardrobe 자동채움


⸻
	4.	화면 & 내비게이션
•	Home: 오늘 날씨 + 코디 TOP3 + 퀵 버튼
•	Try-On: 포토 피커 → 상/하 프리뷰 → 저장/공유 → 크레딧/광고
•	Wardrobe: 리스트/필터 → 추가/편집(자동 태깅 결과 편집)
•	Recommendations: 옷장 세트 추천(JSON) + 외부 검색 카드
•	Shop: 검색 + 카드
•	Cart: 몰별 그룹 + 순차 결제
•	Gallery: 결과 그리드(확대/공유)

⸻
	5.	디렉터리/아키텍처(샘플)

app/
├─ App.kt (MobileAds.init, UMP 부팅)
├─ ads/RewardedAdController.kt
├─ data/
│   ├─ db/ (AppDb, Dao, Converters)
│   ├─ model/ (Garment, CartItem, WeatherSnapshot)
│   ├─ CreditStore.kt
│   ├─ LocalImageStore.kt
│   ├─ TryOnRepository.kt
│   ├─ search/ (NaverApi, GoogleCseApi — baseUrl=Cloudflare 프록시, SearchRepo)
│   └─ weather/ (OpenMeteoApi, WeatherRepo)
├─ domain/OutfitRecommender.kt
├─ domain/RecommendationService.kt
├─ engine/ (TryOnEngine.kt, FakeTryOnEngine.kt, CloudTryOnEngine.kt, NanoBananaTryOnEngine.kt, CompositeTryOnEngine.kt)
├─ ai/
│   └─ cloud/ (GeminiTagger.kt)
├─ ui/ (Home, Try-On, Wardrobe, Recommendations, Shop, Cart, Gallery, components/)
└─ util/ (Browser.kt, Format.kt)

⸻
	6.	Definition of Done (DoD)
•	클린 체크아웃 → 즉시 빌드/실행
•	핵심 플로우 전부 통과:
– Try-On 생성/저장/갤러리
– 주 10회 소진/광고 보너스
– Wardrobe CRUD/필터
– Open-Meteo → 홈 TOP3
– Recommendations→Shop→Cart→몰별 순차 결제
– 폴더블 그리드 증분
– 자동 태깅: Cloud JSON 스키마 준수(검증/재시도)
– 모든 키 기반 외부 호출은 Cloudflare Workers 프록시 경유(앱에서 공급자 도메인 직호출 차단)
– 레포/앱 바이너리에 API 키/시크릿/토큰이 포함되지 않음(BuildConfig/리소스에 없음)
•	접근성/UX: TalkBack, 로딩/에러, 최소 터칃 타깃
•	주석/문서: KDoc, README 완결
•	테스트:
– 유닛: CreditStore, OutfitRecommender, RecommendationService, JsonSchemaValidator, AutoTagger 라우팅
– 간단 UI: 갤러리 칼럼 변화, Wardrobe 필터

⸻
	7.	구현 순서(마일스톤)
	1.	부트스트랩(Compose/Room/DataStore/Ads/UMP)
	2.	Try-On 엔진 + 저장/갤러리
	3.	크레딧/광고 연결
	4.	Wardrobe CRUD(+ 색상 추출)
	5.	Open-Meteo + 추천 로직 + 홈 TOP3
	6.	Cloud Auto-Tagging(Gemini 2.5 Flash Lite → JSON 검증/재시도)
	7.	RecommendationService + Naver/Google 검색 병렬 통합 + UI(Recommendations)
	8.	Shop/Recommendations 카드 → Cart 확장(source/deeplink) + 순차 결제
	9.	폴더블/접근성/QA 통과 → README/테스트 정리

⸻
	8.	유의 사항
•	광고는 테스트 유닛 ID(실배포 전 교체)
•	외부 결제는 딥링크/Custom Tabs (통합결제 금지)
•	이미지: Cloud 태깅 시 사용자 옵트인 업로드, 기타 데이터는 로컬 우선
•	Search API/AI 엔진 연동 대비 인터페이스로 느슨 결합
•	온디바이스 모델/자산 없음(Cloud-only); 응답 JSON은 로컬 캐시

⸻
	9.	통합 한 줄 요약
•	Primary: Cloud 태깅/추천 — `models/gemini-2.5-flash-lite` (JSON 강제)
•	Cloud-only: 온디바이스 경로 폐기, 폴백 없음
•	정책: 실패 시 재시도 후 사용자 편집/재시도 안내

⸻
	10.	클라우드 태깅/추천 전환 (JSON 강제, Gemini 2.5 Flash Lite)

10.1 자동 태깅(Cloud, 기본 경로)
- 모델: `models/gemini-2.5-flash-lite` (Google)
- 목적: 이미지 의류 자동 태깅을 JSON 스키마로 강제 출력
- 입력: `image_uri` 또는 `image_base64` (≤ 2MB, JPEG/PNG)
- 출력(JSON, 고정 스키마)
```
{
  "image_id": "uuid",
  "category_top": "상의|하의|아우터|기타",
  "category_sub": "티셔츠|셔츠|청바지|스커트|…",
  "attributes": {
    "color_primary": "red|white|black|…",
    "color_secondary": "white|null",
    "pattern_basic": "solid|stripe|check|dot|graphic|null",
    "fabric_basic": "cotton|denim|leather|knit|silk|null"
  },
  "confidence": { "top": 0.93, "sub": 0.86 }
}
```
- 프롬프트 정책: "JSON만", "설명 텍스트 금지", "스키마 미준수 시 재생성"
- 검증: JSON Schema Validator/Strict parser → 실패 시 최대 2회 재시도, 폴백 없음; 사용자 편집/재시도 안내
- 개인정보: 원본 이미지 외부 전송 시 사용자 동의(옵트인); 메타데이터만 저장
 - 호출: 앱→Cloudflare Workers 프록시(예: `https://<your-worker-domain>/proxy/gemini/tag`)로 POST. Worker 내부에서 Google Generative Language API 호출 및 키 주입.

10.2 옷장 추천(Cloud → JSON 세트 강제)
- 입력: 사용자가 등록한 아이템의 카테고리/속성(예: 상의: 화이트 티셔츠)
- 과업: 등록 아이템과 어울리는 세트 아이템(최대 4개: 하의/신발/아우터/액세서리 등 자유 추론) 추천
- 출력(JSON, 최소 필수 형태)
```
{
  "input_item": { "category": "티셔츠", "attributes": { "color": "white" } },
  "recommendations": [
    { "category": "스니커즈", "search_keywords": "화이트 스니커즈" },
    { "category": "데님 팬츠", "search_keywords": "라이트 블루 데님" }
  ]
}
```
- 확장 필드(옵션): "rationale_ko": "추천 사유", "style_hint": "미니멀|스트릿|…"
- 프롬프트: "배열 길이 1–4", "중복 카테고리 최소화", "검색 키워드 한국어 우선"

10.3 외부 검색 연동(병렬, Naver/Google — Cloudflare 프록시 경유)
- 앱 호출(프록시): `GET https://<your-worker-domain>/proxy/naver/shop?query={검색어}&display=20&start=1&sort=sim`
  - Worker 동작: 내부에서 `https://openapi.naver.com/v1/search/shop.json` 호출, `X-Naver-Client-Id`, `X-Naver-Client-Secret`를 Worker Secrets에서 주입.
- 앱 호출(프록시): `GET https://<your-worker-domain>/proxy/google/cse?q={검색어}&num=10`
  - Worker 동작: 내부에서 `https://www.googleapis.com/customsearch/v1` 호출, `{key}`, `{cx}`는 Worker Secrets/Env에서 주입. 전용 CSE 구성(쇼핑 도메인 중심), 필요 시 `siteSearch=shopping.google.com` 적용.
- 병렬 처리: `CoroutineScope.async` 2개의 호출 → 통합 결과
- 통합 스키마: `{title, image, price, currency, seller, url, source}` 정규화
- 랭킹/중복제거: 제목+가격+도메인 해시 → 유사 항목 병합; 품질(가격 범위/판매자 평판/배송) 가중치
- 오류 처리: 타임아웃/429 → 재시도 지수백오프, 한쪽 실패 시 다른쪽 결과만 표시

10.4 추천 화면 → 장바구니 → 결제
- 추천 화면: 각 추천 세트 아이템에 외부 결과 카드 리스트 노출(찜/장바구니)
- 장바구니: `CartItem`에 `source(nav er|google)`와 `deeplink(url)` 필드 추가, 몰별 그룹화
- 결제: 기존 흐름과 동일(Chrome Custom Tabs, 순차 결제), 결제 완료 콜백 핸들러 유지

10.5 Definition of Done(추가)
- 클라우드 자동태깅: 유효 JSON(스키마 준수) 1–2회 재시도 내 수신
- 옷장 추천: 등록 아이템당 1–4개 세트 JSON 생성, 최소 한 개는 신발/하의 포함
- 검색: Naver/Google 병렬 호출, 통합 결과 6개 이상 표시, 카드→장바구니→결제까지 무결히 연결
- 로깅: 태깅/추천/검색 각각 지연(ms)/성공률(%) 수집, PII 미수집

10.6 아키텍처/코드 삽입 포인트
- `app/domain/`:
  - `RecommendationService`: 클라우드 호출→JSON 파싱→검색키워드 추출
- `app/data/search/`:
  - `NaverApi`, `GoogleCseApi`: Retrofit 인터페이스 + DTO
- `app/ui/Shop/RecommendationScreen`: JSON·검색 결과 바인딩, 장바구니 연결
- 설정: API 키/시크릿은 레포/앱에 저장 금지. `gradle.properties`에는 프록시 BASE_URL 등 공개 설정만 포함(예: `PROXY_BASE_URL=https://<your-worker-domain>`). 공급자 키는 Cloudflare Worker Secrets/KV/Environment에 저장·주입.

10.7 운영/보안
- 키/시크릿은 로컬에 저장 금지, Cloudflare Workers Secrets/KV/Env로 관리(wrangler secret).
- 앱은 공급자 도메인(googleapis/naver 등)으로 직접 호출 금지. 모든 키 기반 트래픽은 Cloudflare Workers 프록시 경유.
- CORS: 프록시에서 앱의 패키지/도메인만 허용(Origin Allowlist) 및 인증 없는 공개 엔드포인트 최소화.
- 레이트 리밋/봇 방지: Cloudflare Rate Limiting/Ruleset 적용, 필요 시 Turnstile/Device 신호 사용.
- 로깅: 프록시에서 PII 없이 지연/성공률 수집. 요청/응답 JSON은 디바이스 로컬에만 캐시(옵트인 외 전송 금지)

10.8 Cloudflare 프록시(Workers) 설계
- 라우트(예시)
  - `GET /proxy/naver/shop` → Naver Shopping Search 위임
  - `GET /proxy/google/cse` → Google Programmable Search 위임
  - `POST /proxy/gemini/tag` → Gemini 2.5 Flash Lite 태깅 위임(이미지 base64/URL 입력)
  - `POST /proxy/tryon/composite` → Try-On 합성 엔진 위임(`engine=nano|gemini` 등 쿼리)
  - (옵션) `GET /proxy/weather` → Open-Meteo 캐싱 프록시(키 불요지만 CDN 캐시/리라이팅 목적)
- 보안
  - 공급자 자격증명은 Worker Secrets/KV에 저장. 앱에는 절대 배포하지 않음.
  - Origin Allowlist/CORS, Rate Limiting, (옵션) 서명된 타임스탬프 헤더/HMAC 검증.
- 캐시/성능
  - 검색/날씨 응답은 `Cache-Control` 기반 단기 캐싱(예: 60–300s). 태깅/가상 피팅은 no-store.
  - 에러 매핑: 공급자 4xx/5xx → 프록시 표준 에러 스키마 `{code, message, provider?}`로 변환.
- 관측성
  - Cloudflare Analytics/Logs로 지연 p50/p95 및 성공률 집계. 개인식별정보(PII) 저장 금지.


⸻
11. 온디바이스 날씨 기반 추천(옵션 트랙) — LFM2-1.2B(GGUF)

11.1 목표/개요
- 목적: 네트워크 불안/비용 절감을 위해 디바이스 내에서 “오늘 날씨 기반 코디 TOP3”를 생성.
- 모델: LiquidAI/LFM2-1.2B-GGUF(Q4_0 권장, ~696MB). 32k 컨텍스트, 경량/고속.
- 러너: llama.cpp(로컬) 또는 node-llama-cpp. JSON Schema→Grammar(GBNF)로 출력 완전 강제.
- 현 상태: Cloud-only가 기본. 본 섹션은 향후 확장(Feature Flag로 분기) 스펙.

11.2 플로우(End-to-End)
1) 날씨 스냅샷 생성(Open-Meteo)
   - 입력: 금일/하루 전체 예보(평균/최저/최고 체감, 강수확률/량, 바람, 습도, UV 등)
   - WeatherSnapshot 모델에 저장: {date, temp_avg, temp_min, temp_max, temp_feels, rain_prob, wind, uv, humidity}
2) 1차 규칙 필터(로컬 코드)
   - 기온/강수/바람/UV 기반 하드 룰로 후보 축소(카테고리/소재/두께/기능).
   - 예시(요지):
     - 체감 ≥28℃: 반팔/민소매/얇은 하의, 통기성 소재(린넨/코튼), 샌들/로퍼, 선글라스/모자.
     - 18–27℃: 반팔/얇은 긴팔/셔츠, 슬랙스/청바지, 경량 아우터(아침/저녁).
     - 10–17℃: 긴팔+경량 아우터(바람막이/가디건), 긴바지, 양말.
     - ≤9℃: 니트/후디+코트/패딩, 방풍·보온, 워커/부츠, 이너/머플러.
     - 비/눈: 방수 아우터/신발 우선, 우산 고려.
3) 후보 스코어링/Top-K 압축
   - weather_fit(보온/방수/방풍/UV) + 신선도(최근 착용 페널티) + 컬러/실루엣 호환 규칙 가점.
   - 카테고리별 상위 K 유지(예: 상의 20, 하의 20, 아우터 10, 신발 10, 액세서리 20).
   - 입력 토큰 절약을 위해 각 아이템은 {id, cat, color, fabric?, warmth, flags[]}의 1줄 요약으로 변환.
4) 조합 생성(규칙 프루닝)
   - (상의×하의×(아우터?)×신발×(액세서리≤2)) 생성.
   - 금지 룰(예: 반팔+두꺼운 패딩 금지, 포멀+러닝화 금지 등)로 pruning.
   - 상위 M 세트(예: 60–120)만 LLM 입력.
5) LFM2 재랭크/요약(JSON 강제)
   - Prompt에 날씨 요약+후보 세트 목록+출력 스키마를 제공.
   - Grammar(GBNF) 적용으로 JSON-only 보장, temperature=0.2, top_p=0.9 권장.
   - 출력: 코디 TOP3 + 간단 사유.
6) UI 반영/캐시
   - 홈 TOP3 카드 바인딩, 공유/피팅 버튼 연계.
   - 워드로브/날씨 키로 결과 캐시(24h 로테이션), 재생성 버튼 제공.

11.3 출력 JSON 스키마(요약)
```
{
  "date": "YYYY-MM-DD",
  "weather": {
    "temp_feels": number, "rain_prob": number, "wind": number, "uv": number
  },
  "recommendations": [
    {
      "outfit_id": string,
      "items": {
        "top_id": string,
        "bottom_id": string,
        "outer_id": string|null,
        "shoes_id": string,
        "accessory_ids": string[]
      },
      "rationale_ko": string
    }
  ]
}
```
- 길이: recommendations는 정확히 3개.
- ID는 로컬 DB( Garment.id )만 참조해야 함.

11.4 llama.cpp 연동
- 배포: 모델 파일(.gguf)을 앱 자산으로 포함하지 않고 최초 실행 시 다운로드(사용자 동의) 또는 사전 번들(파일 크기 영향 고려).
- 실행 옵션(예시):
  - 서버: `llama-server -m lfm2-1.2b-q4_0.gguf -c 32768 --port 11434 --grammar-file outfit.gbnf`
  - CLI: `llama-cli -m lfm2-1.2b-q4_0.gguf --grammar-file outfit.gbnf -p "..."`
- Grammar: JSON Schema→GBNF 변환 스크립트로 outfit.gbnf 생성(레포 내 scripts/ 포함).
- 모바일: node-llama-cpp(로컬 브릿지) 또는 네이티브 바인딩 활용. 백그라운드 스레드/코루틴에서 호출.

11.5 프롬프트(요지)
- system:
  - “너는 스타일리스트. JSON 형식만 반환. 설명 텍스트 금지. 스키마 필드를 빠뜨리지 말 것.”
- user 컨텍스트:
  - 날씨 요약(JSON 한 줄)
  - 후보 세트 목록(최대 M개, 각 세트는 간단 id 묶음)
  - 제약: 카테고리 중복 최소화, 날씨 적합 우선, 컬러 충돌 회피, 활동성 고려(비/바람/UV).
- assistant: 빈 응답(모델 생성).

11.6 성능/메모리/토큰 예산
- 입력 길이: 날씨(≤100토큰) + 세트 M×요약(세트당 40~80토큰) → 6k~10k 토큰 내 목표.
- 지연 목표: 저사양 CPU 1.5~3.0s, Apple Silicon 0.6~1.2s(설정/디바이스에 따라 상이).
- 설정: temperature 0.2, top_p 0.9, repeat_penalty 1.05, max_output_tokens ≤ 800.

11.7 에러/리트라이/폴백
- JSON 파싱 실패: 1차 재시도(프롬프트에 “스키마 위반” 원인 피드백), 2회 실패 시 규칙 기반 추천 TOP3로 폴백.
- 아이템 누락/ID 미존재: 유효성 검사 후 해당 세트 제외, 다음 후보 채움.
- 시간제한: 전체 4초 초과 시 이전 캐시 노출 + 백그라운드 갱신.

11.8 보안/프라이버시
- 모델은 디바이스 내에서 로컬 추론. 이미지/PII 외부 전송 없음.
- 모델 파일 무결성 해시 검증, 저장 시 암호화 스토리지 사용 권장.

11.9 코드 삽입 포인트
- app/domain/OutfitRecommender.kt: 1차 규칙 필터/스코어/조합 생성/Top-K 압축.
- app/domain/OnDeviceOutfitRanker.kt(신규): LFM2 프롬프트 생성/호출/JSON 파서.
- app/ai/local/ (신규): LlamaEngine, GrammarLoader, TokenBudgeter.
- app/ui/HomeScreen: Feature Flag(온디바이스 사용 여부) 토글/설정.
- scripts/: json-schema → gbnf 변환 스크립트, 모델 다운로드 스크립트.

11.10 QA/DoD(온디바이스)
- 동일 입력에 대해 grammar 강제 JSON 100% 수신(재시도 ≤1회).
- 워드로브 5천 아이템 시나리오: 1차 필터/Top-K 압축으로 입력 토큰 10k 이하 유지.
- 비/강풍/혹서/혹한 4패턴에서 각 3세트 모두 날씨 적합.
- 캐시 유효·재생성 버튼 정상, 오프라인 모드 동작(모델 탑재 후).
- 평균 지연 목표 충족(디바이스별 측정표 첨부).

11.11 마일스톤(온디바이스 트랙)
1) PoC: llama.cpp 연동 + outfit.gbnf + 소규모 워드로브(≤200)로 TOP3 생성.
2) 성능화: 1차 필터/Top-K/조합 프루닝 구현, 토큰 예산/지연 최적화.
3) 품질: 컬러/실루엣 규칙 고도화, 사용자 피드백 학습(선호 색/핏 가점).
4) 안정화: 에러/리트라이/폴백, 캐시/오프라인, 해시 검증.
5) 출시 옵션: Feature Flag로 Cloud vs On-device 선택 빌드.
