package com.cangshuo.toolbox.feature.kinship

import com.cangshuo.toolbox.feature.kinship.data.KinshipData
import com.cangshuo.toolbox.feature.kinship.data.KinshipEngine
import java.io.File

/** Locates the app module from the test working directory and loads the shipped tables. */
internal object KinshipTestFiles {
    private val moduleDir: File by lazy {
        var dir: File? = File(System.getProperty("user.dir")!!).absoluteFile
        while (dir != null && !File(dir, "src/main/assets/kinship/mode.json").isFile) {
            dir = dir.parentFile
        }
        requireNotNull(dir) { "cannot locate the app module from ${System.getProperty("user.dir")}" }
    }

    val engine: KinshipEngine by lazy {
        KinshipEngine(
            KinshipData.load { path -> File(moduleDir, "src/main/assets/$path").readText(Charsets.UTF_8) },
        )
    }

    fun goldenLines(): List<String> =
        File(moduleDir, "src/test/resources/kinship/golden.tsv").readLines(Charsets.UTF_8)
}
