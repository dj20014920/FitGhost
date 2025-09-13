package com.fitghost.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * TryOnBridge
 *
 * 목적
 * - Home(추천 코디) 등 다른 화면에서 Try-On으로 이동할 때, "어느 부위를 우선 피팅할지"에 대한 의도를
 *   안전하게 넘겨주기 위한 경량 브리지.
 * - Try-On 화면은 이 브리지를 조회/소비하여 초기 세그먼트(part)를 설정하고, 필요 시 포토 피커를 즉시 실행(instant flow)하며, 추천 아이템 이미지 URI(Optional)도 전달할 수 있다.
 *
 * 설계 원칙
 * - KISS/DRY: 단일 전역 진입점, 불필요한 상태 복제 금지.
 * - YAGNI: 현재 요구인 "부위 전달 + 즉시 플로우"에만 집중. (아이템/룩 전체 전달은 추후 필요 시 확장)
 * - 안전 소비: consume() 호출 시 한 번만 소모되도록 설계하여 중복 트리거 방지.
 *
 * 크레딧(주당 제한)과의 관계
 * - 크레딧/주당 제한은 TryOnRepository(CreditStore.consumeOne())에서 강제된다.
 * - 본 브리지는 이동 의도/초기 상태만 다루고, 크레딧 체크/소비는 기존 로직을 그대로 사용한다.
 *
 * 사용 예
 * - Home에서:
 *   TryOnBridge.setPart(TryOnPart.TOP, autoLaunchPicker = true)
 *   onNavigateTryOn()
 *
 * - TryOn 화면에서(예: LaunchedEffect로 한 번 소비):
 *   TryOnBridge.consume()?.let { intent ->
 *       // 세그먼트 초기화: part = intent.part.wire
 *       // 필요 시 포토 피커 즉시 실행: if (intent.autoLaunchPicker) picker.launch(...)
 *   }
 */
object TryOnBridge {

    /** Try-On 우선 부위(세그먼트) */
    enum class TryOnPart(val wire: String) {
        TOP("TOP"),
        BOTTOM("BOTTOM")
    }

    /** Try-On 이동 의도 */
    data class TryOnIntent(
        val part: TryOnPart,
        val autoLaunchPicker: Boolean = true,
        val createdAt: Long = System.currentTimeMillis(),
        val itemImageUri: String? = null
    )

    /** 내부 보관 상태(한 번만 소비될 수 있도록 관리) */
    private val _pending: MutableStateFlow<TryOnIntent?> = MutableStateFlow(null)

    /** 관찰용(필요 시) */
    val pending: StateFlow<TryOnIntent?> = _pending

    /**
     * 간편 설정: 우선 부위만 지정 (기본적으로 포토 피커 즉시 실행)
     */
    fun setPart(part: TryOnPart, autoLaunchPicker: Boolean = true, itemImageUri: String? = null) {
        _pending.value = TryOnIntent(part = part, autoLaunchPicker = autoLaunchPicker, itemImageUri = itemImageUri)
    }

    /**
     * 전체 인텐트 설정
     */
    fun set(intent: TryOnIntent) {
        _pending.value = intent
    }

    /**
     * 현재 대기 중인 인텐트를 한 번만 소비하고 비운다.
     * - 중복 트리거 방지
     */
    fun consume(): TryOnIntent? {
        val v = _pending.value
        _pending.value = null
        return v
    }

    /**
     * 현재 인텐트를 비우지 않고 조회(디버그/상태표시용)
     */
    fun peek(): TryOnIntent? = _pending.value

    /**
     * 강제 초기화(필요 시)
     */
    fun clear() {
        _pending.value = null
    }

    /**
     * 현재 저장된 부위만 빠르게 조회(없으면 null)
     */
    fun currentPartOrNull(): TryOnPart? = _pending.value?.part
    fun currentItemImageUriOrNull(): String? = _pending.value?.itemImageUri
}
