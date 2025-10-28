# SmolVLM 모델 다운로드 및 R2 업로드 완료 보고서

## 작업 개요
- **날짜**: 2025년 9월 30일
- **모델**: SmolVLM-500M-Instruct-f16.gguf (782.4 MB)
- **소스**: ggml-org/SmolVLM-500M-Instruct-GGUF (HuggingFace)
- **목적지**: Cloudflare R2 (ghostfit-models 버킷)

## 작업 완료 상태

### ✓ 1. 로컬 다운로드
- **위치**: `/Users/dj20014920/Desktop/ghostfit/ghostfit_models/`
- **파일명**: `SmolVLM-500M-Instruct-f16.gguf`
- **파일 크기**: 782 MB
- **다운로드 방법**: curl (재시작 지원)

### ✓ 2. R2 업로드
- **버킷**: `ghostfit-models`
- **키**: `ghostfit_models/SmolVLM-500M-Instruct-f16.gguf`
- **업로드 시간**: 2025-09-30 14:28:35
- **파일 크기**: 782.4 MiB

### ✓ 3. 검증 완료
- R2에 파일이 정상적으로 업로드됨
- 파일 크기 일치 확인
- 업로드 시간 기록됨

## Cloudflare R2 구조

### 전체 버킷 목록
```
1. deepsleep-models (기존 버킷)
   └── models/ (기존 4개 모델)
       ├── amoral-gemma3-1B-v2-Q5_K_M.gguf (811.9 MiB)
       ├── cherrydavid_hyperclovax-seed-text-instruct-0.5b-q8_0.gguf (692.6 MiB)
       ├── kexplo_hyperclovax-seed-text-instruct-0.5b-q4_k_m.gguf (411.9 MiB)
       └── yeebwn_hyperclovax-seed-text-instruct-1.5b-q4_k_m.gguf (959.9 MiB)

2. ghostfit-models (신규 버킷)
   └── ghostfit_models/ (신규 SmolVLM 모델)
       └── SmolVLM-500M-Instruct-f16.gguf (782.4 MiB)
```

### 총 모델 수
- **기존 모델**: 4개 (deepsleep-models 버킷)
- **신규 모델**: 1개 (ghostfit-models 버킷)
- **전체**: 5개 모델

## 생성된 스크립트 파일

### 1. download_model.sh
- **위치**: `/Users/dj20014920/Desktop/ghostfit/scripts/download_model.sh`
- **용도**: SmolVLM 모델 다운로드 (재시작 지원)
- **특징**: curl 사용, 중단된 다운로드 재개 가능

### 2. upload_to_r2.sh
- **위치**: `/Users/dj20014920/Desktop/ghostfit/scripts/upload_to_r2.sh`
- **용도**: R2 업로드 (AWS CLI 사용)
- **특징**: 버킷 확인, 업로드, 검증 자동화

### 3. download_and_upload_model.py
- **위치**: `/Users/dj20014920/Desktop/ghostfit/scripts/download_and_upload_model.py`
- **용도**: Python을 이용한 다운로드 및 업로드 통합 스크립트

### 4. download_model_optimized.py
- **위치**: `/Users/dj20014920/Desktop/ghostfit/scripts/download_model_optimized.py`
- **용도**: Python을 이용한 최적화된 다운로드

### 5. upload_to_r2.py
- **위치**: `/Users/dj20014920/Desktop/ghostfit/scripts/upload_to_r2.py`
- **용도**: Python boto3를 이용한 R2 업로드

## R2 접근 정보

### 엔드포인트
```
https://081a9810680543ee912eb54ae15876a3.r2.cloudflarestorage.com
```

### 인증 정보
- **Access Key ID**: `008dcc84e74268ed100f51d709e18dec`
- **Secret Access Key**: `fabb13cbdeabe8f90698d86de722aee5d9424492bae9845a8b025fba0e90e30f`

### S3 호환 명령어 예시
```bash
# 환경 변수 설정
export AWS_ACCESS_KEY_ID="008dcc84e74268ed100f51d709e18dec"
export AWS_SECRET_ACCESS_KEY="fabb13cbdeabe8f90698d86de722aee5d9424492bae9845a8b025fba0e90e30f"

# 버킷 목록 확인
aws s3 ls --endpoint-url https://081a9810680543ee912eb54ae15876a3.r2.cloudflarestorage.com

# ghostfit-models 버킷 내용 확인
aws s3 ls s3://ghostfit-models/ --endpoint-url https://081a9810680543ee912eb54ae15876a3.r2.cloudflarestorage.com --recursive --human-readable

# deepsleep-models 버킷 내용 확인
aws s3 ls s3://deepsleep-models/ --endpoint-url https://081a9810680543ee912eb54ae15876a3.r2.cloudflarestorage.com --recursive --human-readable
```

## 다음 단계 (앱 연결)

### 안드로이드 앱에서 모델 사용하기
1. **R2 공개 URL 설정** (필요시)
   - Cloudflare 대시보드에서 R2 버킷 공개 설정
   - 또는 R2 Custom Domain 설정

2. **앱에서 다운로드 로직 구현**
   ```kotlin
   // 예시
   val modelUrl = "https://your-r2-domain/ghostfit_models/SmolVLM-500M-Instruct-f16.gguf"
   val localPath = context.getExternalFilesDir(null).toString() + "/models/"
   
   // 다운로드 및 저장
   downloadModel(modelUrl, localPath)
   ```

3. **모델 로드 및 추론**
   - GGUF 형식 모델 로더 구현
   - llama.cpp 또는 호환 라이브러리 사용

## 참고사항

- 기존 `models` 폴더와 구분하기 위해 `ghostfit_models` 폴더 사용
- 두 버킷은 별도로 관리됨 (deepsleep-models, ghostfit-models)
- 모든 모델은 GGUF 형식 (llama.cpp 호환)
- 로컬 백업: `/Users/dj20014920/Desktop/ghostfit/ghostfit_models/`

## 작업 완료 체크리스트

- [x] ghostfit_models 폴더 생성
- [x] SmolVLM-500M-Instruct-f16.gguf 다운로드 (782 MB)
- [x] Cloudflare R2 버킷 생성 (ghostfit-models)
- [x] R2에 모델 업로드
- [x] 업로드 검증 완료
- [x] 기존 4개 모델 확인 (deepsleep-models 버킷)
- [x] 스크립트 파일 생성 및 문서화
- [ ] 앱과 연결 (다음 단계)
