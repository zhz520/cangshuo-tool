package com.cangshuo.toolbox.feature.coinflip.data

import com.cangshuo.toolbox.feature.coinflip.domain.*
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class SecureCoinRandomSource : CoinRandomSource {
    private val random = SecureRandom()
    override fun nextHeads() = random.nextBoolean()
}

class LocalCoinRepository(
    private val source: CoinRandomSource = SecureCoinRandomSource(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CoinRepository {
    override suspend fun flip(count: Int): List<CoinSide> {
        require(count in 1..CoinPolicy.MAX_BATCH)
        return withContext(dispatcher) {
            List(count) {
                ensureActive()
                if (source.nextHeads()) CoinSide.HEADS else CoinSide.TAILS
            }
        }
    }
}
