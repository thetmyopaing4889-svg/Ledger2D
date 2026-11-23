package com.myanmar.ledger2d.core.profile

import android.content.Context
import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Strictly bounded JPEG photo sidecar, encrypted together with the existing Cherry backup. */
internal object CherryProfileArchive {
    private const val MAX_COUNT = 3000
    private const val MAX_FILE = 2L * 1024L * 1024L
    private const val MAX_TOTAL = 128L * 1024L * 1024L

    fun write(context: Context, stream: OutputStream) {
        synchronized(ProfilePhotoStore.fileLock) {
            // The enclosing backup's photos.zip entry must remain open.
            val output = object : FilterOutputStream(stream) {
                override fun close() { flush() }
            }
            ZipOutputStream(output).use { zip ->
                val files = ProfilePhotoStore.photoDirectory(context).listFiles().orEmpty()
                    .filter { it.isFile && ProfilePhotoStore.validName.matches(it.name) }
                    .sortedBy { it.name }
                require(files.size <= MAX_COUNT)
                var total = 0L
                files.forEach { file ->
                    require(file.length() in 1..MAX_FILE)
                    total += file.length()
                    require(total <= MAX_TOTAL)
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Checks decompressed lengths, JPEG signatures, file naming and duplicates. */
    fun read(source: InputStream, stage: File? = null): Int {
        if (stage != null) {
            check(!stage.exists() || stage.deleteRecursively())
            check(stage.mkdirs())
        }
        val seen = HashSet<String>()
        var count = 0
        var total = 0L
        ZipInputStream(source.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory &&
                    ProfilePhotoStore.validName.matches(entry.name) &&
                    seen.add(entry.name)) { "Unexpected photo path in backup" }
                count++
                require(count <= MAX_COUNT)
                val target = stage?.let { File(it, entry.name) }
                val output = target?.outputStream()
                var current = 0L
                val header = ByteArray(3)
                var h = 0
                val buf = ByteArray(16 * 1024)
                try {
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        for (i in 0 until n) if (h < 3) header[h++] = buf[i]
                        current += n
                        total += n
                        require(current <= MAX_FILE && total <= MAX_TOTAL) { "Profile archive is too large" }
                        output?.write(buf, 0, n)
                    }
                } finally { output?.close() }
                require(current > 3L && h == 3 &&
                    (header[0].toInt() and 255) == 0xFF &&
                    (header[1].toInt() and 255) == 0xD8 &&
                    (header[2].toInt() and 255) == 0xFF) { "Invalid profile JPEG" }
                zip.closeEntry()
            }
        }
        return count
    }
}
