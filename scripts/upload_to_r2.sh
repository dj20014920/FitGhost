#!/bin/bash

#!/bin/bash

# Cloudflare R2 업로드 스크립트 (AWS CLI 사용)
# 주의: 자격증명은 환경변수로 주입하세요 (파일에 절대 하드코딩 금지)

# 필수 환경변수 확인
if [ -z "${AWS_ACCESS_KEY_ID}" ] || [ -z "${AWS_SECRET_ACCESS_KEY}" ]; then
  echo "오류: AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY 환경변수가 필요합니다." >&2
  exit 1
fi

# R2 설정 (엔드포인트/버킷/키는 필요 시 인자로 재정의 가능)
R2_ENDPOINT=${R2_ENDPOINT:-"https://081a9810680543ee912eb54ae15876a3.r2.cloudflarestorage.com"}
BUCKET_NAME=${BUCKET_NAME:-"ghostfit-models"}
LOCAL_FILE=${LOCAL_FILE:-"./tryon_models/SmolVLM-500M-Instruct-f16.gguf"}
R2_KEY=${R2_KEY:-"SmolVLM-500M-Instruct-f16.gguf"}  # 버킷 루트에 직접 배치

echo "============================================================"
echo "Cloudflare R2 업로드 (AWS CLI 사용)"
echo "============================================================"
echo ""
echo "로컬 파일: ${LOCAL_FILE}"
echo "R2 버킷: ${BUCKET_NAME}"
echo "R2 키: ${R2_KEY}"
echo ""

# 파일 크기 확인
if [ -f "${LOCAL_FILE}" ]; then
    FILE_SIZE=$(ls -lh "${LOCAL_FILE}" | awk '{print $5}')
    echo "파일 크기: ${FILE_SIZE}"
else
    echo "오류: 파일을 찾을 수 없습니다: ${LOCAL_FILE}"
    exit 1
fi

# 버킷 존재 확인 및 생성
echo ""
echo "버킷 확인 중..."
aws s3 ls s3://${BUCKET_NAME} --endpoint-url ${R2_ENDPOINT} >/dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "버킷 '${BUCKET_NAME}' 생성 중..."
    aws s3 mb s3://${BUCKET_NAME} --endpoint-url ${R2_ENDPOINT}
    if [ $? -eq 0 ]; then
        echo "✓ 버킷 '${BUCKET_NAME}' 생성 완료"
    else
        echo "버킷 생성 실패 (이미 존재하거나 권한 문제일 수 있습니다. 계속 진행합니다.)"
    fi
else
    echo "✓ 버킷 '${BUCKET_NAME}' 확인됨"
fi

# 파일 업로드
echo ""
echo "업로드 시작..."
aws s3 cp "${LOCAL_FILE}" "s3://${BUCKET_NAME}/${R2_KEY}" \
    --endpoint-url ${R2_ENDPOINT} \
    --no-progress

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ R2 업로드 완료!"
    echo "R2 경로: ${BUCKET_NAME}/${R2_KEY}"
else
    echo ""
    echo "✗ 업로드 실패"
    exit 1
fi

# 업로드 검증
echo ""
echo "============================================================"
echo "업로드 검증"
echo "============================================================"
aws s3 ls "s3://${BUCKET_NAME}/${R2_KEY}" --endpoint-url ${R2_ENDPOINT} --human-readable

# 퍼블릭 URL 확인
echo ""
echo "============================================================"
echo "퍼블릭 URL (개발용)"
echo "============================================================"
PUB_BASE=${PUB_BASE:-"https://pub-411b7feaa5b7440786580c2747a9129f.r2.dev"}
echo "${PUB_BASE}/${R2_KEY}"
echo ""
echo "프로덕션에서는 '사용자 설정 도메인'을 버킷에 연결하세요."

# 버킷 전체 내용 확인
echo ""
echo "============================================================"
echo "버킷 '${BUCKET_NAME}' 전체 내용"
echo "============================================================"
echo ""
aws s3 ls "s3://${BUCKET_NAME}/" --endpoint-url ${R2_ENDPOINT} --recursive --human-readable --summarize

echo ""
echo "============================================================"
echo "모든 작업 완료!"
echo "============================================================"
