package com.loadingbyte.cinecred.projectio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes


internal class CsvFormatTest {

    private val windows1252 = Charset.forName("windows-1252")

    @Test
    fun `reads legacy windows-1252 csv characters`() {
        val file = createTempFile(suffix = ".csv")
        try {
            file.writeBytes("\"¡El Gatito con Zapatos Azules!\",Johannes Bürner\r\n".toByteArray(windows1252))

            val (sheets, log) = CsvFormat.read(file, "Credits")

            assertEquals(emptyList<ParserMsg>(), log)
            assertEquals("¡El Gatito con Zapatos Azules!", sheets.single()[0, 0])
            assertEquals("Johannes Bürner", sheets.single()[0, 1])
        } finally {
            file.deleteIfExists()
        }
    }

    @Test
    fun `keeps utf-8 csv characters`() {
        val file = createTempFile(suffix = ".csv")
        try {
            file.writeBytes("\"¡El Gatito con Zapatos Azules!\",Johannes Bürner\r\n".toByteArray(Charsets.UTF_8))

            val (sheets, _) = CsvFormat.read(file, "Credits")

            assertEquals("¡El Gatito con Zapatos Azules!", sheets.single()[0, 0])
            assertEquals("Johannes Bürner", sheets.single()[0, 1])
        } finally {
            file.deleteIfExists()
        }
    }

}
