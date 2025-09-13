

⸻

FitGhost — 에이전트용 개발 지시 프롬프트

역할: 당신은 Android 시니어 개발자 에이전트입니다.
목표: 아래 PRD를 유일한 소스 오브 트루스로 삼아, Kotlin + Jetpack Compose 기반의 안드로이드 MVP를 컴파일 가능한 전체 소스로 구현하고, 문서/테스트/QA 체크리스트까지 납품하세요.

0) 참고 문서(필수)
	•	PRD 파일 경로(맥):
/Users/dj20014920/Desktop/ghostfit/MASTERPRD.md
(동일 내용이 저장소 루트에도 MASTERPRD.md로 존재한다고 가정)
	•	이 PRD에 명시된 모든 기능·비기능 요구사항, 화면/플로우, 데이터 모델, 일정, QA 시나리오를 반드시 준수할 것.  ￼

⸻

1) 산출물(Deliverables)
	1.	안드로이드 앱 전체 소스(Gradle KTS, Manifest, 리소스 포함) — 즉시 빌드 가능
	2.	README.md — 빌드/실행/테스트/광고 테스트 ID/UMP 테스트 모드 안내
	3.	모듈/패키지 구조 문서 — 아키텍처 다이어그램(간단), DI 경로, 교체 포인트 명시
	4.	테스트 코드 — 유닛 테스트(크레딧/추천 스코어러), 간단 UI 테스트(갤러리 그리드 칼럼 적응)
	5.	QA 체크리스트 — PRD의 QA 시나리오를 체크박스로 정리
	6.	샘플 데이터 — 옷장 더미, 상점 더미(링크/이미지/가격) JSON
	7.	라이선스/주석 — 파일 헤더/중요 클래스에 KDoc

⸻

2) 기술 스택 & 제약
	•	Kotlin + Jetpack Compose, MVVM
	•	minSdk 26 / targetSdk 34
	•	의존성:
	•	compose BOM 최신, material3, activity-compose, lifecycle, coil-compose
	•	datastore-preferences, room(wardrobe/cart), retrofit(okhttp)
	•	play-services-ads(AdMob), androidx.window(폴더블), customtabs
	•	UMP(User Messaging Platform) 템플릿 포함
	•	서버리스: 사용자 데이터는 단말 로컬 저장. 외부 API는 옵션(날씨는 Open-Meteo), 쇼핑은 우선 딥링크.
	•	광고: 개발 중 테스트 유닛 ID 사용(보상형). 실제 키 교체 위치를 README에 명시.
	•	보안: API 키/시크릿을 클라이언트에 넣지 않음(후속 프록시 대비). PII/HW 식별자 수집 금지.

⸻

3) 구현 범위(필수 기능)

A. Try-On(가상 피팅, 로컬)
	•	FakeTryOnEngine: 선택한 사진의 상/하 절반 중 택1에 톤 보정 + “AI PREVIEW/AI RENDER” 워터마크 오버레이 →
getExternalFilesDir(Pictures)/tryon에 PNG 저장(FileProvider로 공유 가능)
	•	엔진 교체점: TryOnEngine 인터페이스 유지. 이후 서버리스 프록시 기반의 AI 엔진 구현만 끼우면 되도록 설계.

B. 크레딧/광고
	•	주당 10회 무료 + AdMob 리워드 시청 시 보너스 +1
	•	DataStore(keys: week(YYYY-'W'ww), used, bonus)
	•	예외(NO_CREDIT)시 광고 유도 UI → 보상 콜백에서 bonus++ 반영
	•	UMP 동의 템플릿 포함(테스트 모드 설정 주석)

C. 내 옷장(로컬 DB)
	•	Room 엔티티: Garment(id, type[T/B/O], color, pattern?, fabric?, warmth1~5, waterResist, imageUri, tags[])
	•	CRUD + 필터(타입/색/태그), 간단 색상 자동 추출(K-Means) 옵션

D. 날씨 기반 추천
	•	Open-Meteo(키 불요)로 오늘 기온/강수/풍속 스냅샷
	•	OutfitRecommender: 기온-보온 적합 + 강수/방수 + 풍속/겉옷 + 태그 호환 스코어 → TOP3 카드 생성
	•	홈 화면에 오늘의 코디 TOP3 표시(+ Try-On 바로가기 버튼)

E. 상품 추천·구매(딥링크)
	•	Shop 화면: 검색어 입력 → 더미 데이터로 카드 리스트 구성(버튼: 찜/장바구니/구매하기)
후속으로 네이버 Search API 프록시를 연결할 수 있게 인터페이스만 설계
	•	Cart 화면: 로컬 장바구니(몰별 그룹) + “이 몰에서 결제하기(순차 오픈)”
→ Chrome Custom Tabs로 링크 순차 오픈 & “다음 열기” 배너 UX

F. 갤러리 & 폴더블
	•	Try-On 결과 PNG를 Adaptive 그리드로 열람(GridCells.Adaptive(minSize=140.dp))
	•	폴더블/대화면에서 칼럼 자동 증가(2→3→4~6)

⸻

4) 화면 & 내비게이션
	•	Home: 오늘 날씨 카드 + 코디 TOP3 + “Try-On”/“상점” 퀵 버튼
	•	Try-On: 포토 피커 → 상/하 프리뷰 → 저장/공유 → 크레딧 부족 시 광고 제안
	•	Wardrobe: 리스트/필터 → 추가/편집
	•	Shop: 검색 + 카드(찜/장바구니/구매하기)
	•	Cart: 몰별 그룹 섹션 + 순차 오픈 버튼 + 완료 체크
	•	Gallery: 결과 이미지 그리드(탭: 확대/공유)

⸻

5) 디렉터리/아키텍처(샘플)

app/
 ├─ App.kt (MobileAds.init, UMP 템플릿 부팅)
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
 ├─ ui/ (Home, TryOn, Wardrobe, Shop, Cart, Gallery, components/)
 └─ util/ (Browser.kt, Format.kt)


⸻

6) 품질 게이트(Definition of Done)
	•	빌드 성공: 클린 체크아웃 후 즉시 빌드/실행 가능
	•	기능 검증(핵심 플로우 전부 통과):
	•	Try-On 프리뷰 생성/저장/갤러리 표출
	•	주당 무료 10회 소진 → 광고 시 보너스 충전 동작
	•	Wardrobe CRUD & 필터
	•	Open-Meteo 호출 → 홈 TOP3 코디 표시
	•	Shop 카드 → 장바구니 담기 → 몰별 순차 오픈 결제 플로우
	•	폴더블에서 그리드 칼럼 증가 확인
	•	접근성/UX: TalkBack 라벨/포커스, 로딩/에러 표시, 버튼 최소 터치 타깃
	•	주석/문서: 주요 클래스 KDoc, README 완결
	•	테스트:
	•	유닛: CreditStore 주차 리셋/소진/보너스, OutfitRecommender 스코어 로직
	•	UI 스냅샷 or 간단 인스트루먼트: 갤러리 그리드 칼럼 변화

⸻

7) 구현 순서(마일스톤)
	1.	프로젝트 부트스트랩(Compose/Material3/Window/Coil/Room/DataStore/Ads/CustomTabs/UMP)
	2.	Try-On 로컬 엔진 + 저장/갤러리 + FileProvider
	3.	CreditStore(주/보너스) + AdMob 리워드 + UMP 템플릿 연결
	4.	Wardrobe CRUD(+ 간단 색상 추출)
	5.	Open-Meteo 연동 + OutfitRecommender + 홈 TOP3
	6.	Shop 더미 데이터 + Cart + 몰별 순차 오픈
	7.	폴더블/접근성/QA 시나리오 통과 → README/테스트 정리

각 단계 종료 시 README에 데모 절차와 스크린샷 추가.

⸻

8) 유의 사항
	•	광고는 반드시 테스트 유닛 ID 사용(실배포 전 교체 주석)
	•	외부 상점 결제는 모두 딥링크/Custom Tab로 처리(통합 결제 금지)
	•	이미지/데이터는 로컬 저장 우선, 외부 업로드 금지
	•	추후 “Search API/ DataLab/ AI 엔진” 연동 시를 대비해 인터페이스/리포지토리로 교체 가능 설계

⸻

9) 제출 형식
	•	사용자에게 보고

⸻

위 지시를 충족하는 완결 소스와 문서를 생성하시오.
PRD의 요구사항·화면·데이터·QA 체크리스트는 엄격하게 준수하시오.  ￼
