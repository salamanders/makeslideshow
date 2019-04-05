import mu.KotlinLogging
import org.bytedeco.javacpp.avutil
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 *
 * @param outputRes 720p
 * @param rootPath Where to look for all folders and to render to
 * @param outputFile out.mp4
 * @param clipsDir folder to scan for media of any type
 * @param fullscreenDir folder to scan for fullscreen videos
 * @param creditsFile single image to use at the beginning
 * @param maxClipFrames all videos and clips render this many frames, long videos skip from beginning if larger, short videos aren't expanded, stills are this long
 */
class Slideshow(
        private val outputRes: Dimension = Dimension(1280, 720),
        private val rootPath: Path = Paths.get(System.getProperty("user.home"), "Desktop", "slideshow")!!,
        private val outputFile: File = rootPath.resolve("out.mp4").toFile(),
        private val clipsDir: File = rootPath.resolve("clips").toFile(),
        private val fullscreenDir: File = rootPath.resolve("fullscreen").toFile(),
        private val creditsFile: File = rootPath.resolve("credits.png").toFile(),
        private val maxClipFrames: Int = 30 * 3
) {
    fun record() {
        avutil.av_log_set_level(avutil.AV_LOG_ERROR) // https://github.com/bytedeco/javacv/issues/780
        FFmpegFrameRecorder(outputFile, outputRes.width, outputRes.height, 0).apply {
            frameRate = 30.0
            videoBitrate = 0 // max
            videoQuality = 0.0 // max
            start()
        }.use { ffr ->
            var frameCount = 0
            LOG.info { "FRAME: $frameCount: Starting recording to '${outputFile.name}' (${ffr.imageWidth}, ${ffr.imageHeight})" }

            // Custom X second intro credits
            if (creditsFile.canRead()) {
                LOG.info { "FRAME $frameCount: clip into fullscreen" }
                val images = Clip(creditsFile, outputRes, 30 * 5).toImages()
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
                    .map { Clip(it, Dimension(outputRes.width / 2, outputRes.height), maxFrames = maxClipFrames) }
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
                    .map { Clip(it, outputRes, maxFrames = 30 * 11).toImages() }
                    .forEach { images ->
                        LOG.info { "$frameCount clip into leftPortrait (as fullscreen)" }
                        fullScreenClipCount++
                        while (images.hasNext()) {
                            drawFrame(fullscreen = images, frameDestination = ffr)
                            frameCount++

                        }
                    }
            LOG.info { "FRAME: $frameCount: Done with $fullScreenClipCount full screen clips." }

            ffr.stop()
            LOG.info { "FRAME: $frameCount: done." }
        }
    }

    companion object {
        private val LOG = KotlinLogging.logger {}
        private val converter = Java2DFrameConverter()

        /**
         * Plops whatever images are available into a single frame and records it.
         * Full screen takes priority over half screen
         */
        private fun drawFrame(
                leftPortrait: Iterator<BufferedImage> = emptyList<BufferedImage>().iterator(),
                rightPortrait: Iterator<BufferedImage> = emptyList<BufferedImage>().iterator(),
                fullscreen: Iterator<BufferedImage> = emptyList<BufferedImage>().iterator(),
                frameDestination: FFmpegFrameRecorder
        ) {

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
    }
}