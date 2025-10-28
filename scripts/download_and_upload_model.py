#!/usr/bin/env python3
"""
SmolVLM 모델 다운로드 및 R2 업로드 스크립트
Hugging Face에서 모델을 다운로드하고 Cloudflare R2에 업로드
"""

import os
import sys
import requests
from tqdm import tqdm
import boto3
from botocore.exceptions import ClientError

# Cloudflare R2 설정 (환경변수에서 주입)
R2_ENDPOINT = os.getenv("R2_ENDPOINT", "https://081a9810680543ee912eb54ae15876a3.r2.cloudflarestorage.com")
R2_ACCESS_KEY = os.getenv("AWS_ACCESS_KEY_ID")
R2_SECRET_KEY = os.getenv("AWS_SECRET_ACCESS_KEY")

# 모델 정보
MODEL_URL = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/SmolVLM-500M-Instruct-f16.gguf"
MODEL_FILENAME = "SmolVLM-500M-Instruct-f16.gguf"
# (선택) 멀티모달 projector 파일 정보
MMPROJ_URL = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/SmolVLM-500M-Instruct-mmproj.gguf"
MMPROJ_FILENAME = "SmolVLM-500M-Instruct-mmproj.gguf"

LOCAL_DIR = "./ghostfit_models"
R2_BUCKET = "ghostfit-models"
R2_KEY = "SmolVLM-500M-Instruct-f16.gguf"  # 버킷 루트에 직접 배치
R2_MMPROJ_KEY = "SmolVLM-500M-Instruct-mmproj.gguf"  # 버킷 루트에 직접 배치(선택)

def download_file(url, dest_path):
    """HuggingFace에서 모델 파일 다운로드"""
    print(f"다운로드 시작: {url}")
    print(f"저장 위치: {dest_path}")
    
    response = requests.get(url, stream=True)
    response.raise_for_status()
    
    total_size = int(response.headers.get('content-length', 0))
    
    with open(dest_path, 'wb') as f, tqdm(
        desc=MODEL_FILENAME,
        total=total_size,
        unit='iB',
        unit_scale=True,
        unit_divisor=1024,
    ) as pbar:
        for data in response.iter_content(chunk_size=1024*1024):
            size = f.write(data)
            pbar.update(size)
    
    print(f"다운로드 완료: {dest_path}")
    file_size_mb = os.path.getsize(dest_path) / (1024 * 1024)
    print(f"파일 크기: {file_size_mb:.2f} MB")

def upload_to_r2(local_path, bucket, key):
    """Cloudflare R2에 파일 업로드"""
    print(f"\nR2 업로드 시작...")
    print(f"버킷: {bucket}")
    print(f"키: {key}")
    
    if not R2_ACCESS_KEY or not R2_SECRET_KEY:
        raise RuntimeError("AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY 환경변수가 필요합니다.")

    s3_client = boto3.client(
        's3',
        endpoint_url=R2_ENDPOINT,
        aws_access_key_id=R2_ACCESS_KEY,
        aws_secret_access_key=R2_SECRET_KEY,
        region_name='auto'
    )
    
    # 버킷 존재 확인
    try:
        s3_client.head_bucket(Bucket=bucket)
        print(f"버킷 '{bucket}' 확인됨")
    except ClientError:
        print(f"버킷 '{bucket}' 생성 중...")
        s3_client.create_bucket(Bucket=bucket)
        print(f"버킷 '{bucket}' 생성 완료")
    
    # 파일 크기 확인
    file_size = os.path.getsize(local_path)
    print(f"업로드할 파일 크기: {file_size / (1024*1024):.2f} MB")
    
    # 업로드 (진행률 표시)
    with tqdm(total=file_size, unit='iB', unit_scale=True, desc='R2 업로드') as pbar:
        s3_client.upload_file(
            local_path,
            bucket,
            key,
            Callback=lambda bytes_transferred: pbar.update(bytes_transferred)
        )
    
    print(f"R2 업로드 완료!")
    print(f"R2 URL: {R2_ENDPOINT}/{bucket}/{key}")

def verify_r2_upload(bucket, key):
    """R2에 업로드된 파일 확인"""
    print(f"\n업로드 검증 중...")
    
    s3_client = boto3.client(
        's3',
        endpoint_url=R2_ENDPOINT,
        aws_access_key_id=R2_ACCESS_KEY,
        aws_secret_access_key=R2_SECRET_KEY,
        region_name='auto'
    )
    
    try:
        response = s3_client.head_object(Bucket=bucket, Key=key)
        file_size = response['ContentLength']
        print(f"✓ 검증 성공!")
        print(f"  - R2 파일 크기: {file_size / (1024*1024):.2f} MB")
        print(f"  - 최종 수정 시간: {response['LastModified']}")
        return True
    except ClientError as e:
        print(f"✗ 검증 실패: {e}")
        return False

def list_r2_contents(bucket):
    """R2 버킷 내용 확인"""
    print(f"\n버킷 '{bucket}' 내용 확인:")
    
    s3_client = boto3.client(
        's3',
        endpoint_url=R2_ENDPOINT,
        aws_access_key_id=R2_ACCESS_KEY,
        aws_secret_access_key=R2_SECRET_KEY,
        region_name='auto'
    )
    
    try:
        response = s3_client.list_objects_v2(Bucket=bucket)
        
        if 'Contents' in response:
            print(f"\n총 {len(response['Contents'])}개의 파일:")
            for obj in response['Contents']:
                size_mb = obj['Size'] / (1024*1024)
                print(f"  - {obj['Key']}: {size_mb:.2f} MB")
        else:
            print("  (버킷이 비어있습니다)")
    except ClientError as e:
        print(f"버킷 조회 실패: {e}")

def main():
    """메인 실행 함수"""
    print("=" * 60)
    print("SmolVLM 모델(+mmproj 선택) 다운로드 및 R2 업로드")
    print("=" * 60)
    
    # 로컬 디렉토리 생성
    os.makedirs(LOCAL_DIR, exist_ok=True)
    local_path = os.path.join(LOCAL_DIR, MODEL_FILENAME)
    mmproj_path = os.path.join(LOCAL_DIR, MMPROJ_FILENAME)
    
    # 1. 모델 다운로드 (이미 있으면 스킵)
    if os.path.exists(local_path):
        print(f"\n파일이 이미 존재합니다: {local_path}")
        file_size_mb = os.path.getsize(local_path) / (1024 * 1024)
        print(f"파일 크기: {file_size_mb:.2f} MB")
        
        user_input = input("\n다시 다운로드하시겠습니까? (y/N): ")
        if user_input.lower() != 'y':
            print("기존 파일 사용")
        else:
            download_file(MODEL_URL, local_path)
    else:
        download_file(MODEL_URL, local_path)
    
    # 1-2. (선택) mmproj 다운로드 시도
    try:
        download_file(MMPROJ_URL, mmproj_path)
    except Exception as e:
        print(f"(참고) mmproj 다운로드 실패 또는 불필요: {e}")
    
    # 2. R2에 업로드
    upload_to_r2(local_path, R2_BUCKET, R2_KEY)
    if os.path.exists(mmproj_path):
        try:
            upload_to_r2(mmproj_path, R2_BUCKET, R2_MMPROJ_KEY)
        except Exception as e:
            print(f"(참고) mmproj 업로드 실패: {e}")
    
    # 3. 업로드 검증
    verify_r2_upload(R2_BUCKET, R2_KEY)
    if os.path.exists(mmproj_path):
        verify_r2_upload(R2_BUCKET, R2_MMPROJ_KEY)
    
    # 4. 버킷 내용 확인
    list_r2_contents(R2_BUCKET)
    
    print("\n" + "=" * 60)
    print("작업 완료!")
    print("=" * 60)

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n작업이 취소되었습니다.")
        sys.exit(1)
    except Exception as e:
        print(f"\n오류 발생: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
