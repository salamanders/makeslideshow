package info.benjaminhill.slideshow

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
 * @param minClipFrames all videos and clips render at least this many frames
 */
class SlideShow(
        private val outputRes: Dimension = Dimension(1280, 720),
        private val rootPath: Path = Paths.get(System.getProperty("user.home"), "Desktop", "slideshow").also {
            require(it.toFile().exists()) { "Missing slideshow folder at ${it.toFile().canonicalPath}" }
        },
        private val outputFile: File = rootPath.resolve("slideshow.mp4").toFile(),
        private val clipsDir: File = rootPath.resolve("clips").toFile(),
        private val fullscreenDir: File = rootPath.resolve("fullscreen").toFile(),
        private val creditsFile: File = rootPath.resolve("credits.png").toFile(),
        private val minClipFrames: Int = FPS * 2

) {

    private lateinit var ffr: FFmpegFrameRecorder

    fun record() {
        avutil.av_log_set_level(avutil.AV_LOG_ERROR) // https://github.com/bytedeco/javacv/issues/780
        ffr = FFmpegFrameRecorder(outputFile, outputRes.width, outputRes.height, 0).apply {
            frameRate = FPS.toDouble()
            videoBitrate = 0 // max
            videoQuality = 0.0 // max
            start()
        }

        ffr.use { ffr ->
            var frameCount = 0
            LOG.info { "FRAME: $frameCount: Starting recording to '${outputFile.name}' (${ffr.imageWidth}, ${ffr.imageHeight})" }

            // Custom X second intro credits
            if (creditsFile.canRead()) {
                LOG.debug { "FRAME $frameCount: clip into fullscreen" }
                ClipStill(creditsFile)
                        .getCorrectedFrames(frameLengthMin = minClipFrames, maxRes = outputRes)
                        .forEach { frame ->
                            drawFullScreen(frame)
                            frameCount++
                        }
            }
            LOG.debug { "FRAME: $frameCount: Done with optional credits." }

            // Empty lists are nice as fillers
            val leftSlot = mutableListOf<BufferedImage>()
            val rightSlot = mutableListOf<BufferedImage>()

            var halfScreenClipCount = 0
            val halfSize = Dimension(outputRes.width / 2, outputRes.height)
            clipsDir.walk()
                    .filter { it.isFile && it.canRead() && !it.isHidden }
                    .map { ClipFactory.fileToClip(it) }
                    .sorted() // Hopefully no memory issues
                    .forEach { halfScreenClip ->
                        // drop the halfScreenClip into the first free slot
                        when {
                            leftSlot.isEmpty() -> leftSlot.addAll(halfScreenClip.getCorrectedFrames(minClipFrames, maxRes = halfSize))
                            rightSlot.isEmpty() -> rightSlot.addAll(halfScreenClip.getCorrectedFrames(minClipFrames, maxRes = halfSize))
                            else -> LOG.warn { "Why were no slots empty?" }
                        }
                        halfScreenClipCount++

                        // While all slots are full AND have content, render.  Then add the next clip.
                        while (leftSlot.isNotEmpty() && rightSlot.isNotEmpty()) {
                            drawSideBySide(leftSlot.removeOrNull(), rightSlot.removeOrNull())
                            frameCount++
                        }
                    }
            LOG.debug { "FRAME: $frameCount: Finishing out left/right sides" }
            while (leftSlot.isNotEmpty() || rightSlot.isNotEmpty()) {
                drawSideBySide(leftSlot.removeOrNull(), rightSlot.removeOrNull())
                frameCount++
            }
            LOG.debug { "FRAME: $frameCount: Done with $halfScreenClipCount half screen clips." }

            var fullScreenClipCount = 0
            fullscreenDir.walk()
                    .filter { it.isFile && it.canRead() && !it.isHidden }
                    .map { ClipFactory.fileToClip(it) }
                    .sorted()
                    .forEach { fullScreenClip ->
                        LOG.debug { "$frameCount clip into leftPortrait (as fullscreen)" }
                        fullScreenClipCount++
                        fullScreenClip.getCorrectedFrames(
                                frameLengthMin = minClipFrames,
                                frameLengthMax = 4 * minClipFrames,
                                maxRes = outputRes
                        ).forEach {
                            drawFullScreen(it)
                            frameCount++
                        }
                    }
            LOG.debug { "FRAME: $frameCount: Done with $fullScreenClipCount full screen clips." }

            ffr.stop()
            LOG.info { "FRAME: $frameCount: done.  ${frameCount.toDouble() / (FPS * 60)}min." }
        }
    }

    private fun drawFullScreen(source: BufferedImage) {
        val frame = BufferedImage(ffr.imageWidth, ffr.imageHeight, BufferedImage.TYPE_INT_ARGB)
        frame.createGraphics()!!.apply {
            val dx = (frame.width - source.width) / 2
            val dy = (frame.height - source.height) / 2
            drawImage(source, dx, dy, null)
            dispose()
        }
        ffr.record(converter.convert(frame), avutil.AV_PIX_FMT_ARGB)
    }

    private fun drawSideBySide(
            left: BufferedImage?,
            right: BufferedImage?
    ) {
        require(left != null || right != null)
        val frame = BufferedImage(ffr.imageWidth, ffr.imageHeight, BufferedImage.TYPE_INT_ARGB)
        frame.createGraphics()!!.apply {
            if (left != null) {
                // Center in your side
                val dx = (frame.width / 2 - left.width) / 2
                val dy = (frame.height - left.height) / 2
                drawImage(left, dx, dy, null)
            }
            if (right != null) {
                // Center in your side
                val dx = (frame.width / 2 - right.width) / 2
                val dy = (frame.height - right.height) / 2
                drawImage(right, frame.width / 2 + dx, dy, null)
            }

            dispose()
        }
        ffr.record(converter.convert(frame), avutil.AV_PIX_FMT_ARGB)
    }


    companion object {
        private val LOG = KotlinLogging.logger {}
        private val converter = Java2DFrameConverter()
    }

}