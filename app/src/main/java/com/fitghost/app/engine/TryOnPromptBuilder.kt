package com.fitghost.app.engine

/**
 * TryOnPromptBuilder
 * - 가상 피팅 공통 프롬프트(시스템/유저) 생성을 담당하는 유틸리티
 * - 중복 로직 방지 및 일관성 유지
 */
object TryOnPromptBuilder {

    /**
     * 시스템 프롬프트 생성
     * - Google Gemini 2.5 Flash 공식 가이드 기반 최적화
     * - 사용자 프롬프트가 있으면 기본 프롬프트 대신 사용자 프롬프트 사용
     */
    fun buildSystemText(userSystemPrompt: String?): String {
        // 사용자가 프롬프트를 입력한 경우 사용자 프롬프트만 사용
        if (!userSystemPrompt.isNullOrBlank()) {
            return userSystemPrompt.trim()
        }
        
        // 기본 프롬프트: Google 공식 가이드 기반
        return """
            Create a photorealistic fashion image by placing the clothing items onto the person.
            
            Key requirements:
            - Preserve the person's face, body proportions, pose, and background exactly as shown
            - Fit the clothing naturally to the person's body shape and pose
            - Match the lighting, shadows, and perspective of the original photo
            - Ensure fabric draping and wrinkles look realistic and natural
            - Maintain sharp edges around hair, hands, and body contours
            - Keep the overall composition professional and suitable for e-commerce
            - Blend colors harmoniously between the clothing and the scene
            
            Style: Professional fashion photography with studio-quality lighting and natural appearance.
        """.trimIndent()
    }

    /**
     * 유저(콘텐츠) 텍스트 생성
     * - Google 공식 가이드 기반: 명확하고 구체적인 지시문
     * - hasModel=true이면 이미지1=모델, 이미지2..N=의상들
     */
    fun buildUserInstruction(hasModel: Boolean, clothingCount: Int): String {
        if (!hasModel) {
            // 현재 UI상 모델 없이 실행되지 않지만, 방어적으로 처리
            return """
                Generate a professional fashion photograph showing the clothing items on a realistic human model.
                Use studio lighting and a clean background.
                The result should be suitable for e-commerce product display.
            """.trimIndent()
        }

        return if (clothingCount <= 1) {
            // 이미지1: 모델, 이미지2: 의상1
            """
                Generate a new image showing the person from the first image wearing the clothing item from the second image.
                Keep the person's face, pose, body shape, and background unchanged.
                Make the clothing fit naturally on the person's body with realistic fabric draping.
                Match the lighting and shadows to create a cohesive, photorealistic result.
            """.trimIndent()
        } else {
            // 이미지1: 모델, 이미지2..N: 의상들
            """
                Generate a new image showing the person from the first image wearing all the clothing items from the subsequent images as a complete outfit.
                Keep the person's face, pose, body shape, and background unchanged.
                Layer the clothing items appropriately (e.g., shirt under jacket, pants on bottom).
                Make each piece fit naturally with realistic fabric draping and proper proportions.
                Match the lighting and shadows to create a cohesive, photorealistic result.
            """.trimIndent()
        }
    }
}

