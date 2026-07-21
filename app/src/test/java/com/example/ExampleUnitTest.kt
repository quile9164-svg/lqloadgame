package com.example

import com.example.core.security.SecurityUtils
import com.example.data.cos.CosSigner
import com.example.data.project.ProjectSerialization
import com.example.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testTokenRedaction() {
        val rawLog = "Error: access_token=abcdef123456&sig=987654321qwer&something=else"
        val redactedLog = SecurityUtils.redact(rawLog)
        
        assertTrue(redactedLog.contains("access_token=[REDACTED]"))
        assertTrue(redactedLog.contains("sig=[REDACTED]"))
        assertTrue(redactedLog.contains("something=else"))
    }

    @Test
    fun testProjectSerialization() {
        val originalProject = Project(
            id = 42,
            name = "Test Campaign Poster",
            background = BackgroundTransform(
                uri = "content://media/external/images/media/1",
                x = 10f,
                y = -20f,
                scale = 1.2f,
                rotation = 45f,
                isFlippedHorizontally = true
            ),
            layers = listOf(
                LayerModel(
                    id = "layer-1",
                    name = "Main Heading",
                    type = LayerType.TEXT,
                    textContent = "AOV Arena",
                    textColor = "#FF0000",
                    fontSize = 32f,
                    textPreset = TextPreset.GOLD
                ),
                LayerModel(
                    id = "layer-2",
                    name = "Bottom Banner",
                    type = LayerType.SHAPE,
                    shapeType = ShapeType.BANNER,
                    shapeColor1 = "#0000FF",
                    shapeColor2 = "#00FF00"
                )
            ),
            quality = "HQ Strong"
        )

        val jsonStr = ProjectSerialization.serialize(originalProject)
        assertNotNull(jsonStr)

        val reconstructed = ProjectSerialization.deserialize(jsonStr)
        assertEquals(originalProject.id, reconstructed.id)
        assertEquals(originalProject.name, reconstructed.name)
        assertEquals(originalProject.quality, reconstructed.quality)
        
        // Assert background transform matches
        assertEquals(originalProject.background.uri, reconstructed.background.uri)
        assertEquals(originalProject.background.x, reconstructed.background.x, 0.01f)
        assertEquals(originalProject.background.scale, reconstructed.background.scale, 0.01f)
        assertEquals(originalProject.background.isFlippedHorizontally, reconstructed.background.isFlippedHorizontally)

        // Assert layers reconstructed properly
        assertEquals(2, reconstructed.layers.size)
        val textLayer = reconstructed.layers.find { it.type == LayerType.TEXT }
        assertNotNull(textLayer)
        assertEquals("AOV Arena", textLayer?.textContent)
        assertEquals("#FF0000", textLayer?.textColor)
        assertEquals(TextPreset.GOLD, textLayer?.textPreset)

        val shapeLayer = reconstructed.layers.find { it.type == LayerType.SHAPE }
        assertNotNull(shapeLayer)
        assertEquals(ShapeType.BANNER, shapeLayer?.shapeType)
        assertEquals("#0000FF", shapeLayer?.shapeColor1)
        assertEquals("#00FF00", shapeLayer?.shapeColor2)
    }

    @Test
    fun testCosSignature() {
        val signature = CosSigner.generatePutSignature(
            tmpSecretId = "AKIDtest123",
            tmpSecretKey = "secretkeytest123",
            startTime = 1721544000,
            expiration = 1721547600,
            bucket = "aov-poster-1250000",
            region = "ap-singapore",
            path = "/poster/999_large.png"
        )

        assertNotNull(signature)
        assertTrue(signature.contains("q-sign-algorithm=sha1"))
        assertTrue(signature.contains("q-ak=AKIDtest123"))
        assertTrue(signature.contains("q-sign-time=1721544000;1721547600"))
        assertTrue(signature.contains("q-header-list=host"))
        assertTrue(signature.contains("&q-signature="))
    }
}
