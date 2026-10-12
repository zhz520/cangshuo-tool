package com.cangshuo.toolbox.feature.kinship.data

import android.content.res.AssetManager
import com.cangshuo.toolbox.feature.kinship.domain.KinshipQuery
import com.cangshuo.toolbox.feature.kinship.domain.KinshipRepository

class LocalKinshipRepository(private val assets: AssetManager) : KinshipRepository {
    private val engine: KinshipEngine by lazy {
        KinshipEngine(
            KinshipData.load { path ->
                assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
            },
        )
    }

    override fun query(query: KinshipQuery): List<String> = engine.query(query)
}
