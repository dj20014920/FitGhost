package com.fitghost.app.engine

/**
 * TryOnPromptBuilder
 * - 가상 피팅 공통 프롬프트(시스템/유저) 생성을 담당하는 유틸리티
 * - 중복 로직 방지 및 일관성 유지
 */
object TryOnPromptBuilder {

    /**
     * 시스템 프롬프트 생성
     * - 기본 품질/제약 가이드라인에 사용자가 전달한 추가 지시문을 결합
     */
    fun buildSystemText(userSystemPrompt: String?): String {
        val base = """
            You are a professional virtual try-on engine.
            Requirements:
            - Preserve the person's identity, face, hands, hair, and background.
            - Match perspective, scale, pose, and lighting realistically.
            - Avoid warping or artifacts; keep edges (hair, fingers) natural.
            - Produce a photorealistic studio-quality image suitable for e-commerce.
            - Maintain consistent shadows and color harmony between garments and the person.
        """.trimIndent()
        val extra = userSystemPrompt?.takeIf { it.isNotBlank() }?.let {
            "\nAdditional instructions: ${it.trim()}"
        }.orEmpty()
        return base + extra
    }

    /**
     * 유저(콘텐츠) 텍스트 생성
     * - 공식 템플릿을 기반으로 이미지 인덱스에 맞춰 동적으로 생성
     * - hasModel=true이면 이미지1=모델, 이미지2..N=의상들
     */
    fun buildUserInstruction(hasModel: Boolean, clothingCount: Int): String {
        if (!hasModel) {
            // 현재 UI상 모델 없이 실행되지 않지만, 방어적으로 처리
            return """
                Create a new image by combining the elements from the provided images.
                Take the clothing items from the provided images and present them on a neutral mannequin or realistic human figure.
                The final image should be a professional e-commerce fashion photo with photorealistic studio lighting and clean composition.
            """.trimIndent()
        }

        return if (clothingCount <= 1) {
            // 이미지1: 모델, 이미지2: 의상1
            """
                Create a new image by combining the elements from the provided images.
                Take the person from image 1 and place the clothing item from image 2 onto the person.
                The final image should be a professional e-commerce fashion photo with photorealistic studio lighting, full-body composition, and natural shadows and perspective.
            """.trimIndent()
        } else {
            // 이미지1: 모델, 이미지2..N: 의상들
            """
                Create a new image by combining the elements from the provided images.
                Take the person from image 1 and place the clothing items from images 2 to ${clothingCount + 1} onto the person as a coordinated outfit.
                The final image should be a professional e-commerce fashion photo with photorealistic studio lighting, full-body composition, and natural shadows and perspective.
            """.trimIndent()
        }
    }
}

