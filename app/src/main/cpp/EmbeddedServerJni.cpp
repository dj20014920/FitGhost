// Direct multimodal inference via llama.cpp + libmtmd (no HTTP, no local server)

#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include <android/log.h>
#include <mutex>
#include <cctype>
#include <cstring>
#include <time.h>

#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define LOG_TAG "EmbeddedLlamaJNI"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model   * g_model = nullptr;
static llama_context * g_ctx   = nullptr;
static mtmd_context  * g_mtmd  = nullptr;
static int g_n_threads = 2;
static int g_n_batch   = 512;
static std::string g_chat_tmpl;
static std::mutex g_mutex; // serialize access to the single global ctx/model

// GBNF grammar: Force a single JSON object with fixed keys and simple string values
// Keys: category (enum), name, color, detailType, pattern, brand, tags (array of string), description
// Note: Keep grammar minimal for performance; disallow numbers/booleans/null.
static const char * FG_JSON_GRAMMAR = R"GBNF(
root        ::= ws object ws

object      ::= "{" ws
                 '"category"' ws ":" ws category ws "," ws
                 '"name"' ws ":" ws string ws "," ws
                 '"color"' ws ":" ws string ws "," ws
                 '"detailType"' ws ":" ws string ws "," ws
                 '"pattern"' ws ":" ws string ws "," ws
                 '"brand"' ws ":" ws string ws "," ws
                 '"tags"' ws ":" ws array ws "," ws
                 '"description"' ws ":" ws string ws
                 "}"

category    ::= '"TOP"' | '"BOTTOM"' | '"OUTER"' | '"SHOES"' | '"ACCESSORY"' | '"OTHER"'

array       ::= "[" ws (string (ws "," ws string)*)? ws "]"

string      ::= '"' characters '"'
characters  ::= (char)*
char        ::= [^"\\\x00-\x1F] | escape
escape      ::= '\\' (["\\/bfnrt] | 'u' hex hex hex hex)
hex         ::= [0-9a-fA-F]

ws          ::= ([ \t\n\r])*
)GBNF";

static inline int64_t now_ms() {
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static bool json_complete(const std::string &s) {
    // Check if a top-level JSON object or array is complete (balanced braces/brackets), ignoring whitespace
    size_t i = 0, n = s.size();
    while (i < n && std::isspace((unsigned char)s[i])) ++i;
    if (i >= n) return false;
    char start = s[i];
    if (start != '{' && start != '[') return false; // expect object/array
    int depth = 0; bool in_str = false; bool esc = false;
    for (; i < n; ++i) {
        char c = s[i];
        if (in_str) {
            if (esc) { esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') { in_str = false; continue; }
        } else {
            if (c == '"') { in_str = true; continue; }
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    // ensure the rest is only whitespace
                    size_t j = i + 1;
                    while (j < n && std::isspace((unsigned char)s[j])) ++j;
                    return j == n;
                }
            }
        }
    }
    return false;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_fitghost_app_ai_EmbeddedLlamaServer_nativeInit(
        JNIEnv* env, jclass,
        jstring jModelPath, jstring jMmprojPath,
        jstring jChatTemplate, jint jCtx, jint jThreads) {
    if (g_model && g_ctx && g_mtmd) return JNI_TRUE;
    const char* model = env->GetStringUTFChars(jModelPath, nullptr);
    const char* mmproj = jMmprojPath ? env->GetStringUTFChars(jMmprojPath, nullptr) : nullptr;
    const char* tmpl = jChatTemplate ? env->GetStringUTFChars(jChatTemplate, nullptr) : nullptr;
    g_chat_tmpl = tmpl ? std::string(tmpl) : std::string("");
    int ctx_len = (int) jCtx;
    g_n_threads = (int) jThreads;

    llama_backend_init();
    llama_model_params mparams = llama_model_default_params();
    // 모바일 안정성 우선: 기본 CPU 경로 유지 (필요 시 ggml 오프로딩은 빌드 플래그로 제어)
    mparams.n_gpu_layers = 0;

    g_model = llama_model_load_from_file(model, mparams);
    if (!g_model) {
        ALOGE("Failed to load model: %s", model);
        if (jModelPath) env->ReleaseStringUTFChars(jModelPath, model);
        if (jMmprojPath && mmproj) env->ReleaseStringUTFChars(jMmprojPath, mmproj);
        if (jChatTemplate && tmpl) env->ReleaseStringUTFChars(jChatTemplate, tmpl);
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = ctx_len > 0 ? ctx_len : 2048;
    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        ALOGE("Failed to create llama context");
        llama_model_free(g_model); g_model = nullptr;
        if (jModelPath) env->ReleaseStringUTFChars(jModelPath, model);
        if (jMmprojPath && mmproj) env->ReleaseStringUTFChars(jMmprojPath, mmproj);
        if (jChatTemplate && tmpl) env->ReleaseStringUTFChars(jChatTemplate, tmpl);
        return JNI_FALSE;
    }

    // 쓰레드 설정 (단일 토큰과 배치 동일 적용)
    llama_set_n_threads(g_ctx, g_n_threads, g_n_threads);

    // 멀티모달 초기화 (mmproj가 있을 때만)
    if (mmproj && std::strlen(mmproj) > 0) {
        mtmd_context_params mp = mtmd_context_params_default();
        mp.use_gpu = false; // Vulkan 기본 OFF
        mp.print_timings = false;
        mp.n_threads = g_n_threads;
        mp.verbosity = GGML_LOG_LEVEL_WARN;
        mp.media_marker = mtmd_default_marker();
        g_mtmd = mtmd_init_from_file(mmproj, g_model, mp);
        if (!g_mtmd) {
            ALOGW("mtmd_init_from_file failed (vision disabled): %s", mmproj);
        }
    }

    if (jModelPath) env->ReleaseStringUTFChars(jModelPath, model);
    if (jMmprojPath && mmproj) env->ReleaseStringUTFChars(jMmprojPath, mmproj);
    if (jChatTemplate && tmpl) env->ReleaseStringUTFChars(jChatTemplate, tmpl);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_fitghost_app_ai_EmbeddedLlamaServer_nativeStop(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> _l(g_mutex);
    if (g_mtmd) { mtmd_free(g_mtmd); g_mtmd = nullptr; }
    if (g_ctx)  { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model){ llama_model_free(g_model); g_model = nullptr; }
    llama_backend_free();
}

static std::string token_to_piece(const llama_vocab * vocab, llama_token tok) {
    std::string out;
    char buf[512];
    int32_t n = llama_token_to_piece(vocab, tok, buf, sizeof(buf), /*lstrip*/0, /*special*/true);
    if (n > 0) out.assign(buf, buf + std::min<int32_t>(n, (int32_t)sizeof(buf)));
    return out;
}

// prompt_already_evaluated = true 인 경우, 현재 KV/로그릿 상태에서 바로 샘플링을 이어감
// n_past는 현재까지 컨텍스트 길이(다음 토큰이 들어갈 절대 위치)
static std::string generate_greedy_json(
        const std::string & prompt,
        int max_tokens,
        double /*temp*/,
        llama_pos n_past,
        bool prompt_already_evaluated)
{
    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    // 프롬프트가 아직 평가되지 않았다면, 프롬프트를 토크나이즈하여 pos = n_past .. n_past + n - 1 로 디코드
    if (!prompt_already_evaluated) {
        int32_t need = llama_tokenize(vocab, prompt.c_str(), (int32_t)prompt.size(), nullptr, 0, /*add_special*/true, /*parse_special*/true);
        if (need < 0) need = -need;
        std::vector<llama_token> toks((size_t)need);
        int32_t n = llama_tokenize(vocab, prompt.c_str(), (int32_t)prompt.size(), toks.data(), (int32_t)toks.size(), /*add_special*/true, /*parse_special*/true);
        if (n < 0) return std::string("{}");

        struct llama_batch b = llama_batch_get_one(toks.data(), n);
        // 절대 위치 보정
        for (int i = 0; i < n; ++i) b.pos[i] = n_past + i;
        if (llama_decode(g_ctx, b) != 0) return std::string("{}");
        n_past += n;
    }

    std::string out;
    out.reserve((size_t)max_tokens * 4);

    for (int i = 0; i < max_tokens; ++i) {
        float * logits = llama_get_logits(g_ctx);
        if (!logits) break;
        const int32_t n_vocab = llama_vocab_n_tokens(vocab);
        // argmax 샘플링 (JSON 안정성을 위해 탐욕적)
        int32_t best = 0; float best_logit = logits[0];
        for (int32_t t = 1; t < n_vocab; ++t) {
            float v = logits[t];
            if (v > best_logit) { best_logit = v; best = t; }
        }

        if (llama_vocab_is_eog(vocab, best)) break;

        out += token_to_piece(vocab, best);
        if (json_complete(out)) break;

        // 다음 토큰을 올바른 절대 위치에 디코드
        llama_token ntok = best;
        struct llama_batch nb = llama_batch_get_one(&ntok, 1);
        nb.pos[0] = n_past; // 중요: pos를 누적 길이로 설정
        if (llama_decode(g_ctx, nb) != 0) break;
        n_past += 1;
    }
    return out;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_fitghost_app_ai_EmbeddedLlamaServer_nativeAnalyze(
        JNIEnv* env, jclass,
        jstring jSystemPrompt,
        jstring jUserText,
        jbyteArray jImagePng,
        jdouble jTemperature,
        jint jMaxTokens) {
    std::lock_guard<std::mutex> _l(g_mutex);
    if (!g_model || !g_ctx) {
        return env->NewStringUTF("{}");
    }

    const char * sys = jSystemPrompt ? env->GetStringUTFChars(jSystemPrompt, nullptr) : "";
    const char * usr = jUserText     ? env->GetStringUTFChars(jUserText, nullptr)     : "";
    const char * marker = mtmd_default_marker();
    std::string prompt = std::string(sys) + "\n\n" + usr;

    // 새 요청마다 KV 메모리 초기화 (동일 seq_id에서 pos=0 재사용 시 충돌 방지)
    // 최신 llama API: context -> memory 핸들을 얻어 clear 호출
    llama_memory_t mem = llama_get_memory(g_ctx);
    if (mem) {
        llama_memory_clear(mem, /*data=*/true);
    }

    // 멀티모달 경로 (PNG 버퍼 → bitmap → 토크나이즈 → eval)
    bool used_mm = false;
    llama_pos n_past = 0;
    if (g_mtmd && jImagePng) {
        jsize len = env->GetArrayLength(jImagePng);
        if (len > 0) {
            jbyte * bytes = env->GetByteArrayElements(jImagePng, nullptr);
            mtmd_bitmap * bmp = mtmd_helper_bitmap_init_from_buf(g_mtmd, (const unsigned char*)bytes, (size_t)len);
            if (bmp) {
                // 프롬프트에 미디어 마커가 없으면 하나 추가
                if (prompt.find(marker) == std::string::npos) {
                    prompt += "\n";
                    prompt += marker;
                }

                mtmd_input_text txt;
                txt.text = prompt.c_str();
                txt.add_special = true;
                txt.parse_special = true;

                mtmd_input_chunks * chunks = mtmd_input_chunks_init();
                const mtmd_bitmap * images[1] = { bmp };
                int32_t tr = mtmd_tokenize(g_mtmd, chunks, &txt, images, 1);
                if (tr == 0) {
                    llama_pos new_n_past = 0;
                    if (mtmd_helper_eval_chunks(g_mtmd, g_ctx, chunks, /*n_past*/0, /*seq_id*/0, g_n_batch, /*logits_last*/true, &new_n_past) == 0) {
                        used_mm = true;
                        n_past = new_n_past; // 이어서 생성 시 사용할 시작 위치
                    }
                }
                mtmd_bitmap_free(bmp);
                mtmd_input_chunks_free(chunks);
            }
            env->ReleaseByteArrayElements(jImagePng, bytes, JNI_ABORT);
        }
    }

    std::string result;
    if (used_mm) {
        // 비전 토큰 평가를 마친 상태에서 이어서 생성
        result = generate_greedy_json("", (int)jMaxTokens, (double)jTemperature, n_past, /*prompt_already_evaluated=*/true);
    } else {
        // 텍스트 전용 경로: 프롬프트부터 평가
        result = generate_greedy_json(prompt, (int)jMaxTokens, (double)jTemperature, /*n_past*/0, /*prompt_already_evaluated=*/false);
    }

    if (jSystemPrompt) env->ReleaseStringUTFChars(jSystemPrompt, sys);
    if (jUserText)     env->ReleaseStringUTFChars(jUserText, usr);
    if (result.empty()) result = "{}";
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_fitghost_app_ai_EmbeddedLlamaServer_nativeIsAlive(JNIEnv*, jclass) {
    return (g_model && g_ctx) ? JNI_TRUE : JNI_FALSE;
}
