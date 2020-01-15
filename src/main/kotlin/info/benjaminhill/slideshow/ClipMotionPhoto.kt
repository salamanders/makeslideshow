package info.benjaminhill.slideshow

import com.google.common.primitives.Bytes
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File
import kotlin.time.ExperimentalTime

/**
 * Special type of movie hidden in a Motion Photo.
 *
 */
@ExperimentalTime
class ClipMotionPhoto(file: File) : ClipMovie(file) {

    @ExperimentalTime
    override fun createGrabber(): FFmpegFrameGrabber {
        val content = file.readBytes()
        val idx = Bytes.indexOf(content, TARGET)
        require(idx > -1)
        LOG.fine { "${file.name} successful motion photo." }
        val actualIdx = idx - 4
        content.inputStream(actualIdx, content.size - actualIdx).use { movieIs ->
            return FFmpegFrameGrabber(movieIs)
        }
    }

    companion object {
        private val TARGET = "ftypmp42".toByteArray()
        fun isVideoEmbedded(file: File): Boolean = file.canRead() &&
                (file.name.startsWith("mvimg", true) || file.nameWithoutExtension.endsWith("_mp", true)) &&
                file.readBytes().let {
                    val exists = Bytes.indexOf(it, TARGET) > -1
                    if (!exists) {
                        LOG.warning { "${file.name} doesn't contain a MP4 marker." }
                    }
                    exists
                }
    }

}
