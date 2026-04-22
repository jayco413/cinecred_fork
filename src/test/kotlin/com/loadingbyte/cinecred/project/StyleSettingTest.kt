package com.loadingbyte.cinecred.project

import com.loadingbyte.cinecred.imaging.Color4f
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


internal class StyleSettingTest {

    companion object {
        init {
            System.loadLibrary("skia")
            System.loadLibrary("skiacapi")
            System.loadLibrary("harfbuzz")
            System.loadLibrary("zimg")
            System.loadLibrary("nfd")
            System.loadLibrary("decklinkcapi")
        }
    }

    private val red = Color4f.fromSRGBHexString("#FF0000")
    private val green = Color4f.fromSRGBHexString("#00FF00")
    private val blue = Color4f.fromSRGBHexString("#0000FF")

    @Test
    fun `newStyleUnsafe uses primary constructor for layer with default parameters`() {
        val values = getStyleSettings(Layer::class.java).map { setting ->
            when (setting.name) {
                "name" -> "Flash"
                "collapsed" -> false
                "color1" -> red
                "flashColors" -> persistentListOf(green, blue)
                "flashIntervalFrames" -> 4
                else -> setting.get(PRESET_LAYER)
            }
        }

        val layer = newStyleUnsafe(Layer::class.java, values)

        assertEquals("Flash", layer.name)
        assertEquals(false, layer.collapsed)
        assertEquals(red, layer.color1)
        assertEquals(persistentListOf(green, blue), layer.flashColors)
        assertEquals(4, layer.flashIntervalFrames)
    }

    @Test
    fun `style copy preserves flashing layer settings`() {
        val layer = PRESET_LAYER.copy(
            Layer::flashColors.st().notarize(persistentListOf(red, green)),
            Layer::flashIntervalFrames.st().notarize(3)
        )

        assertEquals(persistentListOf(red, green), layer.flashColors)
        assertEquals(3, layer.flashIntervalFrames)
    }
}
