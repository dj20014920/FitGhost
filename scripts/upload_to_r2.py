#!/usr/bin/env python3
"""
모델을 Cloudflare R2에 업로드
"""

import os
import sys
import boto3
from botocore.exceptions import ClientError

# Cloudflare R2 설정 (환경변수에서 주입)
R2_ENDPOINT = os.getenv("R2_ENDPOINT", "https://081a9810680543ee912eb54ae15876a3.r2.cloudflarestorage.com")
R2_ACCESS_KEY = os.getenv("AWS_ACCESS_KEY_ID")
R2_SECRET_KEY = os.getenv("AWS_SECRET_ACCESS_KEY")
R2_BUCKET = os.getenv("R2_BUCKET", "ghostfit-models")

if not R2_ACCESS_KEY or not R2_SECRET_KEY:
    print("오류: AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY 환경변수가 필요합니다.")
    sys.exit(1)

def upload_to_r2(local_path, r2_key):
    """Cloudflare R2에 파일 업로드"""
    print("=" * 60)
    print("Cloudflare R2 업로드")
    print("=" * 60)
    print(f"\n로컬 파일: {local_path}")
    print(f"R2 버킷: {R2_BUCKET}")
    print(f"R2 키: {r2_key}")
    
    if not os.path.exists(local_path):
        print(f"\n오류: 파일을 찾을 수 없습니다: {local_path}")
        sys.exit(1)
    
    file_size_mb = os.path.getsize(local_path) / (1024 * 1024)
    print(f"파일 크기: {file_size_mb:.2f} MB")
    
    # S3 클라이언트 생성
    s3_client = boto3.client(
        's3',
        endpoint_url=R2_ENDPOINT,
        aws_access_key_id=R2_ACCESS_KEY,
        aws_secret_access_key=R2_SECRET_KEY,
        region_name='auto'
    )
    
    # 버킷 존재 확인 및 생성
    try:
        s3_client.head_bucket(Bucket=R2_BUCKET)
        print(f"\n✓ 버킷 '{R2_BUCKET}' 확인됨")
    except ClientError:
        print(f"\n버킷 '{R2_BUCKET}' 생성 중...")
        try:
            s3_client.create_bucket(Bucket=R2_BUCKET)
            print(f"✓ 버킷 '{R2_BUCKET}' 생성 완료")
        except ClientError as e:
            print(f"버킷 생성 실패: {e}")
            # 버킷이 이미 있다면 계속 진행
            pass
    
    # 파일 업로드
    print(f"\n업로드 시작...")
    try:
        # 진행 상황을 표시하는 콜백
        def progress_callback(bytes_transferred):
            current_mb = bytes_transferred / (1024 * 1024)
            percentage = (bytes_transferred / os.path.getsize(local_path)) * 100
            print(f"\r업로드 진행: {current_mb:.2f} MB / {file_size_mb:.2f} MB ({percentage:.1f}%)", end='')
        
        s3_client.upload_file(
            local_path,
            R2_BUCKET,
            r2_key,
            Callback=progress_callback
        )
        
        print(f"\n\n✓ R2 업로드 완료!")
        print(f"R2 경로: {R2_BUCKET}/{r2_key}")
        
    except ClientError as e:
        print(f"\n\n업로드 실패: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"\n\n오류 발생: {e}")
        sys.exit(1)

def verify_upload(r2_key):
    """업로드된 파일 검증"""
    print("\n" + "=" * 60)
    print("업로드 검증")
    print("=" * 60)
    
    s3_client = boto3.client(
        's3',
        endpoint_url=R2_ENDPOINT,
        aws_access_key_id=R2_ACCESS_KEY,
        aws_secret_access_key=R2_SECRET_KEY,
        region_name='auto'
    )
    
    try:
        response = s3_client.head_object(Bucket=R2_BUCKET, Key=r2_key)
        file_size_mb = response['ContentLength'] / (1024 * 1024)
        print(f"\n✓ 검증 성공!")
        print(f"  - R2 파일: {r2_key}")
        print(f"  - 파일 크기: {file_size_mb:.2f} MB")
        print(f"  - 최종 수정: {response['LastModified']}")
        return True
    except ClientError as e:
        print(f"\n✗ 검증 실패: {e}")
        return False

def list_bucket_contents():
    """버킷 내용 확인"""
    print("\n" + "=" * 60)
    print(f"버킷 '{R2_BUCKET}' 전체 내용")
    print("=" * 60)
    
    s3_client = boto3.client(
        's3',
        endpoint_url=R2_ENDPOINT,
        aws_access_key_id=R2_ACCESS_KEY,
        aws_secret_access_key=R2_SECRET_KEY,
        region_name='auto'
    )
    
    try:
        response = s3_client.list_objects_v2(Bucket=R2_BUCKET)
        
        if 'Contents' in response:
            print(f"\n총 {len(response['Contents'])}개의 파일:\n")
            
            # models 폴더와 ghostfit_models 폴더로 구분
            models_files = []
            ghostfit_files = []
            other_files = []

            for obj in response['Contents']:
                size_mb = obj['Size'] / (1024 * 1024)
                if obj['Key'].startswith('models/'):
                    models_files.append((obj['Key'], size_mb))
                elif obj['Key'].startswith('ghostfit_models/'):
                    ghostfit_files.append((obj['Key'], size_mb))
                else:
                    other_files.append((obj['Key'], size_mb))

            if models_files:
                print("[기존 models 폴더]")
                for key, size in models_files:
                    print(f"  - {key}: {size:.2f} MB")

            if ghostfit_files:
                print("\n[ghostfit_models 폴더]")
                for key, size in ghostfit_files:
                    print(f"  - {key}: {size:.2f} MB")
            
            if other_files:
                print("\n[루트 파일]")
                for key, size in other_files:
                    print(f"  - {key}: {size:.2f} MB")
                    print(f"     퍼블릭 URL: https://pub-411b7feaa5b7440786580c2747a9129f.r2.dev/{key}")
                    
            print(f"\n총 파일 수: {len(response['Contents'])}개")
        else:
            print("\n(버킷이 비어있습니다)")
            
    except ClientError as e:
        print(f"\n버킷 조회 실패: {e}")

def main():
    """메인 함수"""
    if len(sys.argv) < 2:
        print("사용법: python3 upload_to_r2.py <로컬_파일_경로> [R2_키]")
        sys.exit(1)
    
    local_path = sys.argv[1]
    
    # R2 키 결정 (인자로 받거나 파일명 기반)
    if len(sys.argv) >= 3:
        r2_key = sys.argv[2]
    else:
        filename = os.path.basename(local_path)
        # 버킷 루트에 직접 배치
        r2_key = filename
    
    # 업로드
    upload_to_r2(local_path, r2_key)
    
    # 검증
    verify_upload(r2_key)
    
    # 버킷 전체 내용 확인
    list_bucket_contents()
    
    print("\n" + "=" * 60)
    print("모든 작업 완료!")
    print("=" * 60)

if __name__ == "__main__":
    main()
