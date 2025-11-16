cmake_minimum_required(VERSION 3.22)
project(embedded_llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# Build options: keep minimal, but enable tools to get libmtmd
set(LLAMA_BUILD_COMMON   ON  CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
# 도구 전체 빌드를 비활성화하여 과도한 컴파일을 방지
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

# Avoid extra deps from common/
set(LLAMA_CURL    OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)

# Vulkan은 기본 비활성화(Gradle에서 -DGGML_VULKAN=ON으로 명시 시 활성화)
if(NOT DEFINED GGML_VULKAN)
  set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
endif()

# Prefer local llama.cpp under .work-llama, then fallback to module-local copy
set(LLAMA_LOCAL_DIR "")
set(_LLAMA_CANDIDATES
  "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.work-llama/llama.cpp" # tryon/.work-llama/llama.cpp
  "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp"                         # app/src/main/cpp/llama.cpp
)
foreach(_cand IN LISTS _LLAMA_CANDIDATES)
  if(EXISTS "${_cand}/CMakeLists.txt")
    set(LLAMA_LOCAL_DIR "${_cand}")
    break()
  endif()
endforeach()

if(LLAMA_LOCAL_DIR)
  message(STATUS "Using local llama.cpp at ${LLAMA_LOCAL_DIR}")
  add_subdirectory(${LLAMA_LOCAL_DIR} ${CMAKE_BINARY_DIR}/llama_local)
else()
  message(WARNING "Local llama.cpp not found. Falling back to FetchContent (network required).")
  set(LLAMA_REPO "https://github.com/ggerganov/llama.cpp")
  set(LLAMA_REF  "master")
  FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY ${LLAMA_REPO}
    GIT_TAG        ${LLAMA_REF}
    GIT_SHALLOW    TRUE
  )
  FetchContent_Populate(llama_cpp)
  add_subdirectory(${llama_cpp_SOURCE_DIR} ${CMAKE_BINARY_DIR}/llama_full)
endif()

# 최소 의존(ggml/llama)만 사용하고 도구 전체 빌드 없이 mtmd만 직접 구성
if(NOT TARGET mtmd)
  if(LLAMA_LOCAL_DIR)
    set(_MTMD_DIR ${LLAMA_LOCAL_DIR}/tools/mtmd)
  else()
    set(_MTMD_DIR ${llama_cpp_SOURCE_DIR}/tools/mtmd)
  endif()

  add_library(mtmd STATIC
    ${_MTMD_DIR}/mtmd.cpp
    ${_MTMD_DIR}/mtmd-audio.cpp
    ${_MTMD_DIR}/clip.cpp
    ${_MTMD_DIR}/mtmd-helper.cpp
  )
  target_include_directories(mtmd PUBLIC
    ${_MTMD_DIR}
  )
  if(LLAMA_LOCAL_DIR)
    target_include_directories(mtmd PRIVATE ${LLAMA_LOCAL_DIR} ${LLAMA_LOCAL_DIR}/vendor)
  else()
    target_include_directories(mtmd PRIVATE ${llama_cpp_SOURCE_DIR} ${llama_cpp_SOURCE_DIR}/vendor)
  endif()

  # ggml / llama 집합 타깃 명칭은 버전에 따라 다를 수 있으므로 가급적 기본 이름 우선
  if(TARGET ggml)
    target_link_libraries(mtmd PUBLIC ggml)
  elseif(TARGET ggml_static)
    target_link_libraries(mtmd PUBLIC ggml_static)
  endif()
  if(TARGET llama)
    target_link_libraries(mtmd PUBLIC llama)
  elseif(TARGET llama_static)
    target_link_libraries(mtmd PUBLIC llama_static)
  endif()

  find_package(Threads REQUIRED)
  target_link_libraries(mtmd PRIVATE Threads::Threads)
  target_compile_features(mtmd PRIVATE cxx_std_17)
endif()

# JNI library providing direct multimodal inference
add_library(embedded_server_jni SHARED
  EmbeddedServerJni.cpp
)

# Some llama.cpp revisions export the mtmd target as llama_mtmd - alias if needed
if(NOT TARGET mtmd)
  if(TARGET llama_mtmd)
    add_library(mtmd ALIAS llama_mtmd)
  endif()
endif()

# Ensure headers are resolvable for includes: "llama.h", "mtmd.h", "mtmd-helper.h"
if(LLAMA_LOCAL_DIR)
  target_include_directories(embedded_server_jni PRIVATE
    ${LLAMA_LOCAL_DIR}/include
    ${LLAMA_LOCAL_DIR}/tools/mtmd
  )
else()
  target_include_directories(embedded_server_jni PRIVATE
    ${llama_cpp_SOURCE_DIR}/include
    ${llama_cpp_SOURCE_DIR}/tools/mtmd
  )
endif()
target_include_directories(embedded_server_jni PRIVATE
  ${CMAKE_CURRENT_SOURCE_DIR}
)

# Link against ggml, llama, and mtmd (names may vary by commit)
if(TARGET ggml)
  target_link_libraries(embedded_server_jni PRIVATE ggml)
elseif(TARGET ggml_static)
  target_link_libraries(embedded_server_jni PRIVATE ggml_static)
else()
  message(WARNING "Target 'ggml' not found (or named differently).")
endif()

if(TARGET llama)
  target_link_libraries(embedded_server_jni PRIVATE llama)
elseif(TARGET llama_static)
  target_link_libraries(embedded_server_jni PRIVATE llama_static)
else()
  message(WARNING "Target 'llama' not found (or named differently).")
endif()

if(TARGET mtmd)
  target_link_libraries(embedded_server_jni PRIVATE mtmd)
else()
  message(FATAL_ERROR "Multimodal library target 'mtmd' not found (should be generated minimally above).")
endif()

# Android logcat
find_library(ANDROID_LOG_LIB log)
if(ANDROID_LOG_LIB)
  target_link_libraries(embedded_server_jni PRIVATE ${ANDROID_LOG_LIB})
else()
  target_link_libraries(embedded_server_jni PRIVATE log)
endif()

# Definitions improving Android build compatibility and avoiding libcurl/ssl references
target_compile_definitions(embedded_server_jni PRIVATE
  LLAMA_CURL=0
)
if(GGML_VULKAN)
  target_compile_definitions(embedded_server_jni PRIVATE GGML_VULKAN=1)
endif()

# Ensure position-independent code
set_property(TARGET embedded_server_jni PROPERTY POSITION_INDEPENDENT_CODE ON)
target_compile_features(embedded_server_jni PRIVATE cxx_std_17)
