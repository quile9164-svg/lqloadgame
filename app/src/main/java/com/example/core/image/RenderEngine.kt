package com.example.core.image

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.domain.model.*
import java.io.InputStream
import kotlin.math.cos
import kotlin.math.sin

object RenderEngine {

    /**
     * Renders the project to a Bitmap of the target size, implementing HQ Balanced (1.5x)
     * and HQ Strong (2x) downsampling.
     */
    fun renderProject(
        context: Context,
        project: Project,
        width: Int,
        height: Int
    ): Bitmap {
        // Determine the rendering resolution multiplier
        val scaleFactor = when (project.quality) {
            "HQ Balanced" -> 1.5f
            "HQ Strong" -> 2.0f
            else -> 1.0f
        }

        val renderWidth = (width * scaleFactor).toInt()
        val renderHeight = (height * scaleFactor).toInt()

        // 1. Create base bitmap and canvas
        var bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // 2. Draw Background
        val bgTransform = project.background
        if (bgTransform.uri.isNotEmpty()) {
            val bgBitmap = loadBitmap(context, bgTransform.uri)
            if (bgBitmap != null) {
                val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                canvas.save()
                
                // Scale coordinate to render resolution
                val centerX = renderWidth / 2f
                val centerY = renderHeight / 2f
                canvas.translate(centerX + bgTransform.x * scaleFactor, centerY + bgTransform.y * scaleFactor)
                canvas.rotate(bgTransform.rotation)
                
                val flipScaleX = if (bgTransform.isFlippedHorizontally) -1f else 1f
                canvas.scale(bgTransform.scale * scaleFactor * flipScaleX, bgTransform.scale * scaleFactor)

                // Draw centered
                val srcWidth = bgBitmap.width
                val srcHeight = bgBitmap.height
                canvas.drawBitmap(bgBitmap, -srcWidth / 2f, -srcHeight / 2f, bgPaint)
                canvas.restore()
                bgBitmap.recycle()
            }
        }

        // 3. Draw Layers
        for (layer in project.layers) {
            if (!layer.isVisible) continue
            canvas.save()

            // Translate to center of layer and rotate
            val layerCenterX = (layer.x + layer.width / 2f) * scaleFactor
            val layerCenterY = (layer.y + layer.height / 2f) * scaleFactor
            canvas.translate(layerCenterX, layerCenterY)
            canvas.rotate(layer.rotation)

            val scaledWidth = layer.width * scaleFactor
            val scaledHeight = layer.height * scaleFactor

            when (layer.type) {
                LayerType.TEXT -> {
                    drawTextLayer(canvas, layer, scaledWidth, scaledHeight, layer.opacity, scaleFactor)
                }
                LayerType.STICKER -> {
                    if (layer.stickerUri.isNotEmpty()) {
                        val stickerBitmap = loadBitmap(context, layer.stickerUri)
                        if (stickerBitmap != null) {
                            drawStickerLayer(canvas, stickerBitmap, layer, scaledWidth, scaledHeight)
                            stickerBitmap.recycle()
                        }
                    }
                }
                LayerType.SHAPE -> {
                    drawShapeLayer(canvas, layer, scaledWidth, scaledHeight)
                }
            }
            canvas.restore()
        }

        // 4. Apply Filters & Presets
        val filteredBitmap = applyFiltersAndPresets(bitmap, project.filters)
        if (filteredBitmap != bitmap) {
            bitmap.recycle()
            bitmap = filteredBitmap
        }

        // 5. Downsample if using HQ mode
        if (scaleFactor > 1.0f) {
            val finalBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
            bitmap.recycle()
            return finalBitmap
        }

        return bitmap
    }

    private fun loadBitmap(context: Context, uriString: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(inputStream, null, options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun drawTextLayer(
        canvas: Canvas,
        layer: LayerModel,
        width: Float,
        height: Float,
        opacity: Float,
        scaleFactor: Float
    ) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = layer.fontSize * scaleFactor
            color = Color.parseColor(layer.textColor)
            alpha = (opacity * 255).toInt()
            typeface = if (layer.isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }

        // Preset implementations
        when (layer.textPreset) {
            TextPreset.NEON -> {
                textPaint.setShadowLayer(10f * scaleFactor, 0f, 0f, Color.parseColor(layer.textColor))
            }
            TextPreset.GOLD -> {
                textPaint.color = Color.parseColor("#FFD700")
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textPaint.setShadowLayer(4f * scaleFactor, 2f, 2f, Color.parseColor("#8B6508"))
            }
            TextPreset.ESPORTS -> {
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textPaint.setShadowLayer(6f * scaleFactor, 3f, 3f, Color.BLACK)
            }
            TextPreset.MINIMAL -> {
                textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            }
            TextPreset.NONE -> {
                if (layer.hasShadow) {
                    textPaint.setShadowLayer(5f * scaleFactor, 2f, 2f, Color.BLACK)
                }
            }
        }

        // Alignments
        val alignment = when (layer.textAlignment) {
            TextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_CENTER
        }

        val staticLayout = StaticLayout.Builder.obtain(
            layer.textContent,
            0,
            layer.textContent.length,
            textPaint,
            width.toInt()
        )
            .setAlignment(alignment)
            .setLineSpacing(layer.lineSpacing, 0f)
            .setIncludePad(false)
            .build()

        // Draw Multiline Text centered in layer box
        canvas.save()
        canvas.translate(-width / 2f, -height / 2f)

        // Draw Stroke if required
        if (layer.textStrokeWidth > 0f) {
            val strokePaint = TextPaint(textPaint).apply {
                style = Paint.Style.STROKE
                strokeWidth = layer.textStrokeWidth * scaleFactor
                color = Color.parseColor(layer.textStrokeColor)
                alpha = (opacity * 255).toInt()
            }
            val strokeLayout = StaticLayout.Builder.obtain(
                layer.textContent,
                0,
                layer.textContent.length,
                strokePaint,
                width.toInt()
            )
                .setAlignment(alignment)
                .setLineSpacing(layer.lineSpacing, 0f)
                .setIncludePad(false)
                .build()

            strokeLayout.draw(canvas)
        }

        staticLayout.draw(canvas)
        canvas.restore()
    }

    private fun drawStickerLayer(
        canvas: Canvas,
        bitmap: Bitmap,
        layer: LayerModel,
        width: Float,
        height: Float
    ) {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (layer.opacity * 255).toInt()
        }

        // Define source rectangle for cropping if cropped
        val srcRect = Rect(
            (layer.stickerCropX * bitmap.width).toInt(),
            (layer.stickerCropY * bitmap.height).toInt(),
            ((layer.stickerCropX + layer.stickerCropW) * bitmap.width).toInt(),
            ((layer.stickerCropY + layer.stickerCropH) * bitmap.height).toInt()
        )

        val destRect = RectF(
            -width / 2f,
            -height / 2f,
            width / 2f,
            height / 2f
        )

        canvas.drawBitmap(bitmap, srcRect, destRect, paint)
    }

    private fun drawShapeLayer(
        canvas: Canvas,
        layer: LayerModel,
        width: Float,
        height: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (layer.opacity * 255).toInt()
            style = Paint.Style.FILL
        }

        val rect = RectF(-width / 2f, -height / 2f, width / 2f, height / 2f)

        // Apply Gradients if shapeColor2 is present
        if (layer.shapeColor2 != null) {
            paint.shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                Color.parseColor(layer.shapeColor1),
                Color.parseColor(layer.shapeColor2),
                Shader.TileMode.CLAMP
            )
        } else {
            paint.color = Color.parseColor(layer.shapeColor1)
        }

        when (layer.shapeType) {
            ShapeType.ROUNDED_RECT -> {
                canvas.drawRoundRect(rect, layer.shapeCornerRadius, layer.shapeCornerRadius, paint)
            }
            ShapeType.ELLIPSE -> {
                canvas.drawOval(rect, paint)
            }
            ShapeType.BANNER -> {
                // Banner with slightly angled borders
                val path = Path().apply {
                    moveTo(rect.left, rect.top)
                    lineTo(rect.right - 20f, rect.top)
                    lineTo(rect.right, rect.bottom)
                    lineTo(rect.left + 20f, rect.bottom)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            ShapeType.GRADIENT_RECT -> {
                canvas.drawRect(rect, paint)
            }
        }
    }

    private fun applyFiltersAndPresets(bitmap: Bitmap, filters: FilterSettings): Bitmap {
        var result = bitmap
        
        // 1. ColorMatrix adjustments (Brightness, Contrast, Saturation, Hue, Vignette)
        val colorMatrix = ColorMatrix()

        // Contrast scale
        val c = filters.contrast
        val t = (1.0f - c) * 255.0f * 0.5f

        // Brightness
        val b = filters.brightness * 255f

        val contrastMatrix = floatArrayOf(
            c, 0f, 0f, 0f, b + t,
            0f, c, 0f, 0f, b + t,
            0f, 0f, c, 0f, b + t,
            0f, 0f, 0f, 1f, 0f
        )
        colorMatrix.set(contrastMatrix)

        // Saturation
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(filters.saturation)
        colorMatrix.postConcat(satMatrix)

        // Hue rotation (around RGB axes)
        if (filters.hue != 0f) {
            val hRed = ColorMatrix().apply { setRotate(0, filters.hue) }
            val hGreen = ColorMatrix().apply { setRotate(1, filters.hue) }
            val hBlue = ColorMatrix().apply { setRotate(2, filters.hue) }
            colorMatrix.postConcat(hRed)
            colorMatrix.postConcat(hGreen)
            colorMatrix.postConcat(hBlue)
        }

        // Apply preset overrides if defined
        when (filters.preset) {
            "Auto Pop" -> {
                // High contrast, vivid saturation
                val popMatrix = ColorMatrix()
                popMatrix.setSaturation(1.4f)
                colorMatrix.postConcat(popMatrix)
            }
            "Cinematic" -> {
                // Slightly desaturated, warm yellow-greenish shadows
                val cineMatrix = ColorMatrix(floatArrayOf(
                    1.1f, 0f, 0f, 0f, 10f,
                    0f, 1.0f, 0f, 0f, 5f,
                    0f, 0f, 0.9f, 0f, -10f,
                    0f, 0f, 0f, 1.0f, 0f
                ))
                colorMatrix.postConcat(cineMatrix)
            }
        }

        val finalBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        result = finalBitmap

        // 2. Sharpening convolution (0% to 60%)
        if (filters.sharpen > 0f) {
            val sharpened = sharpenBitmap(result, filters.sharpen)
            if (sharpened != result) {
                result.recycle()
                result = sharpened
            }
        }

        return result
    }

    private fun sharpenBitmap(src: Bitmap, amount: Float): Bitmap {
        val width = src.width
        val height = src.height
        val sharp = Bitmap.createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)

        // Fast 3x3 Sharpen Kernel convolution
        // Center weight = 5, Neighbor weights = -1
        // We interpolate original pixel and sharpened pixel based on amount (0 to 0.6)
        val factor = amount * 0.5f // scale the effect
        
        val srcPixels = IntArray(width * height)
        val destPixels = IntArray(width * height)
        src.getPixels(srcPixels, 0, width, 0, 0, width, height)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x

                val c00 = srcPixels[idx - width - 1]
                val c01 = srcPixels[idx - width]
                val c02 = srcPixels[idx - width + 1]
                val c10 = srcPixels[idx - 1]
                val c11 = srcPixels[idx]
                val c12 = srcPixels[idx + 1]
                val c20 = srcPixels[idx + width - 1]
                val c21 = srcPixels[idx + width]
                val c22 = srcPixels[idx + width + 1]

                // Sum red, green, blue values multiplied by kernel weights
                // Kernel:
                // [  0, -1,  0 ]
                // [ -1,  5, -1 ]
                // [  0, -1,  0 ]
                val rSharp = 5 * Color.red(c11) - Color.red(c01) - Color.red(c10) - Color.red(c12) - Color.red(c21)
                val gSharp = 5 * Color.green(c11) - Color.green(c01) - Color.green(c10) - Color.green(c12) - Color.green(c21)
                val bSharp = 5 * Color.blue(c11) - Color.blue(c01) - Color.blue(c10) - Color.blue(c12) - Color.blue(c21)

                val rOrig = Color.red(c11)
                val gOrig = Color.green(c11)
                val bOrig = Color.blue(c11)

                val rFinal = (rOrig + factor * (rSharp - rOrig)).toInt().coerceIn(0, 255)
                val gFinal = (gOrig + factor * (gSharp - gOrig)).toInt().coerceIn(0, 255)
                val bFinal = (bOrig + factor * (bSharp - bOrig)).toInt().coerceIn(0, 255)
                val aFinal = Color.alpha(c11)

                destPixels[idx] = Color.argb(aFinal, rFinal, gFinal, bFinal)
            }
        }
        sharp.setPixels(destPixels, 0, width, 0, 0, width, height)
        return sharp
    }
}
