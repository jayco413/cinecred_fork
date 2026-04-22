package com.loadingbyte.cinecred.imaging

import com.loadingbyte.cinecred.common.BUILD_ID
import com.loadingbyte.cinecred.common.LOGGER
import com.loadingbyte.cinecred.common.createDirectoriesSafely
import kotlin.io.path.*
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.HexFormat


object RenderDiskCache {

    private const val CACHE_SCHEMA_VERSION = 1
    private const val BITMAP_MAGIC = 0x43435243
    private const val BITMAP_FORMAT_VERSION = 1
    private const val COPY_BUFFER_SIZE = 1 shl 20

    fun open(projectDir: Path, excludedOutput: Path?, renderKey: String): Session {
        val cacheDir = projectDir
            .resolve(".cinecred-cache")
            .resolve("v$CACHE_SCHEMA_VERSION")
            .resolve(BUILD_ID)
            .resolve(fingerprintProject(projectDir, excludedOutput))
            .resolve(sha256Hex(renderKey))
        cacheDir.createDirectoriesSafely()
        return Session(cacheDir)
    }

    fun loadBitmap(file: Path, spec: Bitmap.Spec): Bitmap? {
        if (!file.isRegularFile())
            return null
        return try {
            DataInputStream(BufferedInputStream(Files.newInputStream(file))).use { input ->
                require(input.readInt() == BITMAP_MAGIC) { "Wrong bitmap cache magic." }
                require(input.readInt() == BITMAP_FORMAT_VERSION) { "Wrong bitmap cache format version." }
                require(input.readInt() == spec.resolution.widthPx) { "Wrong cached bitmap width." }
                require(input.readInt() == spec.resolution.heightPx) { "Wrong cached bitmap height." }
                val planeCount = input.readInt()
                require(planeCount == spec.representation.pixelFormat.planes) { "Wrong cached bitmap plane count." }
                val bitmap = Bitmap.allocate(spec)
                try {
                    for (plane in 0..<planeCount) {
                        val linesize = input.readInt()
                        val size = input.readLong()
                        require(linesize == bitmap.linesize(plane)) { "Wrong cached bitmap linesize." }
                        require(size == bitmap.memorySegment(plane).byteSize()) { "Wrong cached bitmap plane size." }
                        copyInputToSegment(input, bitmap.memorySegment(plane), size)
                    }
                    bitmap
                } catch (e: Exception) {
                    bitmap.close()
                    throw e
                }
            }
        } catch (e: Exception) {
            LOGGER.warn("Ignoring unreadable render cache bitmap '{}': {}", file, e.message)
            runCatching { file.deleteExisting() }
            null
        }
    }

    fun storeBitmap(file: Path, bitmap: Bitmap) {
        val parent = file.parent ?: return
        parent.createDirectoriesSafely()
        val temp = parent.resolve("${file.name}.tmp")
        try {
            DataOutputStream(BufferedOutputStream(Files.newOutputStream(temp))).use { output ->
                output.writeInt(BITMAP_MAGIC)
                output.writeInt(BITMAP_FORMAT_VERSION)
                output.writeInt(bitmap.spec.resolution.widthPx)
                output.writeInt(bitmap.spec.resolution.heightPx)
                output.writeInt(bitmap.spec.representation.pixelFormat.planes)
                for (plane in 0..<bitmap.spec.representation.pixelFormat.planes) {
                    output.writeInt(bitmap.linesize(plane))
                    output.writeLong(bitmap.memorySegment(plane).byteSize())
                    copySegmentToOutput(bitmap.memorySegment(plane), output)
                }
            }
            try {
                Files.move(temp, file, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: Exception) {
                Files.move(temp, file, REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            LOGGER.warn("Cannot write render cache bitmap '{}': {}", file, e.message)
            runCatching { temp.deleteExisting() }
        }
    }

    private fun fingerprintProject(projectDir: Path, excludedOutput: Path?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val excludedCacheDir = projectDir.resolve(".cinecred-cache").normalize()
        val excludedOutputAbs = excludedOutput?.takeIf { it.isAbsolute && it.startsWith(projectDir) }?.normalize()
        projectDir.walk()
            .filter(Path::isRegularFile)
            .filterNot { it.normalize().startsWith(excludedCacheDir) }
            .filterNot { excludedOutputAbs != null && it.normalize().startsWith(excludedOutputAbs) }
            .sorted()
            .forEach { file ->
                val rel = projectDir.relativize(file).invariantSeparatorsPathString
                digest.update(rel.toByteArray(Charsets.UTF_8))
                digest.update(0)
                digest.update(file.fileSize().toString().toByteArray(Charsets.UTF_8))
                digest.update(0)
                digest.update(file.getLastModifiedTime().toMillis().toString().toByteArray(Charsets.UTF_8))
                digest.update(0)
            }
        return HexFormat.of().formatHex(digest.digest())
    }

    private fun sha256Hex(text: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))

    private fun copySegmentToOutput(segment: MemorySegment, output: DataOutputStream) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var offset = 0L
        val size = segment.byteSize()
        while (offset < size) {
            val len = minOf(buffer.size.toLong(), size - offset).toInt()
            MemorySegment.copy(segment, JAVA_BYTE, offset, buffer, 0, len)
            output.write(buffer, 0, len)
            offset += len
        }
    }

    private fun copyInputToSegment(input: DataInputStream, segment: MemorySegment, size: Long) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var offset = 0L
        while (offset < size) {
            val len = minOf(buffer.size.toLong(), size - offset).toInt()
            input.readFully(buffer, 0, len)
            MemorySegment.copy(buffer, 0, segment, JAVA_BYTE, offset, len)
            offset += len
        }
    }

    data class Session(val dir: Path) {
        fun chunkFile(chunkIdx: Int, microShiftIdx: Int): Path =
            dir.resolve("chunk-$chunkIdx").resolve("base-$microShiftIdx.bin")

        fun overlayFile(chunkIdx: Int, microShiftIdx: Int, stateKey: DeferredImage.FlashingStateKey): Path =
            dir.resolve("chunk-$chunkIdx").resolve(
                "overlay-$microShiftIdx-${sha256Hex(stateKey.colorIndices.joinToString(","))}.bin"
            )
    }

}
