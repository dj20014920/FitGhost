# FitGhost (MVP)

Kotlin + Jetpack Compose 기반의 서버리스 패션 코디/가상피팅 앱 MVP.

## 빌드/실행
- Android Studio Koala 이상 권장
- minSdk 26, targetSdk 34
- `./gradlew :app:assembleDebug`

## 광고/UMP
- AdMob 테스트 유닛 ID 사용: Rewarded `ca-app-pub-3940256099942544/5224354917`
- 출시 전 실제 ID로 교체 위치: `ads/RewardedAdController.kt`
- UMP 테스트 모드: `App.kt` 내 `ConsentDebugSettings` 주석 참고

## 데이터 저장
- Try-On 결과: `getExternalFilesDir(Pictures)/tryon` 에 PNG 저장. `FileProvider` 로 공유 가능.
- Room DB: `wardrobe`, `cart`
- DataStore: `week(YYYY-'W'ww)`, `used`, `bonus`

## 날씨 API
- Open-Meteo (키 불필요) 사용. `data/weather` 참고.

## 테스트
- 유닛: `OutfitRecommenderTest`
  - 실행: `./gradlew test`
- 계측(Instrumented): `CreditStoreInstrumentedTest`, `GalleryGridInstrumentedTest`
  - 실행: `./gradlew connectedAndroidTest`
  - GalleryGridInstrumentedTest: 저장 이미지가 없을 때 안내 문구가 표시되는지 확인(그리드 렌더링 최소 검증)

## 모듈/패키지 구조
- app/
  - App.kt, MainActivity.kt
  - ads/ (RewardedAdController — AdMob 리워드 로딩/표시)
  - data/
    - db/ (AppDb, Dao, Converters)
    - model/ (Garment, CartItem, WeatherSnapshot)
    - weather/ (OpenMeteoApi, WeatherRepo)
    - CreditStore, LocalImageStore, TryOnRepository
  - domain/ (OutfitRecommender)
  - engine/ (TryOnEngine, FakeTryOnEngine — 엔진 교체 지점)
  - ui/ (Home, TryOn, Wardrobe, Shop, Cart, Gallery, components)
  - util/ (Browser, ServiceLocator)

아키텍처/DI
- 간단 MVVM(+Repository) 구조. ServiceLocator로 Room/Retrofit/도메인 싱글톤 주입
- 교체 포인트: `TryOnEngine` 구현만 교체하면 `TryOnRepository` 생성자 주입으로 전환 가능
- 날씨 API 베이스 URL/인터페이스는 `data/weather`에서 관리

## 교체/확장 포인트
- TryOnEngine: `engine/`에 서버리스 프록시 기반 구현을 추가하고 DI에서 교체
- Shop 데이터 소스: 현재 더미 -> 후속 프록시로 전환 인터페이스 여지

## 접근성/보안
- TalkBack 라벨/포커스 고려, 버튼 최소 터치 타깃
- API 키/PII 수집 금지. 외부 업로드 없음.
