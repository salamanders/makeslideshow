package info.benjaminhill.slideshow

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifSubIFDDirectory
import mu.KotlinLogging
import net.coobird.thumbnailator.Thumbnails
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.File
import java.util.*

abstract class Clip(val file: File) : Comparable<Clip> {

    protected var orientation: Int
    private val creationDate: Date

    init {
        val dirs = ImageMetadataReader.readMetadata(file)
                .directories
                .filterNotNull()
                .filterNot {
                    it.name.contains("thumbnail", ignoreCase = true)
                }

        // Rotated 90 is the only one supported
        orientation = dirs.firstOrNull { it.containsTag(ExifSubIFDDirectory.TAG_ORIENTATION) }?.let { dir ->
            when (dir.getInt(ExifSubIFDDirectory.TAG_ORIENTATION)) {
                6 -> 90
                else -> null
            }
        } ?: 0

        // Minimum of any date found
        creationDate = dirs.map { dir ->
            listOf(
                    ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL,
                    ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED,
                    ExifSubIFDDirectory.TAG_DATETIME
            ).filter { dir.containsTag(it) }.mapNotNull { dir.getDate(it) }
        }.flatten().min() ?: Date()

    }

    /** Returns the actual content, unmodified, possibly mis-rotated or the wrong size.  (Don't set the thumbnail's size) */
    internal abstract fun getFrames(): Sequence<Thumbnails.Builder<BufferedImage>>

    /** Returns the actual length.  Healthy to memoize this. */
    abstract fun getNumberOfFrames(): Int

    /** Repeat frames as necessary to reach the approx length */
    fun getCorrectedFrames(
            frameLengthMin: Int,
            maxRes: Dimension,
            frameLengthMax: Int = 2 * frameLengthMin
    ): Sequence<BufferedImage> = sequence {
        val duplicator = if (getNumberOfFrames() < frameLengthMin) {
            val duplicateCount = frameLengthMin / getNumberOfFrames()
            if (duplicateCount > 1 && getNumberOfFrames() > 1) {
                LOG.debug { "${file.name} stretching clip by ${duplicateCount}x" }
            }
            duplicateCount
        } else {
            1
        }
        val trimFromBeginning = if (getNumberOfFrames() * duplicator > frameLengthMax) {
            val trim = (getNumberOfFrames() * duplicator) - frameLengthMax
            LOG.debug { "${file.name} trimming $trim frames from beginning of clip (after duplication)" }
            if (trim > frameLengthMax * 2) {
                LOG.info { "${file.name} trimming more than 2x." }
            }
            trim
        } else {
            0
        }
        var outputFrame = 0
        getFrames().forEach { tmb ->
            var t = tmb.size(maxRes.width, maxRes.height)
            if (orientation != 0) {
                t = t.rotate(orientation.toDouble())
            }
            repeat(duplicator) {
                outputFrame++
                if (outputFrame !in 0..trimFromBeginning) {
                    yield(t.asBufferedImage()!!)
                }
            }
        }
    }

    /** For sorting by date */
    override fun compareTo(other: Clip): Int = when {
        this.creationDate < other.creationDate -> -1
        this.creationDate > other.creationDate -> 1
        else -> this.file.nameWithoutExtension.toLowerCase().compareTo(other.file.nameWithoutExtension.toLowerCase())
    }

    companion object {
        val LOG = KotlinLogging.logger {}
    }


}