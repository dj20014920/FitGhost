#!/bin/bash

# SmolVLM 모델 다운로드 (wget 재시작 지원)
MODEL_URL="https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/SmolVLM-500M-Instruct-f16.gguf"
MMPROJ_URL="https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/SmolVLM-500M-Instruct-mmproj.gguf"
LOCAL_DIR="./ghostfit_models"
MODEL_FILE="${LOCAL_DIR}/SmolVLM-500M-Instruct-f16.gguf"
MMPROJ_FILE="${LOCAL_DIR}/SmolVLM-500M-Instruct-mmproj.gguf"

echo "============================================================"
echo "SmolVLM 모델 다운로드 (재시작 지원)"
echo "============================================================"

mkdir -p "${LOCAL_DIR}"

# wget 또는 curl 사용 (재시작 지원)
if command -v wget &> /dev/null; then
    echo "wget 사용 (재시작 지원)"
    wget -c "${MODEL_URL}" -O "${MODEL_FILE}"
else
    echo "curl 사용 (재시작 지원)"
    curl -C - -L -o "${MODEL_FILE}" "${MODEL_URL}"
fi

# 파일 크기 확인(메인)
if [ -f "${MODEL_FILE}" ]; then
    FILE_SIZE=$(ls -lh "${MODEL_FILE}" | awk '{print $5}')
    echo ""
    echo "✓ 모델 다운로드 완료!"
    echo "파일: ${MODEL_FILE}"
    echo "크기: ${FILE_SIZE}"
else
    echo ""
    echo "✗ 모델 다운로드 실패"
    exit 1
fi

# (선택) mmproj 다운로드 시도 (실패해도 계속)
echo ""
echo "mmproj 다운로드 시도..."
if command -v wget &> /dev/null; then
    wget -c "${MMPROJ_URL}" -O "${MMPROJ_FILE}" || true
else
    curl -C - -L -o "${MMPROJ_FILE}" "${MMPROJ_URL}" || true
fi
if [ -f "${MMPROJ_FILE}" ]; then
    FILE_SIZE=$(ls -lh "${MMPROJ_FILE}" | awk '{print $5}')
    echo "✓ mmproj 준비됨: ${MMPROJ_FILE} (${FILE_SIZE})"
else
    echo "(참고) mmproj 파일 없음 또는 다운로드 실패 — 서버 -hf 사용 시 자동 다운로드 가능"
fi
