package info.benjaminhill.slideshow

import info.benjaminhill.util.BasicLogger
import kotlin.time.ExperimentalTime

const val FPS = 30
@ExperimentalTime
val LOG = BasicLogger()

/**
 * @author Benjamin Hill benjaminhill@gmail.com
 *
 * Make a slideshow
 * Read through a folder of ~Desktop/slideshow/clips* (movies or images)
 * Pack them into the left or right side of a 720p output video
 * Customizations: ~Desktop/slideshow/credits.png at the beginning, ~Desktop/slideshow/fullscreen* at the end
 * Great to use the mp4 videos that come from Live Motion
 * Also `mogrify -auto-orient -path ../rotated *.jpg` for the stills
 */
@ExperimentalTime
fun main() {
    // LOG.level = BasicLogger.Companion.LEVEL.INFO
    val ss = SlideShow()
    ss.record()
}

