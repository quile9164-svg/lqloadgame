package com.example.data.project

import com.example.domain.model.*
import org.json.JSONArray
import org.json.JSONObject

object ProjectSerialization {

    fun serialize(project: Project): String {
        val root = JSONObject().apply {
            put("id", project.id)
            put("name", project.name)
            put("quality", project.quality)
            put("lastUpdated", project.lastUpdated)
            if (project.thumbnailPath != null) {
                put("thumbnailPath", project.thumbnailPath)
            }

            // Background
            val bg = JSONObject().apply {
                put("uri", project.background.uri)
                put("x", project.background.x.toDouble())
                put("y", project.background.y.toDouble())
                put("scale", project.background.scale.toDouble())
                put("rotation", project.background.rotation.toDouble())
                put("isFlipped", project.background.isFlippedHorizontally)
                put("originalWidth", project.background.originalWidth)
                put("originalHeight", project.background.originalHeight)
            }
            put("background", bg)

            // Filters
            val fl = JSONObject().apply {
                put("sharpen", project.filters.sharpen.toDouble())
                put("brightness", project.filters.brightness.toDouble())
                put("contrast", project.filters.contrast.toDouble())
                put("saturation", project.filters.saturation.toDouble())
                put("hue", project.filters.hue.toDouble())
                put("blur", project.filters.blur.toDouble())
                put("vignette", project.filters.vignette.toDouble())
                put("preset", project.filters.preset)
            }
            put("filters", fl)

            // Layers
            val layersArray = JSONArray()
            for (layer in project.layers) {
                val lay = JSONObject().apply {
                    put("id", layer.id)
                    put("name", layer.name)
                    put("type", layer.type.name)
                    put("isLocked", layer.isLocked)
                    put("isVisible", layer.isVisible)
                    put("x", layer.x.toDouble())
                    put("y", layer.y.toDouble())
                    put("width", layer.width.toDouble())
                    put("height", layer.height.toDouble())
                    put("rotation", layer.rotation.toDouble())
                    put("opacity", layer.opacity.toDouble())

                    // Text style fields
                    put("textContent", layer.textContent)
                    put("textColor", layer.textColor)
                    put("textStrokeColor", layer.textStrokeColor)
                    put("textStrokeWidth", layer.textStrokeWidth.toDouble())
                    put("fontSize", layer.fontSize.toDouble())
                    put("fontFamily", layer.fontFamily)
                    put("isBold", layer.isBold)
                    put("textAlignment", layer.textAlignment.name)
                    put("letterSpacing", layer.letterSpacing.toDouble())
                    put("lineSpacing", layer.lineSpacing.toDouble())
                    put("textPreset", layer.textPreset.name)
                    put("hasShadow", layer.hasShadow)

                    // Sticker fields
                    put("stickerUri", layer.stickerUri)
                    put("stickerCropX", layer.stickerCropX.toDouble())
                    put("stickerCropY", layer.stickerCropY.toDouble())
                    put("stickerCropW", layer.stickerCropW.toDouble())
                    put("stickerCropH", layer.stickerCropH.toDouble())

                    // Shape fields
                    put("shapeType", layer.shapeType.name)
                    put("shapeColor1", layer.shapeColor1)
                    if (layer.shapeColor2 != null) {
                        put("shapeColor2", layer.shapeColor2)
                    }
                    put("shapeCornerRadius", layer.shapeCornerRadius.toDouble())
                }
                layersArray.put(lay)
            }
            put("layers", layersArray)
        }
        return root.toString()
    }

    fun deserialize(jsonStr: String): Project {
        try {
            val root = JSONObject(jsonStr)
            val id = root.optInt("id", 0)
            val name = root.optString("name", "Untitled")
            val quality = root.optString("quality", "Standard")
            val lastUpdated = root.optLong("lastUpdated", System.currentTimeMillis())
            val thumbnailPath = if (root.has("thumbnailPath")) root.getString("thumbnailPath") else null

            // Background
            val bgObj = root.optJSONObject("background")
            val background = if (bgObj != null) {
                BackgroundTransform(
                    uri = bgObj.optString("uri", ""),
                    x = bgObj.optDouble("x", 0.0).toFloat(),
                    y = bgObj.optDouble("y", 0.0).toFloat(),
                    scale = bgObj.optDouble("scale", 1.0).toFloat(),
                    rotation = bgObj.optDouble("rotation", 0.0).toFloat(),
                    isFlippedHorizontally = bgObj.optBoolean("isFlipped", false),
                    originalWidth = bgObj.optInt("originalWidth", 0),
                    originalHeight = bgObj.optInt("originalHeight", 0)
                )
            } else {
                BackgroundTransform()
            }

            // Filters
            val flObj = root.optJSONObject("filters")
            val filters = if (flObj != null) {
                FilterSettings(
                    sharpen = flObj.optDouble("sharpen", 0.0).toFloat(),
                    brightness = flObj.optDouble("brightness", 0.0).toFloat(),
                    contrast = flObj.optDouble("contrast", 1.0).toFloat(),
                    saturation = flObj.optDouble("saturation", 1.0).toFloat(),
                    hue = flObj.optDouble("hue", 0.0).toFloat(),
                    blur = flObj.optDouble("blur", 0.0).toFloat(),
                    vignette = flObj.optDouble("vignette", 0.0).toFloat(),
                    preset = flObj.optString("preset", "None")
                )
            } else {
                FilterSettings()
            }

            // Layers
            val layersList = mutableListOf<LayerModel>()
            val layersArray = root.optJSONArray("layers")
            if (layersArray != null) {
                for (i in 0 until layersArray.length()) {
                    val layObj = layersArray.getJSONObject(i)
                    val typeStr = layObj.optString("type", LayerType.TEXT.name)
                    val type = try { LayerType.valueOf(typeStr) } catch (e: Exception) { LayerType.TEXT }

                    val alignStr = layObj.optString("textAlignment", TextAlign.CENTER.name)
                    val textAlignment = try { TextAlign.valueOf(alignStr) } catch (e: Exception) { TextAlign.CENTER }

                    val textPresetStr = layObj.optString("textPreset", TextPreset.NONE.name)
                    val textPreset = try { TextPreset.valueOf(textPresetStr) } catch (e: Exception) { TextPreset.NONE }

                    val shapeTypeStr = layObj.optString("shapeType", ShapeType.ROUNDED_RECT.name)
                    val shapeType = try { ShapeType.valueOf(shapeTypeStr) } catch (e: Exception) { ShapeType.ROUNDED_RECT }

                    val layer = LayerModel(
                        id = layObj.optString("id", ""),
                        name = layObj.optString("name", "Layer"),
                        type = type,
                        isLocked = layObj.optBoolean("isLocked", false),
                        isVisible = layObj.optBoolean("isVisible", true),
                        x = layObj.optDouble("x", 0.0).toFloat(),
                        y = layObj.optDouble("y", 0.0).toFloat(),
                        width = layObj.optDouble("width", 100.0).toFloat(),
                        height = layObj.optDouble("height", 100.0).toFloat(),
                        rotation = layObj.optDouble("rotation", 0.0).toFloat(),
                        opacity = layObj.optDouble("opacity", 1.0).toFloat(),

                        textContent = layObj.optString("textContent", ""),
                        textColor = layObj.optString("textColor", "#FFFFFF"),
                        textStrokeColor = layObj.optString("textStrokeColor", "#000000"),
                        textStrokeWidth = layObj.optDouble("textStrokeWidth", 0.0).toFloat(),
                        fontSize = layObj.optDouble("fontSize", 24.0).toFloat(),
                        fontFamily = layObj.optString("fontFamily", "Default"),
                        isBold = layObj.optBoolean("isBold", false),
                        textAlignment = textAlignment,
                        letterSpacing = layObj.optDouble("letterSpacing", 0.0).toFloat(),
                        lineSpacing = layObj.optDouble("lineSpacing", 1.0).toFloat(),
                        textPreset = textPreset,
                        hasShadow = layObj.optBoolean("hasShadow", false),

                        stickerUri = layObj.optString("stickerUri", ""),
                        stickerCropX = layObj.optDouble("stickerCropX", 0.0).toFloat(),
                        stickerCropY = layObj.optDouble("stickerCropY", 0.0).toFloat(),
                        stickerCropW = layObj.optDouble("stickerCropW", 1.0).toFloat(),
                        stickerCropH = layObj.optDouble("stickerCropH", 1.0).toFloat(),

                        shapeType = shapeType,
                        shapeColor1 = layObj.optString("shapeColor1", "#03DAC6"),
                        shapeColor2 = if (layObj.has("shapeColor2")) layObj.getString("shapeColor2") else null,
                        shapeCornerRadius = layObj.optDouble("shapeCornerRadius", 8.0).toFloat()
                    )
                    layersList.add(layer)
                }
            }

            return Project(
                id = id,
                name = name,
                background = background,
                layers = layersList,
                filters = filters,
                quality = quality,
                lastUpdated = lastUpdated,
                thumbnailPath = thumbnailPath
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return Project(id = 0, name = "Corrupted Project")
        }
    }
}
