package info.benjaminhill.slideshow

import net.coobird.thumbnailator.Thumbnails
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.ExperimentalTime

@ExperimentalTime
class ClipStill(file: File) : Clip(file) {

    override fun getFrames() = sequence<Thumbnails.Builder<BufferedImage>> {
        LOG.fine { "Still: ${file.name}, zooming over ${getNumberOfFrames()} frames." }
        val originalBi = ImageIO.read(file)!!
        val width = originalBi.width
        val widthPct = width * SCALE_PCT
        val height = originalBi.height
        val heightPct = height * SCALE_PCT

        for (i in 0 until getNumberOfFrames()) {
            // Gradual zoom towards center by trimming off the edges equally
            yield(Thumbnails.of(originalBi)
                    .sourceRegion(
                            (i * widthPct).toInt(), (i * heightPct).toInt(),
                            width - 2 * (i * widthPct).toInt(), height - 2 * (i * heightPct).toInt())
            )
        }
    }

    // Reasonable 2 second clip
    override fun getNumberOfFrames(): Int = 60

    companion object {
        const val SCALE_PCT = 0.002
    }
}
