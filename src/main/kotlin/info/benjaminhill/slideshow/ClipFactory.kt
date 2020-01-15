package info.benjaminhill.slideshow

import java.io.File
import kotlin.time.ExperimentalTime

/**
 * Loads the correct Clip subclass based on media type (with some fallbacks)
 */
@ExperimentalTime
object ClipFactory {

    fun fileToClip(file: File): Clip = when {
        listOf("mp4", "mpeg", "mov").contains(file.extension.toLowerCase()) -> ClipMovie(file)
        listOf("gif").contains(file.extension.toLowerCase()) -> ClipGif(file)
        listOf("jpg", "jpeg").contains(file.extension.toLowerCase()) && ClipMotionPhoto.isVideoEmbedded(file) -> ClipMotionPhoto(file)
        else -> ClipStill(file)
    }
}