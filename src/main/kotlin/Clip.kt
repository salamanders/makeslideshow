import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.google.common.primitives.Bytes
import mu.KotlinLogging
import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.filters.Canvas
import net.coobird.thumbnailator.geometry.Positions
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameConverter
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.File
import java.util.*

class Clip(private val file: File, private val maxRes: Dimension, private val maxFrames: Int) {

    private var orientation: Int = 0
    private var original = Date()
    private var type = ""

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
                    if (dir.containsTag(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)) {
                        original = dir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
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

    private fun processStill(): Iterator<BufferedImage> = iterator {
        LOG.info { "Still: ${file.name}" }
        type += ".still"
        val scaled =
                Canvas(maxRes.width, maxRes.height, Positions.CENTER).apply(
                        Thumbnails.of(file)
                                .size(maxRes.width, maxRes.height)
                                .asBufferedImage())

        for (i in 0 until maxFrames) {
            // not proportional :/
            val trimmed = scaled.getSubimage(i, i, scaled.width - (2 * i), scaled.height - (2 * i))
            var t = Thumbnails.of(trimmed).size(maxRes.width, maxRes.height)
            if (orientation != 0) {
                t = t.rotate(orientation.toDouble())
            }
            yield(t.asBufferedImage())
        }
    }

    private fun processMovie(): Iterator<BufferedImage> {
        LOG.info { "Video: ${file.name}" }
        type += ".movie"
        return inputStreamToFrames(FFmpegFrameGrabber(file))
    }

    private fun processMotionPhoto(): Iterator<BufferedImage> {
        LOG.info { "Motion Photo: ${file.name}" }

        val content = file.readBytes()
        val target = hexStringToByteArray("FFD900000018") + "ftypmp42".toByteArray()
        val idx = Bytes.indexOf(content, target)
        return if (idx > -1) {
            type += ".motion"

            content.inputStream(idx + 2, content.size - (idx + 2)).use { movieIs ->
                inputStreamToFrames(FFmpegFrameGrabber(movieIs))
            }
        } else {
            LOG.warn { "Falling back to still: ${file.name}" }
            type += ".fallback"
            processStill()
        }
    }

    /**
     * With any input stream (direct from file or parsed from Live/Motion Photo)
     * Generate BI frames
     * TODO: Toss in the central still image
     */
    private fun inputStreamToFrames(grabber: FFmpegFrameGrabber): Iterator<BufferedImage> {
        grabber.use { g ->
            return iterator {

                g.start()
                val rotation = (g.getVideoMetadata("rotate") ?: "0").toInt()
                val skip = Math.max(0, g.lengthInVideoFrames - maxFrames)
                LOG.info { "${file.name}: Getting ${g.lengthInVideoFrames} frames with rotation:$orientation, skipping $skip" }

                repeat(skip) {
                    g.grabImage()
                }

                while (true) {
                    yield(g.grabImage()?.let { nextFrame ->
                        var tmb = Thumbnails.of(converter.get().convert(nextFrame)!!)
                                .size(maxRes.width, maxRes.height)!!
                        if (rotation != 0) {
                            tmb = tmb.rotate(rotation.toDouble())
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

    override fun toString(): String = "Clip {name:${file.name}, rot:$orientation, type:$type}"

    companion object {
        private val LOG = KotlinLogging.logger {}
        fun hexStringToByteArray(str: String) = ByteArray(str.length / 2) { str.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
