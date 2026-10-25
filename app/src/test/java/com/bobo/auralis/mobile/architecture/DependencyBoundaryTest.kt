package com.bobo.auralis.mobile.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lightweight source guard for dependency direction, without another architecture framework. */
class DependencyBoundaryTest {
    @Test fun productionDependenciesStayWithinTheirLayer() {
        val root = listOf(File("src/main/java/com/bobo/auralis/mobile"), File("app/src/main/java/com/bobo/auralis/mobile"))
            .first { it.isDirectory }
        val violations = mutableListOf<String>()
        root.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val path = file.relativeTo(root).invariantSeparatorsPath
            val imports = file.readLines().filter { it.startsWith("import com.bobo.auralis.mobile.") }
            imports.forEach { line ->
                val dependency = line.removePrefix("import com.bobo.auralis.mobile.")
                val forbidden = when {
                    path.startsWith("debug/") -> false
                    dependency.startsWith("debug.") -> true
                    listOf("library/", "playback/", "artwork/", "lyrics/").any(path::startsWith) -> dependency.startsWith("ui.")
                    path.startsWith("ui/demo/") -> dependency.startsWith("ui.app.") || dependency.startsWith("ui.library.") ||
                        dependency.startsWith("library.db.") || dependency.startsWith("library.pipeline.") ||
                        dependency.startsWith("playback.PlaybackControllerConnection")
                    else -> false
                }
                if (forbidden) violations += "$path: $line"
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }
}
