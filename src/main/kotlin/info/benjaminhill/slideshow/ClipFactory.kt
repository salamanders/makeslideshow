package info.benjaminhill.slideshow

import mu.KotlinLogging
import java.io.File

object ClipFactory {
    private val LOG = KotlinLogging.logger {}

    fun fileToClip(
            file: File
    ): Clip {
        if (listOf("mp4", "mpeg", "mov").contains(file.extension.toLowerCase())) {
            return ClipMovie(file)
        }

        if (file.extension.toLowerCase() == "gif") {
            return ClipGif(file)
        }

        if (listOf("jpg", "jpeg", "png").contains(file.extension.toLowerCase())) {
            if (file.name.startsWith("mvimg", true) || file.nameWithoutExtension.endsWith("_mp", true)) {
                try {
                    return ClipMotionPhoto(file)
                } catch (e: Exception) {
                    LOG.info { "${file.name} unable to extract motion photo video, ${e.message}" }
                }
            }

            return ClipStill(file)
        }
        error("${file.name} Don't know how to load file")
    }
}