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

[3단계: 탐색/구매] ⭐ 업데이트 (2025-10-30)
1.	상점 탭에서 추천/검색
2.	**이미지 기반 검색** (신규 기능)
    - (A) 옷장 아이템 선택 → "유사 상품 찾기"
    - (B) 새 사진 업로드 → 자동 분석 및 검색
3.	찜/장바구니/구매
4.	몰별 순차 결제(Chrome Custom Tabs)

⸻
	2.	산출물(Deliverables)
	1.	Android 앱 전체 소스(Gradle KTS/리소스)
	2.	README.md (빌드/실행/테스트 안내)
	3.	모듈/패키지 구조 문서(간단 아키/DI/교체 포인트)
	4.	테스트: 유닛(추천 로직), 간단 UI(그리드 칼럼 적응)
	5.	QA 체크리스트(본 PRD 시나리오 체크박스)
	6.	샘플 데이터(JSON: 옷장/상점)
	7.	라이선스/주석(KDoc)
	8.	클라우드 태깅/추천 JSON 스키마 및 검증 규칙
	9.	성능 로그 리포트(지연/성공률, PII 미수집)
	10.	AI 경로: Cloud-only(Google Vertex AI Gemini 2.5 Flash Lite → JSON 강제, OAuth 2.0 Service Account 인증)
  11.	서버리스 우선(로컬 저장). 외부 API: Open-Meteo(키 불요), Naver Shopping, Google Programmable Search(JSON API) — 모든 키 기반 API는 Cloudflare Workers 프록시를 통해 호출(앱 내 키 저장/전달 금지)
	13.	보안: API 키/시크릿 클라 포함 금지, PII/HW 식별자 수집 금지
	14.	성능 목표(Cloud-only): 태깅 응답 600–1500ms, 추천 400–1200ms, 성공률 ≥ 98%, 재시도 ≤ 2회

⸻
	3.	구현 범위(필수)

A. Try-On(가상 피팅, 클라우드 API — NanoBanana/Gemini)
•	CompositeTryOnEngine: NanoBananaTryOnEngine 우선(Google Vertex AI Gemini 2.5 Flash Image 모델), 필요 시 CloudTryOnEngine
•	인증: 앱은 Cloudflare Workers 프록시 엔드포인트를 호출하며, 실제 Service Account 키와 OAuth 2.0 토큰 생성은 Worker Secrets로 보관/처리(앱 내 키 없음)
•	저장 경로: getExternalFilesDir(Pictures)/tryon/*.png (FileProvider 공유)
•	교체점: TryOnEngine 인터페이스 유지
•	상태: 구현 완료(FittingScreen, CompositeTryOnEngine, GeminiApiHelper)

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
•	기본: Google Vertex AI models/gemini-2.5-flash-lite → JSON 강제(스키마 검증/재시도) → Wardrobe 자동채움
•	인증: Cloudflare Workers 프록시에서 OAuth 2.0 Service Account 토큰 자동 생성/갱신


⸻
	4.	화면 & 내비게이션
•	Home: 오늘 날씨 + 코디 TOP3 + 퀵 버튼
•	Try-On: 포토 피커 → 상/하 프리뷰 → 저장/공유
•	Wardrobe: 리스트/필터 → 추가/편집(자동 태깅 결과 편집)
•	Recommendations: 옷장 세트 추천(JSON) + 외부 검색 카드
•	Shop: 검색 + 카드
•	Cart: 몰별 그룹 + 순차 결제
•	Gallery: 결과 그리드(확대/공유)

⸻
	5.	디렉터리/아키텍처(샘플)

app/
├─ App.kt (애플리케이션 초기화)
├─ data/
│   ├─ db/ (AppDb, Dao, Converters)
│   ├─ model/ (Garment, CartItem, WeatherSnapshot)
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
– 유닛: OutfitRecommender, RecommendationService, JsonSchemaValidator, AutoTagger 라우팅
– 간단 UI: 갤러리 칼럼 변화, Wardrobe 필터

⸻
	7.	구현 순서(마일스톤)
		1.	부트스트랩(Compose/Room/DataStore/프록시)
	2.	Try-On 엔진 + 저장/갤러리
	4.	Wardrobe CRUD(+ 색상 추출)
	5.	Open-Meteo + 추천 로직 + 홈 TOP3
	6.	Cloud Auto-Tagging(Gemini 2.5 Flash Lite → JSON 검증/재시도)
	7.	RecommendationService + Naver/Google 검색 병렬 통합 + UI(Recommendations)
	8.	Shop/Recommendations 카드 → Cart 확장(source/deeplink) + 순차 결제
	9.	폴더블/접근성/QA 통과 → README/테스트 정리

⸻
	8.	유의 사항
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
	10.	클라우드 태깅/추천 전환 (Vertex AI, JSON 강제, Gemini 2.5 Flash Lite)

**🔄 2025-11-09 업데이트: Vertex AI 전환 완료**
- **전환 사유**: Google AI Studio API의 지역 제한 문제 해결
  - 문제: "User location is not supported for the API use" 오류
  - 원인: API 키 기반 인증의 지리적 제약
  - 해결: Vertex AI OAuth 2.0 Service Account 인증으로 전환
- **변경 사항**:
  - 인증: API Key → OAuth 2.0 Service Account (JWT RS256)
  - 엔드포인트: generativelanguage.googleapis.com → us-central1-aiplatform.googleapis.com
  - 프록시: Cloudflare Workers에서 자동 토큰 생성 및 갱신
  - 지역 제한: 완전 해결 (전 세계 어디서나 사용 가능)
- **비용**: 사용량 기반 과금 (예상 월 $0.25, 1000회 태깅 기준)

10.1 자동 태깅(Cloud, 기본 경로, Vertex AI)
- 모델: `gemini-2.5-flash-lite` (Google Vertex AI)
- API: Vertex AI Gemini API v1
- 인증: OAuth 2.0 Service Account (Cloudflare Workers에서 자동 처리)
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
- 호출: 앱→Cloudflare Workers 프록시(`https://fitghost-proxy.vinny4920-081.workers.dev/proxy/gemini/tag`)로 POST
  - Worker 내부에서 Vertex AI OAuth 토큰 자동 생성 후 Vertex AI Gemini API 호출

10.2 옷장 추천(Cloud → JSON 세트 강제, Vertex AI)
- 모델: `gemini-2.5-flash-lite` (Google Vertex AI)
- 인증: OAuth 2.0 Service Account (자동 처리)
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
- 호출: 앱→Cloudflare Workers 프록시→Vertex AI (OAuth 토큰 자동 관리)

10.3 외부 검색 연동(병렬, Naver/Google — Cloudflare 프록시 경유) ✅ 테스트 완료

#### 10.3.1 네이버 쇼핑 검색 API
- **프록시 엔드포인트**: `GET https://fitghost-proxy.vinny4920-081.workers.dev/proxy/naver/shop`
- **파라미터**:
  - `query`: 검색어 (필수)
  - `display`: 결과 개수 (기본 20, 최대 100)
  - `start`: 시작 위치 (기본 1)
  - `sort`: 정렬 방식 (`sim`=유사도, `date`=날짜, `asc`=가격오름차순, `dsc`=가격내림차순)

- **실제 응답 구조** (테스트 완료):
```json
{
  "lastBuildDate": "Thu, 30 Oct 2025 14:40:11 +0900",
  "total": 34289,
  "start": 1,
  "display": 3,
  "items": [
    {
      "title": "상품명 <b>검색어</b> 포함",
      "link": "https://search.shopping.naver.com/catalog/48197246290",
      "image": "https://shopping-phinf.pstatic.net/main_4819724/...",
      "lprice": "15900",
      "hprice": "25000",
      "mallName": "네이버",
      "productId": "48197246290",
      "productType": "1",
      "brand": "브랜드명",
      "maker": "제조사",
      "category1": "패션의류",
      "category2": "남성의류",
      "category3": "바지",
      "category4": "청바지"
    }
  ]
}
```

- **파싱 방법**:
```kotlin
data class NaverSearchResponse(
    val lastBuildDate: String,
    val total: Int,
    val start: Int,
    val display: Int,
    val items: List<NaverShopItem>
)

data class NaverShopItem(
    val title: String,           // HTML 태그 포함 (제거 필요)
    val link: String,
    val image: String,
    val lprice: String,          // 최저가 (문자열)
    val hprice: String,          // 최고가 (문자열)
    val mallName: String,
    val productId: String,
    val productType: String,
    val brand: String,
    val maker: String,
    val category1: String,
    val category2: String,
    val category3: String,
    val category4: String
)

// Product 모델로 변환
fun NaverShopItem.toProduct(): Product {
    return Product(
        id = productId,
        name = title.replace(Regex("<[^>]*>"), ""), // HTML 태그 제거
        price = lprice.toIntOrNull() ?: 0,
        imageUrl = image,
        seller = mallName,
        url = link,
        source = "naver",
        category = ProductCategory.fromString(category1)
    )
}
```

#### 10.3.2 구글 커스텀 검색 API
- **프록시 엔드포인트**: `GET https://fitghost-proxy.vinny4920-081.workers.dev/proxy/google/cse`
- **파라미터**:
  - `q`: 검색어 (필수)
  - `num`: 결과 개수 (기본 10, 최대 10)
  - `start`: 시작 위치 (기본 1)

- **실제 응답 구조** (테스트 완료):
```json
{
  "kind": "customsearch#search",
  "url": {
    "type": "application/json",
    "template": "https://www.googleapis.com/customsearch/v1?..."
  },
  "queries": {
    "request": [
      {
        "title": "Google Custom Search - jeans",
        "totalResults": "4590000000",
        "searchTerms": "jeans",
        "count": 3,
        "startIndex": 1,
        "cx": "REDACTED_GOOGLE_CSE_CX"
      }
    ],
    "nextPage": [
      {
        "title": "Google Custom Search - jeans",
        "totalResults": "4590000000",
        "searchTerms": "jeans",
        "count": 3,
        "startIndex": 4
      }
    ]
  },
  "searchInformation": {
    "searchTime": 0.315233,
    "formattedSearchTime": "0.32",
    "totalResults": "4590000000"
  },
  "items": [
    {
      "kind": "customsearch#result",
      "title": "상품 제목",
      "htmlTitle": "상품 <b>제목</b>",
      "link": "https://example.com/product/123",
      "displayLink": "example.com",
      "snippet": "상품 설명 텍스트...",
      "htmlSnippet": "상품 <b>설명</b> 텍스트...",
      "pagemap": {
        "cse_image": [
          {
            "src": "https://example.com/image.jpg"
          }
        ],
        "metatags": [
          {
            "og:price:amount": "29900",
            "og:price:currency": "KRW",
            "og:title": "상품명",
            "og:description": "상품 설명"
          }
        ]
      }
    }
  ]
}
```

- **파싱 방법**:
```kotlin
data class GoogleSearchResponse(
    val kind: String,
    val searchInformation: SearchInformation,
    val items: List<GoogleSearchItem>?
)

data class SearchInformation(
    val searchTime: Double,
    val totalResults: String
)

data class GoogleSearchItem(
    val title: String,
    val link: String,
    val snippet: String,
    val displayLink: String,
    val pagemap: GooglePageMap?
)

data class GooglePageMap(
    val cse_image: List<GoogleImage>?,
    val metatags: List<Map<String, String>>?
)

data class GoogleImage(
    val src: String
)

// Product 모델로 변환
fun GoogleSearchItem.toProduct(): Product? {
    val imageUrl = pagemap?.cse_image?.firstOrNull()?.src ?: return null
    val metatag = pagemap?.metatags?.firstOrNull()
    val price = metatag?.get("og:price:amount")?.toIntOrNull()
        ?: extractPriceFromSnippet(snippet)
    
    return Product(
        id = link.hashCode().toString(),
        name = title,
        price = price,
        imageUrl = imageUrl,
        seller = displayLink,
        url = link,
        source = "google",
        category = ProductCategory.OTHER
    )
}

// 스니펫에서 가격 추출
private fun extractPriceFromSnippet(text: String): Int {
    val priceRegex = Regex("""(\d{1,3}(?:,\d{3})*)\s*원""")
    return priceRegex.find(text)
        ?.groupValues?.get(1)
        ?.replace(",", "")
        ?.toIntOrNull() ?: 0
}
```

#### 10.3.3 병렬 검색 및 통합
- **병렬 처리**: `CoroutineScope.async` 2개의 호출 → 통합 결과
```kotlin
suspend fun searchProducts(query: String): List<Product> = coroutineScope {
    val naverDeferred = async { searchNaver(query) }
    val googleDeferred = async { searchGoogle(query) }
    
    val naverResults = naverDeferred.await()
    val googleResults = googleDeferred.await()
    
    // 결과 통합 및 중복 제거
    (naverResults + googleResults)
        .distinctBy { it.url }
        .sortedByDescending { it.relevanceScore }
        .take(20)
}
```

- **통합 스키마**: `Product` 모델
```kotlin
data class Product(
    val id: String,
    val name: String,
    val price: Int,
    val imageUrl: String,
    val seller: String,
    val url: String,
    val source: String,        // "naver" | "google"
    val category: ProductCategory,
    val isWishlisted: Boolean = false
)
```

- **랭킹/중복제거**: 
  - URL 기준 중복 제거 (`distinctBy { it.url }`)
  - 제목+가격+도메인 해시로 유사 항목 병합
  - 품질 가중치: 가격 범위, 판매자 평판, 배송 정보

- **오류 처리**: 
  - 타임아웃/429 → 재시도 지수백오프
  - 한쪽 실패 시 다른쪽 결과만 표시
  - 빈 결과 시 사용자 친화적 메시지

10.4 이미지 기반 검색 시스템 ⭐ 신규 (2025-10-30)

#### 10.4.1 사용자 지침 및 구현 방향

**핵심 문제 정의**:
- 기존 텍스트 검색의 한계: "검은색 청바지와 어울리는 옷"으로 검색 시 청바지만 검색됨
- 사용자 니즈: 특정 아이템과 실제로 코디 가능한 다른 아이템을 찾고 싶음

**혁신적 해결 방안**:
온디바이스 AI(LFM2)를 활용하여 "어울리는 아이템 카테고리"를 자동 생성하고, 각 카테고리로 실제 상품 검색을 수행

#### 10.4.2 검색 트리거 방식 (하이브리드 접근)

**방식 A: 옷장 아이템 기반 검색**
- 위치: 옷장 화면의 각 아이템 카드
- 트리거: "유사 상품 찾기" 버튼
- 데이터 소스: 기존 메타데이터 (category, color, detailType 등)
- 장점: API 호출 없음, 즉시 검색 가능
- 플로우:
  1. 사용자가 옷장 아이템 선택
  2. "유사 상품 찾기" 버튼 클릭
  3. 기존 메타데이터 사용 (예: "검은색 청바지", "하의")
  4. 온디바이스 AI 실행 → 어울리는 카테고리 생성
  5. 검색 탭으로 자동 이동 + 결과 표시

**방식 B: 새 사진 업로드 검색**
- 위치: 검색 탭 상단
- 트리거: 카메라/갤러리 아이콘 버튼
- 데이터 소스: Gemini Vision API (이미지 분석)
- 장점: 옷장에 없는 아이템도 검색 가능
- 플로우:
  1. 사용자가 검색 탭에서 카메라 아이콘 클릭
  2. Photo Picker 실행 → 사진 선택
  3. Gemini Vision API로 이미지 분석 (1-2초)
  4. 메타데이터 추출 (category, color, detailType)
  5. 온디바이스 AI 실행 → 어울리는 카테고리 생성
  6. 검색 + 결과 표시

**통합 전략**:
- 옷장 아이템: 기존 메타데이터 우선 (빠름)
- 새 사진: Gemini Vision API 사용 (정확함)
- 두 방식 모두 온디바이스 AI로 어울리는 카테고리 생성

#### 10.4.3 온디바이스 AI 카테고리 생성 (핵심 혁신)

**목적**: "검은색 청바지"와 어울리는 구체적인 아이템 카테고리 생성

**입력**:
- `itemDescription`: "검은색 청바지"
- `itemCategory`: "하의"

**AI 프롬프트 구조**:
```
당신은 패션 스타일리스트입니다. 주어진 의류 아이템과 잘 어울리는 다른 아이템들을 추천해주세요.

아이템: 검은색 청바지
카테고리: 하의

이 아이템과 코디하기 좋은 다른 아이템들을 최대 5개 추천해주세요.
각 추천은 "색상 + 아이템명" 형식으로 작성하세요.

응답은 반드시 다음 JSON 형식으로만 작성하세요:
{
  "matching_items": [
    "화이트 셔츠",
    "그레이 니트",
    "블랙 스니커즈",
    "브라운 가죽 재킷",
    "베이지 백팩"
  ]
}

규칙:
1. 색상과 아이템명을 함께 포함하세요
2. 실제 검색 가능한 구체적인 아이템명을 사용하세요
3. 같은 카테고리는 제외하세요 (예: 청바지에 청바지 추천 금지)
4. 계절과 스타일을 고려하세요
5. JSON 형식만 출력하세요
```

**AI 출력 예시**:
```json
{
  "matching_items": [
    "화이트 셔츠",
    "그레이 니트",
    "블랙 스니커즈",
    "브라운 가죽 재킷",
    "베이지 백팩"
  ]
}
```

**검색 실행**:
- 각 카테고리로 네이버/구글 병렬 검색
- 예: "화이트 셔츠" → 네이버 20개 + 구글 10개 → 통합 20개

**폴백 전략**:
- AI 실패 시: 카테고리별 기본 추천 사용
  - 하의 → ["화이트 셔츠", "그레이 니트", "블랙 스니커즈", "네이비 재킷"]
  - 상의 → ["블랙 청바지", "베이지 슬랙스", "화이트 스니커즈", "브라운 재킷"]
  - 아우터 → ["화이트 티셔츠", "블랙 청바지", "화이트 스니커즈", "그레이 머플러"]

#### 10.4.4 검색 결과 표시 방식 (A+B 통합)

**UI 구성**:
```
검색 탭 (개선)
├─ 검색 바 (텍스트 입력)
├─ 📷 이미지 검색 버튼 (새 기능)
├─ 선택된 이미지 미리보기 섹션 (새 기능)
│   ├─ 이미지 썸네일
│   ├─ 분석 결과: "검은색 청바지"
│   └─ [X] 제거 버튼
├─ 🤖 AI 추천 카테고리 칩 (새 기능)
│   ├─ [화이트 셔츠] [그레이 니트] [블랙 스니커즈]
│   └─ 각 칩 클릭 시 해당 카테고리만 필터링
└─ 검색 결과 리스트
    ├─ 실제 상품 카드 (사진, 가격, 쇼핑몰, 링크)
    │   ├─ 상품 이미지 (터치 시 외부 링크 이동)
    │   ├─ 상품명
    │   ├─ 가격 (파싱 가능 시 표시)
    │   ├─ 쇼핑몰명
    │   ├─ [찜하기] [장바구니] 버튼
    │   └─ 출처 배지 (네이버/구글)
    └─ 유사 상품 섹션 (AI 추천 카테고리별 그룹)
        ├─ "화이트 셔츠" 섹션 (5개)
        ├─ "그레이 니트" 섹션 (5개)
        └─ "블랙 스니커즈" 섹션 (5개)
```

**상품 카드 디자인**:
```
┌─────────────────────────────────────┐
│ [상품 이미지]                       │
│                                     │
│ 상품명                              │
│ ₩ 45,000                           │
│ 쇼핑몰명 | [N] 네이버              │
│                                     │
│ [♡ 찜하기] [🛒 장바구니]           │
└─────────────────────────────────────┘
```

#### 10.4.5 검색 정확도 향상 전략

**상세 속성 활용** (옵션 B):
- 기본 정보: 카테고리 + 색상 + 타입
- 상세 정보: + 패턴 + 소재 + 핏
- 예시:
  - 기본: "블랙 청바지"
  - 상세: "블랙 슬림핏 데님 청바지"

**검색어 생성 로직**:
```kotlin
fun generateSearchQuery(metadata: ClothingMetadata): String {
    val parts = mutableListOf<String>()
    
    // 필수: 색상 + 타입
    if (metadata.color.isNotBlank()) parts.add(metadata.color)
    if (metadata.detailType.isNotBlank()) parts.add(metadata.detailType)
    
    // 선택: 패턴, 소재 (있을 경우만)
    if (metadata.pattern.isNotBlank() && metadata.pattern != "무지") {
        parts.add(metadata.pattern)
    }
    if (metadata.fabric.isNotBlank()) {
        parts.add(metadata.fabric)
    }
    
    return parts.joinToString(" ")
}
```

#### 10.4.6 사용자 경험 최적화

**로딩 상태**:
1. 이미지 분석 중 (1-2초): "이미지를 분석하고 있어요..."
2. AI 카테고리 생성 중 (2-5초): "어울리는 아이템을 찾고 있어요..."
3. 상품 검색 중 (0.5-1초): "상품을 검색하고 있어요..."

**에러 처리**:
- 이미지 분석 실패: "이미지를 분석할 수 없습니다. 다른 사진을 선택해주세요."
- AI 생성 실패: 폴백 카테고리 사용 (사용자에게 알리지 않음)
- 검색 실패: "검색 결과를 불러올 수 없습니다. 다시 시도해주세요."

**사용자 피드백**:
- 검색 결과 상단에 "검은색 청바지와 어울리는 아이템을 찾았어요!" 메시지
- AI 추천 카테고리 칩 표시 (클릭 시 필터링)
- 검색어 수정 가능 (텍스트 입력창 활성화)

**접근성**:
- 이미지 업로드 버튼: "사진으로 검색" TalkBack 지원
- 상품 카드: "상품명, 가격, 쇼핑몰" 정보 읽기
- 카테고리 칩: "화이트 셔츠 필터" 읽기

#### 10.4.7 성능 목표

| 단계 | 목표 시간 | 비고 |
|------|----------|------|
| 이미지 분석 (Gemini) | 1-2초 | 새 사진만 |
| AI 카테고리 생성 (온디바이스) | 2-5초 | 모델 다운로드 후 |
| 네이버/구글 검색 (병렬) | 0.5-1초 | 각 카테고리당 |
| **총 소요 시간** | **3-8초** | 옷장 아이템은 더 빠름 (1-6초) |

**최적화 전략**:
- 옷장 아이템: 이미지 분석 스킵 (기존 메타데이터 사용)
- 검색 결과 캐싱: 동일 검색어 24시간 캐시
- 프리로딩: 인기 카테고리 미리 검색

#### 10.4.8 구현 파일 및 코드 삽입 포인트

**신규 파일**:
- `app/ai/MatchingItemsGenerator.kt`: 온디바이스 AI로 어울리는 카테고리 생성
- `app/data/network/NaverApi.kt`: 네이버 쇼핑 API 인터페이스
- `app/data/network/GoogleCseApi.kt`: 구글 검색 API 인터페이스
- `app/data/network/SearchApiClient.kt`: Retrofit 클라이언트

**업데이트 파일**:
- `app/data/repository/ShopRepository.kt`:
  - `searchByImage(bitmap)`: 새 사진 업로드 검색
  - `searchMatchingItems(description, category)`: 옷장 아이템 검색
  - `searchNaver()`, `searchGoogle()`: 실제 API 호출
- `app/data/model/ShopModels.kt`:
  - `ImageSearchResult`: 이미지 검색 결과 모델
  - `Product.source`: 출처 필드 추가 ("naver" | "google")
- `app/ui/screens/shop/ShopViewModel.kt`: (다음 단계)
  - 이미지 검색 상태 관리
  - AI 추천 카테고리 상태
- `app/ui/screens/shop/ShopScreen.kt`: (다음 단계)
  - 이미지 업로드 UI
  - 카테고리 칩 UI
  - 검색 결과 개선
- `app/ui/screens/wardrobe/WardrobeScreen.kt`: (다음 단계)
  - "유사 상품 찾기" 버튼 추가

#### 10.4.9 데이터 모델

**ImageSearchResult**:
```kotlin
data class ImageSearchResult(
    val sourceImage: String?,           // "검은색 청바지"
    val matchingCategories: List<String>, // ["화이트 셔츠", "그레이 니트", ...]
    val products: List<Product>         // 실제 검색 결과 (최대 20개)
)
```

**Product (확장)**:
```kotlin
data class Product(
    val id: String,
    val name: String,
    val price: Int,
    val imageUrl: String,
    val category: ProductCategory,
    val shopName: String,
    val shopUrl: String,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val isWishlisted: Boolean = false,
    val source: String = ""  // "naver" | "google" | ""
)
```

#### 10.4.10 Definition of Done (이미지 검색)

- ✅ 옷장 아이템에서 "유사 상품 찾기" 버튼 작동
- ✅ 검색 탭에서 이미지 업로드 기능 작동
- ✅ Gemini Vision API로 이미지 분석 성공 (1-2초)
- ✅ 온디바이스 AI로 어울리는 카테고리 생성 (2-5초, JSON 형식)
- ✅ 네이버/구글 병렬 검색 성공 (각 카테고리당 0.5-1초)
- ✅ 검색 결과 20개 이상 표시 (사진, 가격, 링크 포함)
- ✅ AI 추천 카테고리 칩 표시 및 필터링 기능
- ✅ 상품 카드 터치 시 외부 링크 이동 (Chrome Custom Tabs)
- ✅ 찜하기/장바구니 기능 정상 작동
- ✅ 에러 처리: 이미지 분석 실패, AI 생성 실패, 검색 실패 각각 대응
- ✅ 로딩 상태: 각 단계별 로딩 메시지 표시
- ✅ 접근성: TalkBack 지원, 44dp 이상 터치 영역

10.5 추천 화면 → 장바구니 → 결제
- 추천 화면: 각 추천 세트 아이템에 외부 결과 카드 리스트 노출(찜/장바구니)
- 장바구니: `CartItem`에 `source(naver|google)`와 `deeplink(url)` 필드 추가, 몰별 그룹화
- 결제: 기존 흐름과 동일(Chrome Custom Tabs, 순차 결제), 결제 완료 콜백 핸들러 유지

10.6 Definition of Done(추가)
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
