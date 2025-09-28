#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
OUTDIR="$ROOT/screens"
mkdir -p "$OUTDIR"

dump() {
  adb shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window_dump.xml "$ROOT/window_dump.xml" >/dev/null 2>&1 || true
}

screencap() {
  local name="$1"
  adb exec-out screencap -p > "$OUTDIR/$name" 2>/dev/null || true
}

# Dynamically compute a reasonable min-y threshold to avoid top app bar hits
compute_miny() {
  # expects a fresh dump already present at $ROOT/window_dump.xml
  local txy
  txy=$(python3 parse_ui.py coords-parent --text "추천" window_dump.xml || true)
  if [[ -z "${txy}" ]]; then
    txy=$(python3 parse_ui.py coords-parent --text "검색" window_dump.xml || true)
  fi
  if [[ -z "${txy}" ]]; then
    txy=$(python3 parse_ui.py coords-parent --text "위시리스트" window_dump.xml || true)
  fi
  # Start with a conservative baseline that still excludes the top app bar
  local TY=800
  if [[ -n "${txy}" ]]; then
    local TX TY_TMP
    read TX TY_TMP <<< "${txy}"
    # push interaction area just below the tab row
    TY=$((TY_TMP + 50))
  fi
  # clamp to a sane minimum in case of parsing issues
  if [[ ${TY} -lt 800 ]]; then TY=800; fi
  MINY=${TY}
  export MINY
  echo "MINY=${MINY}"
}

ensure_shop() {
  local i=0
  while [[ $i -lt 3 ]]; do
    dump
    local has_title=$(python3 parse_ui.py count --text "FitGhost Shop" window_dump.xml || true)
    local c_search=$(python3 parse_ui.py count --text "검색" window_dump.xml || true)
    local c_reco=$(python3 parse_ui.py count --text "추천" window_dump.xml || true)
    local c_wish=$(python3 parse_ui.py count --text "위시리스트" window_dump.xml || true)
    if [[ ${has_title} -gt 0 || $((c_search + c_reco + c_wish)) -gt 0 ]]; then
      return 0
    fi
    local in_cart=$(python3 parse_ui.py count --text "장바구니" window_dump.xml || true)
    local has_back=$(python3 parse_ui.py count --content-desc "뒤로가기" window_dump.xml || true)
    if [[ ${in_cart} -gt 0 && ${has_back} -gt 0 ]]; then
      local bxby
      bxby=$(python3 parse_ui.py coords-parent --content-desc "뒤로가기" window_dump.xml || true)
      if [[ -n "${bxby}" ]]; then
        read BX BY <<< "${bxby}"
        adb shell input tap "${BX}" "${BY}"
        sleep 0.7
        i=$((i+1))
        continue
      fi
    fi
    # Try tapping bottom nav 상점 (prefer content-desc since labels show only for selected item)
    local shopxy
    shopxy=$(python3 parse_ui.py coords-parent --content-desc "상점" window_dump.xml || true)
    if [[ -z "${shopxy}" ]]; then
      shopxy=$(python3 parse_ui.py coords-parent --text "상점" window_dump.xml || true)
    fi
    if [[ -n "${shopxy}" ]]; then
      read SX SY <<< "${shopxy}"
      adb shell input tap "${SX}" "${SY}"
    else
      # Fallback: tap near the Gallery icon's X minus an offset (Shop is left of Gallery)
      local galxy
      galxy=$(python3 parse_ui.py coords-parent --content-desc "갤러리" window_dump.xml || true)
      if [[ -n "${galxy}" ]]; then
        read GX GY <<< "${galxy}"
        adb shell input tap "$((GX-200))" "${GY}" || true
      else
        # Last resort hardcoded coords (approx center-right bottom)
        adb shell input tap 980 2688 || true
      fi
    fi
    sleep 0.7
    i=$((i+1))
  done
  return 0
}

search_and_try_add_to_cart() {
  # Switch to 검색 tab
  local search_tab
  search_tab=$(python3 parse_ui.py coords-parent --text "검색" window_dump.xml || true)
  if [[ -n "${search_tab}" ]]; then
    read TX TY <<< "${search_tab}"
    adb shell input tap "${TX}" "${TY}"
    sleep 0.5
  fi
  dump
  compute_miny
  
  # Focus search field via placeholder coords or EditText
  local sxy
  sxy=$(python3 parse_ui.py coords-parent --text "상품명, 태그로 검색" window_dump.xml || true)
  if [[ -z "${sxy}" ]]; then
    # Try to find EditText directly
    sxy=$(python3 parse_ui.py coords --class "android.widget.EditText" window_dump.xml || true)
  fi
  
  if [[ -n "${sxy}" ]]; then
    read SX SY <<< "${sxy}"
    adb shell input tap "${SX}" "${SY}"
    sleep 0.5
    
    # 더 강력한 텍스트 클리어링 방법
    echo "기존 텍스트 클리어링 중..."
    
    # 방법 1: 전체 선택 후 삭제 (여러 번 시도)
    for i in {1..5}; do
      adb shell input keyevent 29 || true  # CTRL+A
      sleep 0.3
      adb shell input keyevent 67 || true  # DEL
      sleep 0.3
    done
    
    # 방법 2: 백스페이스로 강제 삭제
    for i in {1..15}; do
      adb shell input keyevent 67 || true  # DEL
      sleep 0.1
    done
    
    # 방법 3: 홈으로 이동 후 전체 선택 삭제
    adb shell input keyevent 122 || true  # MOVE_HOME
    sleep 0.5
    adb shell input keyevent 29 || true   # CTRL+A
    sleep 0.5
    adb shell input keyevent 67 || true   # DEL
    sleep 0.5
    
    # 방법 4: 빈 텍스트 입력으로 클리어 시도
    adb shell input text "" || true
    sleep 0.5
    
    echo "새 검색어 입력: knit"
    adb shell input text 'knit' || true
    sleep 0.5
    adb shell input keyevent 66 || true  # ENTER
    sleep 4.0  # 검색 결과 로딩 대기 시간 증가
  fi
  
  # Attempt to find product card cart icon
  local attempt=0
  local added=0
  while [[ ${attempt} -lt 5 && ${added} -eq 0 ]]; do
    dump
    local coords
    coords=$(python3 parse_ui.py coords-parent --content-desc "장바구니" --min-y ${MINY} window_dump.xml || true)
    if [[ -n "${coords}" ]]; then
      read CX CY <<< "${coords}"
      echo "Tapping product cart at ${CX} ${CY}"
      adb shell input tap "${CX}" "${CY}"
      sleep 1.2
      added=1
      break
    else
      # Fallback: try button text "담기"
      coords=$(python3 parse_ui.py coords-parent --text "담기" --min-y ${MINY} window_dump.xml || true)
      if [[ -n "${coords}" ]]; then
        read CX CY <<< "${coords}"
        echo "Tapping add-to-cart (text) at ${CX} ${CY}"
        adb shell input tap "${CX}" "${CY}"
        sleep 1.2
        added=1
        break
      else
        echo "No product cart button found, scrolling"
        adb shell input swipe 600 1600 600 600 300 || true
        sleep 0.6
        attempt=$((attempt+1))
      fi
    fi
  done
  dump
  local cnt
  cnt=$(python3 parse_ui.py count --text "장바구니에 추가되었습니다." window_dump.xml || true)
  echo "SNACKBAR_ADD_TO_CART_COUNT=${cnt}"
  screencap "snackbar_add_to_cart.png"
}

wishlist_toggle_try() {
  # Try to find 찜하기 button and toggle
  local toggled=0
  local attempt=0
  while [[ ${attempt} -lt 5 && ${toggled} -eq 0 ]]; do
    dump
    compute_miny
    local wxy
    wxy=$(python3 parse_ui.py coords-parent --content-desc "찜하기" --min-y ${MINY} window_dump.xml || true)
    if [[ -n "${wxy}" ]]; then
      read WX WY <<< "${wxy}"
      echo "Tapping wishlist at ${WX} ${WY}"
      adb shell input tap "${WX}" "${WY}"
      sleep 1.0
      toggled=1
      break
    else
      echo "No wishlist button found, scrolling"
      adb shell input swipe 600 1600 600 600 300 || true
      sleep 0.6
      attempt=$((attempt+1))
    fi
  done
  dump
  local addc removec
  addc=$(python3 parse_ui.py count --text "위시리스트에 추가되었습니다." window_dump.xml || true)
  removec=$(python3 parse_ui.py count --text "위시리스트에서 제거되었습니다." window_dump.xml || true)
  echo "WISHLIST_SNACKBAR_ADD=${addc} REMOVE=${removec}"
  screencap "snackbar_wishlist.png"
}

recommend_try_add_to_cart() {
  # Try to add to cart directly from 추천 tab
  local attempt=0
  local added=0
  while [[ ${attempt} -lt 5 && ${added} -eq 0 ]]; do
    dump
    compute_miny
    local coords
    coords=$(python3 parse_ui.py coords-parent --content-desc "장바구니" --min-y ${MINY} window_dump.xml || true)
    if [[ -n "${coords}" ]]; then
      read CX CY <<< "${coords}"
      echo "[추천] Tapping cart at ${CX} ${CY}"
      adb shell input tap "${CX}" "${CY}"
      sleep 1.2
      added=1
      break
    else
      # Fallback: try button text "담기"
      coords=$(python3 parse_ui.py coords-parent --text "담기" --min-y ${MINY} window_dump.xml || true)
      if [[ -n "${coords}" ]]; then
        read CX CY <<< "${coords}"
        echo "[추천] Tapping add-to-cart (text) at ${CX} ${CY}"
        adb shell input tap "${CX}" "${CY}"
        sleep 1.2
        added=1
        break
      else
        echo "[추천] No cart button found, scrolling"
        adb shell input swipe 600 1600 600 600 300 || true
        sleep 0.6
        attempt=$((attempt+1))
      fi
    fi
  done
  dump
  local cnt
  cnt=$(python3 parse_ui.py count --text "장바구니에 추가되었습니다." window_dump.xml || true)
  echo "REC_SNACKBAR_ADD_TO_CART_COUNT=${cnt}"
  screencap "rec_snackbar_add_to_cart.png"
}

recommend_toggle_wishlist() {
  # Try to toggle wishlist directly from 추천 tab
  local attempt=0
  local toggled=0
  while [[ ${attempt} -lt 5 && ${toggled} -eq 0 ]]; do
    dump
    compute_miny
    local wxy
    wxy=$(python3 parse_ui.py coords-parent --content-desc "찜하기" --min-y ${MINY} window_dump.xml || true)
    if [[ -n "${wxy}" ]]; then
      read WX WY <<< "${wxy}"
      echo "[추천] Tapping wishlist at ${WX} ${WY}"
      adb shell input tap "${WX}" "${WY}"
      sleep 1.0
      toggled=1
      break
    else
      echo "[추천] No wishlist button found, scrolling"
      adb shell input swipe 600 1600 600 600 300 || true
      sleep 0.6
      attempt=$((attempt+1))
    fi
  done
  dump
  local addc removec
  addc=$(python3 parse_ui.py count --text "위시리스트에 추가되었습니다." window_dump.xml || true)
  removec=$(python3 parse_ui.py count --text "위시리스트에서 제거되었습니다." window_dump.xml || true)
  echo "REC_WISHLIST_SNACKBAR_ADD=${addc} REMOVE=${removec}"
  screencap "rec_snackbar_wishlist.png"
}

# 0) Ensure Shop tab (handling cart screen/back navigation)
ensure_shop

dump

# 1) Verify tab labels present
c_search=$(python3 parse_ui.py count --text "검색" window_dump.xml || true)
c_reco=$(python3 parse_ui.py count --text "추천" window_dump.xml || true)
c_wish=$(python3 parse_ui.py count --text "위시리스트" window_dump.xml || true)
echo "TAB_COUNTS search=$c_search recommend=$c_reco wishlist=$c_wish"

# 2) Switch to 추천 and verify placeholder disappearance
coords=$(python3 parse_ui.py coords-parent --text "추천" window_dump.xml || true)
if [[ -n "${coords}" ]]; then
  read RX RY <<< "${coords}"
  adb shell input tap "$RX" "$RY"
  sleep 0.8
fi

dump
compute_miny
pc=$(python3 parse_ui.py count --text "상품명, 태그로 검색" window_dump.xml || true)
echo "PLACEHOLDER_ON_추천=${pc}"
screencap "tab_recommendations.png"

# 2.5) In 추천 tab, try add-to-cart and wishlist toggle directly
recommend_try_add_to_cart
recommend_toggle_wishlist

# 3) Switch back to 검색 and verify placeholder appears
coords=$(python3 parse_ui.py coords-parent --text "검색" window_dump.xml || true)
if [[ -n "${coords}" ]]; then
  read SX SY <<< "${coords}"
  adb shell input tap "$SX" "$SY"
  sleep 0.8
fi

dump
compute_miny
pc2=$(python3 parse_ui.py count --text "상품명, 태그로 검색" window_dump.xml || true)
echo "PLACEHOLDER_ON_검색=${pc2}"
screencap "tab_search.png"

# 4) Try search and add-to-cart (fallback path)
search_and_try_add_to_cart

# 5) Switch to 위시리스트 tab and screenshot
coords=$(python3 parse_ui.py coords-parent --text "위시리스트" window_dump.xml || true)
if [[ -n "${coords}" ]]; then
  read LX LY <<< "${coords}"
  adb shell input tap "${LX}" "${LY}"
  sleep 0.8
fi

dump
wl_empty=$(python3 parse_ui.py count --text "위시리스트가 비어 있습니다." window_dump.xml || true)
echo "WISHLIST_EMPTY_TEXT_COUNT=${wl_empty}"
screencap "tab_wishlist.png"

# 6) Try wishlist toggle on product card (in 검색 tab)
coords=$(python3 parse_ui.py coords-parent --text "검색" window_dump.xml || true)
if [[ -n "${coords}" ]]; then
  read SX SY <<< "${coords}"
  adb shell input tap "$SX" "$SY"
  sleep 0.6
fi
wishlist_toggle_try