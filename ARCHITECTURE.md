# 모듈/패키지 구조 및 DI 경로

간단 아키텍처(MVVM/리포지토리)

- App.kt: MobileAds/UMP 초기화
- ads/RewardedAdController: 리워드 광고 로딩/표시
- engine/TryOnEngine: 교체 가능한 인터페이스
- engine/FakeTryOnEngine: 로컬 톤 보정 + 워터마크
- data/
  - db/: Room(AppDb, Dao, Converters)
  - model/: Garment, CartItem, WeatherSnapshot
  - CreditStore: DataStore(week/used/bonus)
  - LocalImageStore: PNG 저장/FileProvider
  - TryOnRepository: 크레딧 소비 + 엔진 실행 + 저장
  - weather/: OpenMeteoApi, WeatherRepo
- domain/OutfitRecommender: 코디 스코어러
- ui/screens: Home/TryOn/Wardrobe/Shop/Cart/Gallery
- util/Browser: Custom Tabs 오픈

DI/교체 포인트
- TryOnRepository(engine: TryOnEngine) 생성자 주입 → 서버리스 AI 엔진 추가 시 이 지점만 교체
- Shop 데이터 소스는 현재 더미. retrofit 기반 프록시 추가 시 인터페이스로 분리 권장

아키텍처 다이어그램(간단)

[UI] → ViewModel → Repository → (Room/DataStore/Weather API/TryOnEngine)

현재 MVP는 단순화를 위해 일부 화면에서 직접 Repository/DB 접근. 후속 단계에서 ViewModel/DI(Hilt) 도입 권장.
