FitGhost — Android MVP 통합 PRD (Unified, On-Device AI 포함)

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
	8.	AI 모델 자산 목록 및 SHA-256 해시(무결성 확인)
	9.	온디바이스 벤치마크 리포트(지연/메모리/전력, 단말별)

⸻
	3.	기술 스택 & 제약
•	Kotlin + Jetpack Compose, MVVM
•	minSdk 26 / targetSdk 34
•	의존성: compose BOM, material3, activity-compose, lifecycle, coil-compose, DataStore, Room, Retrofit/OkHttp, play-services-ads, androidx.window, customtabs
•	온디바이스 AI: ONNX Runtime(Android), TensorFlow Lite(+ NNAPI/GPU delegate), llama.cpp JNI
•	서버리스 우선(로컬 저장), 외부 API 옵션: 날씨 Open-Meteo(키 불요)
•	광고: 개발 중 테스트 유닗 ID (README에 실제 키 교체 위치)
•	보안: API 키/시크릿 클라 포함 금지, PII/HW 식별자 수집 금지
•	성능/용량 목표: 모델 총 용량 ≤ 320MB (Gemma 포함), 1장 처리 지연 80–300ms 목표(단말/경로별 상이), 스레드 4–6 기본

⸻
	4.	구현 범위(필수)

A. Try-On(가상 피팅, 로컬)
•	FakeTryOnEngine: 선택 영역(상/하) 톤 보정 + 워터마크 AI PREVIEW/AI RENDER
•	저장 경로: getExternalFilesDir(Pictures)/tryon/*.png (FileProvider 공유)
•	교체점: TryOnEngine 인터페이스 유지

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

G. 자동 태깅(Auto-Tagging, 온디바이스)
•	이미지 입력 → 다중 전처리/분석(세그/검출/분류/속성) → Gemma(JSON 구조화) → Wardrobe 항목 자동채움

⸻
	5.	화면 & 내비게이션
•	Home: 오늘 날씨 + 코디 TOP3 + 퀵 버튼
•	Try-On: 포토 피커 → 상/하 프리뷰 → 저장/공유 → 크레딧/광고
•	Wardrobe: 리스트/필터 → 추가/편집(자동 태깅 결과 편집)
•	Shop: 검색 + 카드
•	Cart: 몰별 그룹 + 순차 결제
•	Gallery: 결과 그리드(확대/공유)

⸻
	6.	디렉터리/아키텍처(샘플)

app/
├─ App.kt (MobileAds.init, UMP 부팅)
├─ ads/RewardedAdController.kt
├─ data/
│   ├─ db/ (AppDb, Dao, Converters)
│   ├─ model/ (Garment, CartItem, WeatherSnapshot)
│   ├─ CreditStore.kt
│   ├─ LocalImageStore.kt
│   ├─ TryOnRepository.kt
│   └─ weather/ (OpenMeteoApi, WeatherRepo)
├─ domain/OutfitRecommender.kt
├─ engine/ (TryOnEngine.kt, FakeTryOnEngine.kt)
├─ ai/
│   ├─ AutoTagger.kt
│   ├─ seg/ (U2NetpOrt.kt, SelfieSegTfl.kt)
│   ├─ det/ (EffDetLite0Tfl.kt, MobileNetSsdTfl.kt)
│   ├─ cls/ (EffLite0Tfl.kt, MobileNetV3LargeTfl.kt)
│   ├─ attr/ (PatternTinyCnnTfl.kt, FabricTinyCnnTfl.kt, ColorHeuristics.kt)
│   └─ llm/ (LlamaCppBridge.kt, Grammar.gbnf)
├─ ui/ (Home, TryOn, Wardrobe, Shop, Cart, Gallery, components/)
└─ util/ (Browser.kt, Format.kt)

⸻
	7.	Definition of Done (DoD)
•	클린 체크아웃 → 즉시 빌드/실행
•	핵심 플로우 전부 통과:
– Try-On 생성/저장/갤러리
– 주 10회 소진/광고 보너스
– Wardrobe CRUD/필터
– Open-Meteo → 홈 TOP3
– Shop→Cart→몰별 순차 결제
– 폴더블 그리드 증분
– 자동 태깅: 단일/다중 아이템 이미지에서 상용 태깅 품질 달성(정확도 가이드 별첨)
•	접근성/UX: TalkBack, 로딩/에러, 최소 터치 타깃
•	주석/문서: KDoc, README 완결
•	테스트:
– 유닛: CreditStore, OutfitRecommender, ColorHeuristics, AutoTagger 라우팅
– 간단 UI: 갤러리 칼럼 변화, Wardrobe 필터

⸻
	8.	구현 순서(마일스톤)
	1.	부트스트랩(Compose/Room/DataStore/Ads/UMP)
	2.	Try-On 엔진 + 저장/갤러리
	3.	크레딧/광고 연결
	4.	Wardrobe CRUD(+ 색상 추출)
	5.	Open-Meteo + 추천 로직 + 홈 TOP3
	6.	Auto-Tagging 1차 경로(Seg→Eff-Lite0→Color→Gemma)
	7.	Auto-Tagging 확장(Det/Pattern/Fabric/계층분류 + 튜닝)
	8.	Shop 더미 + Cart + 순차 결제
	9.	폴더블/접근성/QA 통과 → README/테스트 정리

⸻
	9.	유의 사항
•	광고는 테스트 유닛 ID(실배포 전 교체)
•	외부 결제는 딥링크/Custom Tabs (통합결제 금지)
•	이미지/데이터는 로컬 우선, 외부 업로드 금지
•	Search API/AI 엔진 연동 대비 인터페이스로 느슨 결합
•	모델/자산은 내부 저장소 복사 후 로드(가속기 활용률↑)

⸻
	10.	온디바이스 AI 파이프라인 (하이브리드, 파일명 중심)

목표: 제로 트레이닝·온디바이스·상업 배포 가능 / 총 용량·지연 최적화
구성: [{Seg}+{Det}+{Cls(계층)}+{Pattern}+{Fabric}+{Color}] → Gemma(JSON)

10.1 모델 구성(최종)

(1) 세그멘트(의류/배경 분리)
•	권장: U²-Netp (ONNX) — u2netp.onnx (320×320), ~4–5MB
•	대안(착용샷 경량): MediaPipe Selfie Segmentation (TFLite) — selfie_segmenter.tflite

(2) 객체 검출(다중 아이템 컷아웃)
•	권장: EfficientDet-Lite0 (TFLite INT8) — efficientdet-lite0-int8.tflite, ~5–7MB
•	대안: MobileNet-SSD (TFLite INT8) — mobilenet-ssd-int8.tflite, ~4–6MB

(3) 계층적 분류기
•	Top(대분류: 상/하/아우터 등): EfficientNet-Lite0 (TFLite INT8) — efficientnet-lite0-int8.tflite, ~3–7MB
•	Sub(소분류: 티셔츠/셔츠/청바지/스커트 등): MobileNetV3-Large (TFLite INT8) — mobilenetv3-large-int8.tflite, ~4–6MB

(4) 패턴 인식
•	TinyCNN (TFLite INT8) — pattern_tinycnn_int8.tflite, ~1–3MB
•	보조 휴리스틱: 주파수/에지 기반(스트라이프/체크/도트/그래픽 감지)

(5) 재질 인식
•	TinyCNN (TFLite INT8) — fabric_tinycnn_int8.tflite, ~2–6MB
•	보조 휴리스틱: 광택/주름/니트 짜임 등 텍스처 지표

(6) 색상 추출
•	Heuristics — HSV 변환 → K-Means 팔레트(1–2 대표색) → PaletteMap(color_names.json)

(7) 텍스트(JSON 구조화 전용 LLM)
•	권장: google/gemma-3-270m-it (GGUF, Q4) — gemma-3-270m-it-Q4_K_M.gguf (~245–260MB)
•	JSON 강제: llama.cpp –grammar fashion_json_schema.gbnf
•	(옵션) 한국어 문장력↑, 메모리↑: Qwen2.5-1.5B-GGUF(Q4_K_M)

⸻

10.2 앱 자산 구성(복사 체크리스트)

app/src/main/assets/
├─ u2netp.onnx                            # Seg (또는 selfie_segmenter.tflite)
├─ efficientdet-lite0-int8.tflite         # Det (옵션: mobilenet-ssd-int8.tflite)
├─ efficientnet-lite0-int8.tflite         # Cls-Top
├─ mobilenetv3-large-int8.tflite          # Cls-Sub
├─ pattern_tinycnn_int8.tflite            # Pattern
├─ fabric_tinycnn_int8.tflite             # Fabric
├─ color_names.json                       # Color 팔레트 사전
├─ gemma-3-270m-it-Q4_K_M.gguf            # LLM(JSON 생성)
└─ fashion_json_schema.gbnf               # GBNF 문법(출력 강제)

⸻

10.3 엔진 플로우(요약)
	1.	Seg — 전경 마스크
•	ONNX Runtime(Android) + NNAPI EP로 u2netp.onnx 실행(320px) → 전경 마스크
•	착용샷 전용 경량 경로 필요 시 selfie_segmenter.tflite로 대체
	2.	Det — 객체 박스(복수)
•	TFLite(Det)로 의류 박스 추출 → 각 박스별 ROI 크롭(마스크와 교차하여 정밀 추출)
	3.	Cls(계층) — 대분류 → 소분류
•	Top: EfficientNet-Lite0 → type[T/B/O…] + conf
•	Sub: MobileNetV3-Large(Top 결과별 라우팅) → 세부 카테고리 + conf
	4.	Pattern/Fabric/Color — 속성 병렬 추론
•	Pattern TinyCNN + 휴리스틱(주파수/에지)
•	Fabric TinyCNN + 휴리스틱(광택/주름/니트)
•	Color HSV → K-Means → PaletteMap
	5.	LLM(JSON) — 통합/구조화
•	llama.cpp JNI로 Gemma Q4 로드 → –grammar fashion_json_schema.gbnf
•	비전 태그/점수 배열 → 최종 JSON(제목/카테고리/속성/간단 사유)
	6.	실패/보정
•	conf < τ 또는 Top1–Top2 < δ → 후보 3개 카드(사용자 선택 반영)
•	마스크 품질 낮음 → 세그 생략/재시도, 감마/밝기 전처리
•	색상 불명 → “unknown” 허용 + 유저 편집

⸻

10.4 입·출력 스키마

비전 태깅 결과(내부)

{
“image_id”: “uuid”,
“category_top”: “상의|하의|아우터|기타”,
“category_sub”: “티셔츠|셔츠|청바지|스커트|…”,
“attributes”: {
“color_primary”: “black”,
“color_secondary”: “gray|null”,
“pattern_basic”: “solid|stripe|check|dot|graphic|null”,
“fabric_basic”: “cotton|denim|leather|knit|silk|null”
},
“confidence”: { “top”: 0.93, “sub”: 0.86 }
}

LLM 요청 → 응답(JSON 고정 스키마)

{
“title”: “빨강 스트라이프 면 티셔츠”,
“category”: { “top”: “상의”, “sub”: “티셔츠” },
“attributes”: {
“color_primary”: “red”,
“color_secondary”: “white”,
“pattern_basic”: “stripe”,
“fabric_basic”: “cotton”,
“fit”: “regular”
},
“rationale_ko”: “스트라이프 텍스처와 면 재질 특징이 두드러짐.”,
“confidence”: 0.80
}

⸻

10.5 GBNF(예시, 축약)

root ::= “{” ws
‘“title”’ ws “:” ws string ws “,” ws
‘“category”’ ws “:” ws category ws “,” ws
‘“attributes”’ ws “:” ws attributes ws “,” ws
‘“rationale_ko”’ ws “:” ws string ws “,” ws
‘“confidence”’ ws “:” ws number
ws? “}”
category ::= “{” ws
‘“top”’ ws “:” ws string ws “,” ws
‘“sub”’ ws “:” ws string
ws? “}”
attributes ::= “{” ws
‘“color_primary”’ ws “:” ws string ws “,” ws
‘“color_secondary”’ ws “:” ws string ws “,” ws
‘“pattern_basic”’ ws “:” ws string ws “,” ws
‘“fabric_basic”’ ws “:” ws string ws “,” ws
‘“fit”’ ws “:” ws string
ws? “}”
string ::= “"” ([^”\] | “\"”)* “"”
number ::= (“0” | “-”? [1-9][0-9]* ) (”.” [0-9]+)?
ws ::= ([ \t\n\r])*

⸻

10.6 스펙 요약(용량·지연, 단말/입력 해상도에 따라 변동)

Stage	모델(대안)	포맷	추정 용량(MB)	추정 지연(ms)	비고
Seg	U²-Netp	ONNX (FP32/INT8)	4–5	25–60	320px, NNAPI EP
Seg(대안)	SelfieSeg	TFLite INT8	1–5	5–20	착용샷 경량 경로
Det	EfficientDet-Lite0	TFLite INT8	5–7	25–60	다중 아이템 컷아웃
Det(대안)	MobileNet-SSD	TFLite INT8	4–6	15–35	경량·간단
Cls-Top	EfficientNet-Lite0	TFLite INT8	3–7	20–35	대분류
Cls-Sub	MobileNetV3-Large	TFLite INT8	4–6	15–30	소분류
Pattern	TinyCNN	TFLite INT8	1–3	5–15	패턴 종류
Fabric	TinyCNN	TFLite INT8	2–6	10–25	재질 추정
Color	Heuristic	—	0	2–5	HSV→KMeans→Palette
LLM	Gemma-3-270M-it(Q4)	GGUF	245–260	50–200	GBNF JSON, RAM 512–768MB 여유 권장

플로우별 합(예시)
•	기본: [Seg(U²) + Cls-Top + Color] → Gemma ≈ 270–280MB / ~100–300ms
•	확장: [Seg + Det + Cls-Top/Sub + Pattern + Fabric + Color] → Gemma ≈ 285–295MB / ~140–400ms
•	착용샷 경량: [SelfieSeg + Cls-Top + Color] → Gemma ≈ 270MB / ~80–260ms

⸻

10.7 배포/최적화 팁
•	NNAPI/GPU delegate 우선, Interpreter/Session 싱글톤 + 버퍼 재사용, 워밍업
•	모델은 내부 저장소 복사 후 로드(일부 단말 가속기 활용률↑)
•	프리뷰 경로는 저해상도·세그 생략, 저장 시 정밀 경로
•	LLM 토큰 제한/캐시 관리, 스레드(4–6) 튜닝
•	Top/Sub 계층 분기, conf 임계(τ)·Top1–Top2 차(δ)로 품질 제어

⸻

10.8 실패·예외 처리(UX)
•	conf < τ 또는 Top1–Top2 < δ → 후보 3개 카드 제시(사용자 선택 반영)
•	마스크 품질 낮음 → 세그 생략/재시도
•	색상 불명 → “unknown” 허용 + 유저 편집
•	다중 아이템 시 박스별 진행, 실패 박스는 재시도 큐

⸻

10.9 프라이버시/로그
•	기본 온디바이스 처리, 서버 전송 금지(옵트인 수집 시 암호화)
•	로그: 결과 메타(클래스/점수/지연)만, 원본 픽셀 저장 금지

⸻

10.10 QA 체크리스트(요약)
•	기기 매트릭스(저가/중가/고가)별 ms/장 확보
•	조명/배경/원거리/검정-on-검정/거울샷 스트레스
•	한글 라벨/맵핑 QA
•	τ/δ 그리드 서치(Top/Sub/Pattern/Fabric별)
•	크래시/전력/메모리 측정(프리뷰 vs 셔터)
•	JSON 밸리데이터(Grammar 불일치 → 재생성/경고)

⸻
	11.	코드 스니펫(축약, 개념)

ONNX(Runtime) — U²-Netp
val env = OrtEnvironment.getEnvironment()
val opts = OrtSession.SessionOptions().apply { addNnapi() }
val bytes = assets.open(“u2netp.onnx”).readBytes()
val session = env.createSession(bytes, opts)
// bitmap → 320x320 FloatBuffer 전처리 후 run()

TFLite — Det / Cls(Top/Sub)
val nnapi = NnapiDelegate()
val det = Interpreter(loadMapped(“efficientdet-lite0-int8.tflite”),
Interpreter.Options().addDelegate(nnapi).setNumThreads(4))
val top = Interpreter(loadMapped(“efficientnet-lite0-int8.tflite”),
Interpreter.Options().addDelegate(nnapi).setNumThreads(4))
val sub = Interpreter(loadMapped(“mobilenetv3-large-int8.tflite”),
Interpreter.Options().addDelegate(nnapi).setNumThreads(4))
// Det → ROIs → Top → (route) Sub

Attr — Pattern/Fabric/Color
val pattern = Interpreter(loadMapped(“pattern_tinycnn_int8.tflite”), opts)
val fabric  = Interpreter(loadMapped(“fabric_tinycnn_int8.tflite”), opts)
// Color: HSV→KMeans(2)→PaletteMap(color_names.json)

llama.cpp JNI — Gemma Q4 + GBNF
LlamaCpp.loadModel(filesDir.resolve(“gemma-3-270m-it-Q4_K_M.gguf”).path)
val json = LlamaCpp.generate(
prompt = buildPrompt(tags),
grammarPath = filesDir.resolve(“fashion_json_schema.gbnf”).path,
maxTokens = 180
)

⸻
	12.	통합 한 줄 요약
•	비전/전처리(하이브리드):
[{Seg: U²-Netp(ONNX) | SelfieSeg(TFLite)} + {Det: EfficientDet-Lite0(TFLite INT8) | MobileNet-SSD(옵션)} + {Cls: Top(EfficientNet-Lite0 INT8)→Sub(MobileNetV3-Large INT8)} + {Pattern: TinyCNN | 주파수·에지 휴리스틱} + {Fabric: TinyCNN | 텍스처 휴리스틱} + {Color: HSV→K-Means→PaletteMap}]
→ gemma-3-270m-it(GGUF,Q4) + GBNF → JSON 저장
•	장점: 제로 트레이닝·온디바이스·상업 OK / 용량·속도 균형 / 다중 모델 병렬·계층 추론로 정확도 극대화 / 교체·확장 용이
