package info.benjaminhill.slideshow

import net.coobird.thumbnailator.Thumbnails
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameConverter
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.BufferedImage
import java.io.File

/**
 * With any FFmpegFrameGrabber (direct from file or parsed from Live/Motion Photo).
 * Apple's Live Photos will already be split into standalone video files from Google Photos album download.
 */
open class ClipMovie(file: File) : Clip(file) {

    override fun getFrames(): Sequence<Thumbnails.Builder<BufferedImage>> {
        grabber.use { g ->
            return sequence {
                g.start()
                val videoRotation = g.getVideoMetadata("rotate")?.toInt() ?: orientation
                if (videoRotation != orientation) {
                    LOG.info { "File rotation:$orientation overridden by videoRotation:$videoRotation" }
                    orientation = videoRotation
                }
                while (true) {
                    yield(Thumbnails.of(converter.get().convert(g.grabImage() ?: break)))
                }
                g.stop()
            }.constrainOnce()
        }
    }

    private val nof: Int by lazy {
        grabber.start()
        grabber.lengthInVideoFrames
    }

    override fun getNumberOfFrames(): Int = nof

    private val grabber: FFmpegFrameGrabber by lazy {
        createGrabber()
    }

    protected open fun createGrabber() = FFmpegFrameGrabber(file)

    private val converter = object : ThreadLocal<FrameConverter<BufferedImage>>() {
        override fun initialValue() = Java2DFrameConverter()
    }

}