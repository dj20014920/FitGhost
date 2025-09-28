# FitGhost 프로젝트 아키텍처

## 기술 스택
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Architecture**: MVVM + Clean Architecture
- **Navigation**: Navigation Compose
- **Database**: Room
- **Network**: Retrofit + OkHttp + Moshi
- **Storage**: DataStore Preferences
- **Images**: Coil
- **Ads**: AdMob + UMP
- **Device**: Window Manager (폴더블)

## 패키지 구조
```
com.fitghost.app/
├── MainActivity.kt
├── FitGhostApplication.kt
├── ui/
│   ├── screens/
│   │   ├── home/         # 홈 화면 (날씨 + AI 추천)
│   │   ├── fitting/      # 가상 피팅
│   │   ├── wardrobe/     # 옷장 (메인 + 추가)
│   │   ├── shop/         # 상점 (검색)
│   │   └── gallery/      # 갤러리
│   ├── components/       # 공통 컴포넌트
│   ├── theme/           # Soft Clay 디자인 시스템
│   └── navigation/      # 네비게이션 설정
├── data/
│   ├── db/              # Room 엔티티 & DAO
│   ├── network/         # API 인터페이스
│   ├── repository/      # Repository 구현
│   └── model/          # 데이터 모델
├── domain/             # UseCase & Repository 인터페이스
├── engine/             # Try-On 엔진
├── ads/               # AdMob 관련
└── util/              # 유틸리티
```

## 하단 네비게이션 구조
1. 피팅 (가상_피팅_화면)
2. 옷장 (옷장)
3. 홈 (홈) - 기본 화면
4. 상점 (검색_화면)
5. 갤러리 (갤러리_화면)

## 디자인 시스템
- **Primary**: Soft Clay 뉴모피즘
- **색상**: 기존 홈 화면의 CSS 변수 기반
- **아이콘**: Material Icons
- **폰트**: Noto Sans KR