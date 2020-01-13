package info.benjaminhill.slideshow

import com.google.common.primitives.Bytes
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File

class ClipMotionPhoto(file: File) : ClipMovie(file) {

    init {
        val content = file.readBytes()
        val target = "ftypmp42".toByteArray()
        require(Bytes.indexOf(content, target) > -1) { "${file.name} 'ftypmp42' not found in file." }
    }

    override fun createGrabber(): FFmpegFrameGrabber {
        val content = file.readBytes()
        val target = "ftypmp42".toByteArray()
        val idx = Bytes.indexOf(content, target)
        require(idx > -1)
        LOG.debug { "${file.name} successful motion photo." }
        val actualIdx = idx - 4
        content.inputStream(actualIdx, content.size - actualIdx).use { movieIs ->
            return FFmpegFrameGrabber(movieIs)
        }
    }

}
