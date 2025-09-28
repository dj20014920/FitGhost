package com.fitghost.app.engine

/**
 * Try-On 기본 프롬프트 템플릿(공통)
 * - 사용자가 별도의 시스템 프롬프트를 입력하지 않은 경우 사용
 * - 이미지 합성 지시(추천/설명 텍스트 아님)
 */
object PromptTemplates {
    val TRYON_DEFAULT_PROMPT: String = (
        """
        Using the provided images, place the clothing item from image 1 onto the person in image 2.
        Ensure the features of image 2 (face, hair, hands, identity) remain completely unchanged.
        Integrate the clothing naturally: match perspective, scale, pose, fabric drape, lighting, and shadows.
        Produce a photorealistic, professional e-commerce fashion photo, preferably a full-body shot if possible.
        """.trimIndent()
    )
}

