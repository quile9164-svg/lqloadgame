package com.example.domain.model

import androidx.annotation.Keep

@Keep
enum class LayerType {
    TEXT, STICKER, SHAPE
}

@Keep
enum class TextPreset {
    NONE, NEON, GOLD, ESPORTS, MINIMAL
}

@Keep
enum class ShapeType {
    ROUNDED_RECT, ELLIPSE, BANNER, GRADIENT_RECT
}

@Keep
enum class TextAlign {
    LEFT, CENTER, RIGHT
}

@Keep
data class LayerModel(
    val id: String,
    val name: String,
    val type: LayerType,
    val isLocked: Boolean = false,
    val isVisible: Boolean = true,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 120f,
    val height: Float = 80f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    // Text style fields
    val textContent: String = "",
    val textColor: String = "#FFFFFF",
    val textStrokeColor: String = "#000000",
    val textStrokeWidth: Float = 0f,
    val fontSize: Float = 24f,
    val fontFamily: String = "Default",
    val isBold: Boolean = false,
    val textAlignment: TextAlign = TextAlign.CENTER,
    val letterSpacing: Float = 0f,
    val lineSpacing: Float = 1f,
    val textPreset: TextPreset = TextPreset.NONE,
    val hasShadow: Boolean = false,
    // Sticker fields
    val stickerUri: String = "",
    val stickerCropX: Float = 0f,
    val stickerCropY: Float = 0f,
    val stickerCropW: Float = 1f,
    val stickerCropH: Float = 1f,
    // Shape fields
    val shapeType: ShapeType = ShapeType.ROUNDED_RECT,
    val shapeColor1: String = "#03DAC6",
    val shapeColor2: String? = null,
    val shapeCornerRadius: Float = 8f
)

@Keep
data class BackgroundTransform(
    val uri: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val isFlippedHorizontally: Boolean = false,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0
)

@Keep
data class FilterSettings(
    val sharpen: Float = 0f, // 0 to 60%
    val brightness: Float = 0f, // -1f to 1f
    val contrast: Float = 1f, // 0.5f to 2f
    val saturation: Float = 1f, // 0f to 2f
    val hue: Float = 0f, // -180f to 180f
    val blur: Float = 0f, // 0f to 10f
    val vignette: Float = 0f, // 0f to 1f
    val preset: String = "None" // "None", "Auto Pop", "Cinematic"
)

@Keep
data class Project(
    val id: Int = 0,
    val name: String,
    val background: BackgroundTransform = BackgroundTransform(),
    val layers: List<LayerModel> = emptyList(),
    val filters: FilterSettings = FilterSettings(),
    val quality: String = "Standard", // "Standard", "HQ Balanced", "HQ Strong"
    val lastUpdated: Long = System.currentTimeMillis(),
    val thumbnailPath: String? = null
)
