package com.loadingbyte.cinecred.imaging

import com.loadingbyte.cinecred.project.*
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test


internal class FlashingTextTest {

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
    fun `plain text layer with flash colors becomes flashing coloring`() {
        val layer = PRESET_LAYER.copy(
            shape = LayerShape.TEXT,
            color1 = red,
            flashColors = persistentListOf(green, blue),
            flashIntervalFrames = 3
        )

        val coloring = layer.toFormattedStringColoring(100.0)
        val flashing = assertInstanceOf(FormattedString.Layer.Coloring.Flashing::class.java, coloring)

        assertEquals(listOf(red, green, blue), flashing.colors)
        assertEquals(3, flashing.intervalFrames)
    }

    @Test
    fun `preview keeps primary color for flashing coat`() {
        val coat = DeferredImage.Coat.Flashing(listOf(red, green, blue), intervalFrames = 2)

        val resolved = DeferredImage.resolveTextCoat(coat, frameIdx = 5, animateFlashingText = false)
        val plain = assertInstanceOf(DeferredImage.Coat.Plain::class.java, resolved)

        assertEquals(red, plain.color)
    }

    @Test
    fun `render cycles flashing coat by frame interval`() {
        val coat = DeferredImage.Coat.Flashing(listOf(red, green, blue), intervalFrames = 2)

        val frame0 = assertInstanceOf(
            DeferredImage.Coat.Plain::class.java,
            DeferredImage.resolveTextCoat(coat, frameIdx = 0, animateFlashingText = true)
        )
        val frame2 = assertInstanceOf(
            DeferredImage.Coat.Plain::class.java,
            DeferredImage.resolveTextCoat(coat, frameIdx = 2, animateFlashingText = true)
        )
        val frame4 = assertInstanceOf(
            DeferredImage.Coat.Plain::class.java,
            DeferredImage.resolveTextCoat(coat, frameIdx = 4, animateFlashingText = true)
        )

        assertEquals(red, frame0.color)
        assertEquals(green, frame2.color)
        assertEquals(blue, frame4.color)
    }
}
