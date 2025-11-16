#!/usr/bin/env python3
"""
SmolVLM 모델 다운로드 (최적화 버전 - wget 사용)
"""

import os
import subprocess
import sys

# 모델 정보
MODEL_URL = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/SmolVLM-500M-Instruct-f16.gguf"
MODEL_FILENAME = "SmolVLM-500M-Instruct-f16.gguf"
LOCAL_DIR = "./tryon_models"

def main():
    """wget를 이용한 빠른 다운로드"""
    print("=" * 60)
    print("SmolVLM 모델 다운로드 (wget 사용)")
    print("=" * 60)
    
    os.makedirs(LOCAL_DIR, exist_ok=True)
    local_path = os.path.join(LOCAL_DIR, MODEL_FILENAME)
    
    # 이미 다운로드된 파일 확인
    if os.path.exists(local_path):
        file_size_mb = os.path.getsize(local_path) / (1024 * 1024)
        print(f"\n파일이 이미 존재합니다: {local_path}")
        print(f"파일 크기: {file_size_mb:.2f} MB")
        
        # 782MB 정도가 완전한 파일 크기
        if file_size_mb > 780:
            print("✓ 다운로드가 완료된 것으로 보입니다.")
            return local_path
        else:
            print(f"파일이 불완전합니다 (예상: ~782 MB). 계속 다운로드합니다...")
            # 부분 다운로드된 파일 삭제
            os.remove(local_path)
    
    # wget로 다운로드 (재시작 지원)
    print(f"\n다운로드 시작: {MODEL_URL}")
    print(f"저장 위치: {local_path}\n")
    
    cmd = [
        "curl",
        "-L",  # 리다이렉트 따라가기
        "-o", local_path,
        "--progress-bar",
        MODEL_URL
    ]
    
    try:
        subprocess.run(cmd, check=True)
        
        file_size_mb = os.path.getsize(local_path) / (1024 * 1024)
        print(f"\n✓ 다운로드 완료!")
        print(f"파일 크기: {file_size_mb:.2f} MB")
        return local_path
        
    except subprocess.CalledProcessError as e:
        print(f"\n다운로드 실패: {e}")
        sys.exit(1)
    except KeyboardInterrupt:
        print("\n다운로드가 중단되었습니다.")
        sys.exit(1)

if __name__ == "__main__":
    main()
