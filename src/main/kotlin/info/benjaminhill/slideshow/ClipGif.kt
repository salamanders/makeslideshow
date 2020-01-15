package info.benjaminhill.slideshow

import net.coobird.thumbnailator.Thumbnails
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import kotlin.time.ExperimentalTime

/**
 * StackOverflow's advice on how to read an animated GIF file
 * Ignores frame duration
 */
@ExperimentalTime
class ClipGif(file: File) : Clip(file) {

    private val reader: ImageReader by lazy {
        val reader = ImageIO.getImageReadersByFormatName("gif").next() as ImageReader
        reader.setInput(ImageIO.createImageInputStream(file), false)
        reader
    }

    private val nof: Int by lazy {
        reader.getNumImages(true)
    }

    override fun getNumberOfFrames(): Int = nof

    override fun getFrames() = sequence<Thumbnails.Builder<BufferedImage>> {
        LOG.info { "Animated GIF: ${file.name}, length: $nof" }

        lateinit var master: BufferedImage
        for (i in 0 until nof) {
            val image = reader.read(i)
            val metadata = reader.getImageMetadata(i)
            val tree: Node = metadata.getAsTree("javax_imageio_gif_image_1.0")
            val children: NodeList = tree.childNodes
            for (j in 0 until children.length) {
                val nodeItem: Node = children.item(j)
                if (nodeItem.nodeName == "ImageDescriptor") {
                    val imageAttr = imageAtt.indices.map { k ->
                        imageAtt[k] to nodeItem.attributes.getNamedItem(imageAtt[k]).nodeValue.toInt()
                    }.toMap()
                    if (i == 0) {
                        master = BufferedImage(
                                imageAttr["imageWidth"] ?: error("Missing imageWidth"),
                                imageAttr["imageHeight"] ?: error("Missing imageHeight"),
                                BufferedImage.TYPE_INT_ARGB)
                    }
                    master.graphics.drawImage(
                            image,
                            imageAttr["imageLeftPosition"] ?: error("Missing imageLeftPosition"),
                            imageAttr["imageTopPosition"] ?: error("Missing imageTopPosition"),
                            null)
                }
            }
            yield(Thumbnails.of(master))
        }
        reader.dispose()
    }.constrainOnce()


    companion object {
        private val imageAtt = arrayOf(
                "imageLeftPosition",
                "imageTopPosition",
                "imageWidth",
                "imageHeight"
        )
    }

}
