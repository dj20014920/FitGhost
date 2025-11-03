# AI 모델 다운로드 상태 유지 문제 해결

## 🔍 문제 분석

### 증상
- AI 모델 다운로드 완료 후 설정에서는 "다운로드 완료" 표시
- 앱 재시작 시 홈 화면에 "AI 모델 준비하기" 버튼이 다시 나타남
- 매번 앱을 켤 때마다 다운로드 버튼이 활성화됨

### 로그 분석
```
10-30 13:22:03.225 ModelManager: Download completed successfully!
10-30 13:22:03.247 ModelManager: Model state updated: READY

[앱 재시작]

10-30 13:30:53.798 ModelManager: getModelState() -> READY
10-30 13:30:53.798 ModelManager: reconcileState() - Current state: READY, Model file exists: true, Size: 663MB
10-30 13:30:53.832 ModelManager: Model files incomplete, changed to NOT_READY
```

### 근본 원인 발견 ⭐

**파일 크기 불일치 문제**:

1. **실제 다운로드된 파일 크기**: 663.52 MB (695,749,568 bytes)
2. **코드에서 기대하는 크기**: 696 MB
3. **검증 로직**: `663MB >= (696MB - 10MB = 686MB)` → **실패!**

```kotlin
// 잘못된 상수
private const val MODEL_SIZE_MB = 696L // 695.75 MB

// 검증 로직
val mainOk = modelFile.exists() && 
             (modelFile.length() / (1024 * 1024)) >= (MODEL_SIZE_MB - 10)
// 663 >= 686 → false!
```

**결과**: `reconcileState()`가 파일이 불완전하다고 판단하여 `NOT_READY`로 변경

## ✅ 해결 방법

### 1. 실제 파일 크기 확인
```bash
curl -I https://cdn.emozleep.space/models/LFM2-1.2B-Q4_0.gguf | grep content-length
# content-length: 695749568

echo "695749568 / 1024 / 1024" | bc -l
# 663.51849365234375
```

### 2. 상수 수정
**ModelManager.kt**
```kotlin
// 수정 전
private const val MODEL_SIZE_MB = 696L // 695.75 MB

// 수정 후
private const val MODEL_SIZE_MB = 664L // 실제: 663.52 MB (695,749,568 bytes)
```

### 3. UI 표시 수정
**HomeScreen.kt**
```kotlin
// 수정 전
downloadProgress = DownloadProgress(0f, 696f, 0)
"삭제 후 다시 사용하려면 696MB를 다시 다운로드해야 합니다."

// 수정 후
downloadProgress = DownloadProgress(0f, 664f, 0)
"삭제 후 다시 사용하려면 664MB를 다시 다운로드해야 합니다."
```

**WardrobeAddScreen.kt**
```kotlin
// 수정 전
downloadProgress = ModelManager.DownloadProgress(0f, 696f, 0)

// 수정 후
downloadProgress = ModelManager.DownloadProgress(0f, 664f, 0)
```

## 🔧 검증 로직 설명

### 파일 크기 검증
```kotlin
val mainOk = modelFile.exists() && 
             (modelFile.length() / (1024 * 1024)) >= (MODEL_SIZE_MB - 10)
```

**수정 전**:
- 기대 크기: 696 MB
- 최소 허용: 686 MB (696 - 10)
- 실제 크기: 663 MB
- 결과: `663 >= 686` → **false** ❌

**수정 후**:
- 기대 크기: 664 MB
- 최소 허용: 654 MB (664 - 10)
- 실제 크기: 663 MB
- 결과: `663 >= 654` → **true** ✅

## 📊 수정된 파일 목록

1. **app/src/main/java/com/fitghost/app/ai/ModelManager.kt**
   - `MODEL_SIZE_MB`: 696L → 664L
   - 주석 업데이트: "크기: 696 MB" → "크기: 664 MB (663.52 MB)"

2. **app/src/main/java/com/fitghost/app/ui/screens/home/HomeScreen.kt**
   - `DownloadProgress` 초기값: 696f → 664f
   - 삭제 확인 메시지: "696MB" → "664MB"

3. **app/src/main/java/com/fitghost/app/ui/screens/wardrobe/WardrobeAddScreen.kt**
   - `DownloadProgress` 초기값: 696f → 664f

## 🎯 예상 동작

### 수정 전
```
다운로드 완료 (663MB) → READY 상태 저장
앱 재시작 → reconcileState() 호출
→ 파일 크기 확인: 663MB < 686MB
→ NOT_READY로 변경 ❌
→ 다운로드 버튼 다시 표시
```

### 수정 후
```
다운로드 완료 (663MB) → READY 상태 저장
앱 재시작 → reconcileState() 호출
→ 파일 크기 확인: 663MB >= 654MB ✅
→ READY 상태 유지
→ 다운로드 버튼 숨김
```

## 🧪 테스트 시나리오

### 시나리오 1: 정상 다운로드 후 재시작
1. AI 모델 다운로드 (663MB)
2. 다운로드 완료 → 배너 숨김
3. 앱 재시작
4. **예상 결과**: 배너 계속 숨김 상태 유지 ✅

### 시나리오 2: 파일 손상 감지
1. 모델 파일 일부 삭제 (예: 500MB로 축소)
2. 앱 재시작
3. **예상 결과**: `500MB < 654MB` → NOT_READY → 다운로드 버튼 표시 ✅

### 시나리오 3: 설정에서 모델 삭제
1. 설정 → 모델 삭제
2. 파일 삭제 완료
3. **예상 결과**: NOT_READY → 다운로드 버튼 표시 ✅

## 💡 교훈

### 문제 발생 원인
1. **잘못된 상수 값**: 실제 파일 크기와 코드의 기대 크기 불일치
2. **불충분한 로깅**: 파일 크기 검증 실패 원인이 명확하지 않음
3. **테스트 부족**: 앱 재시작 시나리오 테스트 미흡

### 개선 사항
1. **정확한 상수 값**: 실제 파일 크기 측정 후 설정
2. **상세한 로깅**: 검증 실패 시 구체적인 이유 로깅
3. **충분한 여유**: 10MB 오차 허용으로 네트워크 불안정 대응

## 🎉 결론

**근본 원인**: 모델 파일 크기 상수가 실제 파일보다 33MB 크게 설정되어, 검증 로직이 정상 파일을 불완전하다고 판단

**해결 방법**: 상수를 실제 파일 크기(663.52 MB)에 맞게 664MB로 수정

**효과**: 
- ✅ 다운로드 완료 후 상태 영구 유지
- ✅ 앱 재시작 시에도 다운로드 버튼 숨김 유지
- ✅ 중복 다운로드 완전 차단

---

**수정 완료일**: 2025-10-30
**작성자**: Kiro AI Assistant
**상태**: 완료 및 테스트 준비
