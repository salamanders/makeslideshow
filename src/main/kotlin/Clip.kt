import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.google.common.primitives.Bytes
import mu.KotlinLogging
import net.coobird.thumbnailator.Thumbnails
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameConverter
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.Dimension
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.imageio.ImageIO

/** Wrapper for a media source - movie, image, Live Photo, Motion Photo */
class Clip(private val file: File, private val maxRes: Dimension, private val maxFrames: Int) {

    private var orientation: Int = 0
    private var original: Date? = null
    private val type = mutableListOf<String>()

    init {
        ImageMetadataReader.readMetadata(file)
                .directories
                .filterNotNull()
                .filterNot {
                    it.name.contains("thumbnail", ignoreCase = true)
                }
                .forEach { dir ->
                    if (dir.containsTag(ExifSubIFDDirectory.TAG_ORIENTATION)) {
                        when (dir.getInt(ExifSubIFDDirectory.TAG_ORIENTATION)) {
                            6 -> {
                                orientation = 90
                            }
                        }
                    }
                    listOf(
                            ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL,
                            ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED,
                            ExifSubIFDDirectory.TAG_DATETIME
                    ).forEach { tag ->
                        if (original == null && dir.containsTag(tag)) {
                            original = dir.getDate(tag)
                        }
                    }
                }
    }

    /**
     * If a movie then direct to small images
     * If an image then copy to 2 seconds of images
     * If a Live Motion or Motion Photo, extract the motion and use both
     * Resulting images should be oriented AND sized
     */
    fun toImages(): Iterator<BufferedImage> = when {
        file.name.startsWith("mvimg", true) && file.extension.endsWith("jpg", true) -> processMotionPhoto()
        listOf("mp4", "mpeg", "mov").contains(file.extension.toLowerCase()) -> processMovie()
        listOf("jpg", "jpeg", "png").contains(file.extension.toLowerCase()) -> processStill()
        else -> emptyList<BufferedImage>().iterator().also {
            LOG.warn { "Don't know how to handle ${file.name}" }
        }
    }

    /** Do that gradual 50% zoom-to-center really smoothly */
    private fun processStill(): Iterator<BufferedImage> = iterator {
        LOG.info { "Still: ${file.name}" }
        type += "still"
        val src = ImageIO.read(file)!!

        // Optimization for overly large images
        /*
        if (src.width > 2 * maxRes.width || src.height > 2 * maxRes.height) {
            LOG.info { "Scaling down oversize image prior to zoom (${src.width}x${src.height}) " }
            src = Thumbnails.of(src).size(maxRes.width * 2, maxRes.height * 2).asBufferedImage()
        }
         */

        var lastScale = 0
        for (frameIdx in 0 until maxFrames) {
            val pct = frameIdx / (maxFrames * 10.0) // just 1/10th zoom
            val scale = 1.0 + pct

            val at = AffineTransform()
            at.translate(src.width / 2.0, src.height / 2.0)
            at.scale(scale, scale)
            at.translate(-src.width / 2.0, -src.height / 2.0)

            Math.round(scale * 100).toInt().let {
                if (lastScale != it) {
                    print("($frameIdx z:$it)")
                    lastScale = it
                }
            }

            // VALUE_INTERPOLATION_BICUBIC is best.  Very slow however, and if we are going to shrink it all down anyway...
            val ato = AffineTransformOp(at, RenderingHints(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR))
            var t = Thumbnails.of(ato.filter(src, null)).size(maxRes.width, maxRes.height)
            if (orientation != 0) {
                t = t.rotate(orientation.toDouble())
            }
            yield(t.asBufferedImage())
        }
        println()
    }

    /** Direct transfer */
    private fun processMovie(): Iterator<BufferedImage> {
        LOG.info { "Video: ${file.name}" }
        type += "movie"
        return frameGrabberToImages(FFmpegFrameGrabber(file))
    }

    /** Attempt to extract the internal video */
    private fun processMotionPhoto(): Iterator<BufferedImage> {
        LOG.info { "Motion Photo: ${file.name}" }

        val content = file.readBytes()
        val target = hexStringToByteArray("FFD900000018") + "ftypmp42".toByteArray()
        val idx = Bytes.indexOf(content, target)
        return if (idx > -1) {
            type += "motion"
            content.inputStream(idx + 2, content.size - (idx + 2)).use { movieIs ->
                frameGrabberToImages(FFmpegFrameGrabber(movieIs))
            }
        } else {
            LOG.warn { "Falling back to still: ${file.name}" }
            type += "fallbackToStill"
            processStill()
        }
    }

    /**
     * With any FFmpegFrameGrabber (direct from file or parsed from Live/Motion Photo)
     * Generate BI frames
     * TODO: Toss in the central still image?
     */
    private fun frameGrabberToImages(grabber: FFmpegFrameGrabber): Iterator<BufferedImage> {
        grabber.use { g ->
            return iterator {

                g.start()
                val videoRotation = (g.getVideoMetadata("rotate") ?: "0").toInt()
                if (videoRotation != orientation) {
                    LOG.warn { "File rotation:$orientation but videoRotation:$videoRotation" }
                }
                val skip = Math.max(0, g.lengthInVideoFrames - maxFrames)
                LOG.info { "${file.name}: frames:${g.lengthInVideoFrames}, rotation:$orientation" }
                if (skip > 0) {
                    LOG.warn { "${file.name} skipping first $skip of ${g.lengthInVideoFrames} frames." }
                }

                repeat(skip) {
                    g.grabImage()
                }

                while (true) {
                    yield(g.grabImage()?.let { nextFrame ->
                        var tmb = Thumbnails.of(converter.get().convert(nextFrame)!!)
                                .size(maxRes.width, maxRes.height)!!
                        if (videoRotation != 0) {
                            tmb = tmb.rotate(videoRotation.toDouble())
                        }
                        tmb.asBufferedImage()!!
                    } ?: break)
                }
                g.stop()
            }
        }
    }

    private val converter = object : ThreadLocal<FrameConverter<BufferedImage>>() {
        override fun initialValue() = Java2DFrameConverter()
    }

    override fun toString(): String = "Clip {name:${file.name}, rot:$orientation, type:${type.joinToString(">")}}"

    companion object {
        private val LOG = KotlinLogging.logger {}
        fun hexStringToByteArray(str: String) = ByteArray(str.length / 2) { str.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
