/**
 * @author Benjamin Hill benjaminhill@gmail.com
 */

import mu.KotlinLogging
import org.bytedeco.javacpp.avutil
import org.bytedeco.javacpp.avutil.av_log_set_level
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.nio.file.Paths

private val LOG = KotlinLogging.logger {}
private val converter = Java2DFrameConverter()

/**
 * Make a slideshow
 * Read through a folder of ~Desktop/slideshow/clips* (movies or images)
 * Pack them into the left or right side of a 720p output video
 * Customizations: ~Desktop/slideshow/credits.png at the beginning, ~Desktop/slideshow/fullscreen* at the end
 * Great to use the mp4 videos that come from Live Motion
 * Also `mogrify -auto-orient -path ../rotated *.jpg` for the stills
 */
fun main() {
    av_log_set_level(org.bytedeco.javacpp.avutil.AV_LOG_ERROR) // https://github.com/bytedeco/javacv/issues/780

    val outputRes = Dimension(1280, 720)
    val rootPath = Paths.get(System.getProperty("user.home"), "Desktop", "slideshow")!!
    val outputFile = rootPath.resolve("out.mp4").toFile()
    val clipsDir = rootPath.resolve("clips").toFile()
    val fullscreenDir = rootPath.resolve("fullscreen").toFile()
    val creditsFile = rootPath.resolve("credits.png").toFile()


    FFmpegFrameRecorder(outputFile, outputRes.width, outputRes.height, 0).apply {
        frameRate = 30.0
        videoBitrate = 0 // max
        videoQuality = 0.0 // max
        start()
    }.use { ffr ->
        var frameCount = 0
        LOG.info { "FRAME: $frameCount: Starting recording to '${outputFile.name}' (${ffr.imageWidth}, ${ffr.imageHeight})" }

        // Custom 6 second intro credits
        if (creditsFile.canRead()) {
            LOG.info { "FRAME $frameCount: clip into fullscreen" }
            val images = Clip(creditsFile, outputRes, 90).toImages()
            while (images.hasNext()) {
                drawFrame(fullscreen = images, frameDestination = ffr)
                frameCount++
            }
        }
        LOG.info { "FRAME: $frameCount: Done with optional credits." }


        // Empty lists are nice as fillers
        var leftSlot = emptyList<BufferedImage>().iterator()
        var rightSlot = emptyList<BufferedImage>().iterator()

        var halfScreenClipCount = 0
        clipsDir.walk()
                .filter { it.isFile && it.canRead() && !it.isHidden }
                .sortedBy { it.name }
                .map { Clip(it, Dimension(outputRes.width / 2, outputRes.height), maxFrames = 30 * 4) }
                .forEach { nextClip ->
                    // While all slots are full AND have content, loop and render
                    while (leftSlot.hasNext() && rightSlot.hasNext()) {
                        drawFrame(leftPortrait = leftSlot, rightPortrait = rightSlot, frameDestination = ffr)
                        frameCount++
                    }

                    // Drop in the nextClip into first free slot
                    if (!leftSlot.hasNext()) {
                        leftSlot = nextClip.toImages()
                        LOG.info { "FRAME $frameCount: slotting next clip $nextClip into leftSlot" }
                        halfScreenClipCount++
                    } else if (!rightSlot.hasNext()) {
                        rightSlot = nextClip.toImages()
                        LOG.info { "FRAME: $frameCount: slotting next clip $nextClip into rightSlot" }
                        halfScreenClipCount++
                    } else {
                        LOG.warn { "Why are both areas full?" }
                    }
                }

        LOG.info { "FRAME: $frameCount: Finishing out left/right sides" }
        while (leftSlot.hasNext() || rightSlot.hasNext()) {
            drawFrame(leftPortrait = leftSlot, rightPortrait = rightSlot, frameDestination = ffr)
        }
        LOG.info { "FRAME: $frameCount: Done with $halfScreenClipCount half screen clips." }

        var fullScreenClipCount = 0
        fullscreenDir.walk()
                .filter { it.isFile && it.canRead() && !it.isHidden }
                .sortedBy { it.name }
                .map { Clip(it, outputRes, maxFrames = 30 * 100).toImages() }
                .forEach { images ->
                    LOG.info { "$frameCount clip into leftPortrait (as fullscreen)" }
                    while (images.hasNext()) {
                        drawFrame(fullscreen = images, frameDestination = ffr)
                        frameCount++
                        fullScreenClipCount++
                    }
                }
        LOG.info { "FRAME: $frameCount: Done with $fullScreenClipCount full screen clips." }

        ffr.stop()
        LOG.info { "FRAME: $frameCount: done." }
    }
}


/**
 * Plops whatever images are available into a single frame and records it.
 * Full screen takes priority over half screen
 */
fun drawFrame(
        leftPortrait: Iterator<BufferedImage> = emptyList<BufferedImage>().iterator(),
        rightPortrait: Iterator<BufferedImage> = emptyList<BufferedImage>().iterator(),
        fullscreen: Iterator<BufferedImage> = emptyList<BufferedImage>().iterator(),
        frameDestination: FFmpegFrameRecorder) {

    require(leftPortrait.hasNext() || rightPortrait.hasNext() || fullscreen.hasNext())

    val frame = BufferedImage(frameDestination.imageWidth, frameDestination.imageHeight, BufferedImage.TYPE_INT_ARGB)
    frame.createGraphics()!!.apply {

        if (fullscreen.hasNext()) {
            val bi = fullscreen.next()
            val dx = (frame.width - bi.width) / 2
            val dy = (frame.height - bi.height) / 2
            drawImage(bi, dx, dy, null)
        } else {
            if (leftPortrait.hasNext()) {
                val bi = leftPortrait.next()
                // Center in your side
                val dx = (frame.width / 2 - bi.width) / 2
                val dy = (frame.height - bi.height) / 2
                drawImage(bi, dx, dy, null)
            }
            if (rightPortrait.hasNext()) {
                // Center in your side
                val bi = rightPortrait.next()
                val dx = (frame.width / 2 - bi.width) / 2
                val dy = (frame.height - bi.height) / 2
                drawImage(bi, frame.width / 2 + dx, dy, null)
            }
        }

        dispose()
    }
    frameDestination.record(converter.convert(frame), avutil.AV_PIX_FMT_ARGB)
}


