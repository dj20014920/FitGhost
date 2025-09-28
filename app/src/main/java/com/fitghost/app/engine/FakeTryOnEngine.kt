package com.fitghost.app.engine

import android.content.Context
import android.graphics.*
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PRD 4.A: 로컬 FakeTryOnEngine
 * - 세그/분류 없이 단순 합성 + 톤보정 + 워터마크로 프리뷰 비트맵 생성
 * - 저장은 외부(갤러리 모듈에서) 처리
 */
class FakeTryOnEngine : TryOnEngine {

    override suspend fun renderPreview(
        context: Context,
        modelUri: Uri,
        clothingUris: List<Uri>,
        systemPrompt: String?
    ): Bitmap = withContext(Dispatchers.Default) {
        val model = loadBitmap(context, modelUri)

        // 캔버스: 모델 기준
        val out = Bitmap.createBitmap(model.width, model.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(model, 0f, 0f, null)

        // 간단 톤 보정 (의상만 약간 명도/대비 조절)
        val cm = ColorMatrix().apply {
            set(floatArrayOf(
                1.05f, 0f, 0f, 0f, 8f,
                0f, 1.05f, 0f, 0f, 8f,
                0f, 0f, 1.05f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
            alpha = 235
        }

        // 다중 의상: 위에서부터 일정 간격으로 중앙 배치(프리뷰)
        val baseTargetWidth = (model.width * 0.6f).toInt()
        val baseY = (model.height * 0.22f)
        val gap = (model.height * 0.02f)
        clothingUris.forEachIndexed { idx, uri ->
            runCatching { loadBitmap(context, uri) }.getOrNull()?.let { clothing ->
                val scale = baseTargetWidth.toFloat() / clothing.width
                val targetH = (clothing.height * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(clothing, baseTargetWidth, targetH, true)
                val x = ((model.width - baseTargetWidth) / 2f)
                val y = baseY + idx * (gap + targetH)
                canvas.drawBitmap(scaled, x, y, paint)
            }
        }

        // 워터마크 "AI PREVIEW" (+ systemPrompt 있으면 작은 텍스트 추가)
        val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (model.width * 0.05f)
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val wmText = "AI PREVIEW"
        canvas.drawText(wmText, 24f, model.height - 24f, wmPaint)

        systemPrompt?.takeIf { it.isNotBlank() }?.let { sp ->
            val spPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = (model.width * 0.025f)
                setShadowLayer(4f, 0f, 0f, Color.BLACK)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val y2 = model.height - 24f - (model.width * 0.06f)
            canvas.drawText(sp.take(40), 24f, y2, spPaint)
        }

        out
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap {
        val stream = context.contentResolver.openInputStream(uri)
        return BitmapFactory.decodeStream(stream).also { stream?.close() }
    }
}
