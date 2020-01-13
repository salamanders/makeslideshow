package info.benjaminhill.slideshow

import net.coobird.thumbnailator.Thumbnails
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class ClipStill(file: File) : Clip(file) {
    // TODO: maxRes: Dimension
    override fun getFrames() = sequence<Thumbnails.Builder<BufferedImage>> {
        LOG.debug { "Still: ${file.name}" }
        val originalBi = ImageIO.read(file)!!
        val width = originalBi.width
        val widthPct = width * SCALE_PCT
        val height = originalBi.height
        val heightPct = height * SCALE_PCT

        for (i in 0 until getNumberOfFrames()) {
            yield(Thumbnails.of(originalBi)
                    .sourceRegion(
                            (i * widthPct).toInt(), (i * heightPct).toInt(),
                            width - 2 * (i * widthPct).toInt(), height - 2 * (i * heightPct).toInt())
            )
        }
    }

    override fun getNumberOfFrames(): Int = 30

    companion object {
        const val SCALE_PCT = 0.002
    }
}
