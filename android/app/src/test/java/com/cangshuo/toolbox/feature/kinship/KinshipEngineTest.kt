package com.cangshuo.toolbox.feature.kinship

import com.cangshuo.toolbox.feature.kinship.domain.KinshipQuery
import com.cangshuo.toolbox.feature.kinship.domain.KinshipSex
import com.cangshuo.toolbox.feature.kinship.domain.KinshipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KinshipEngineTest {
    private val engine = KinshipTestFiles.engine

    private fun titles(
        text: String,
        target: String = "",
        sex: KinshipSex = KinshipSex.UNSPECIFIED,
        type: KinshipType = KinshipType.TITLE,
        reverse: Boolean = false,
        optimal: Boolean = false,
    ) = engine.query(KinshipQuery(text, target, sex, type, reverse, optimal))

    @Test
    fun classicChainsReturnTheExpectedTitles() {
        assertEquals(listOf("奶奶"), titles("爸爸的妈妈"))
        assertEquals(listOf("伯父"), titles("爸爸的哥哥"))
        assertEquals(listOf("岳父"), titles("老婆的爸爸"))
        assertEquals(listOf("曾祖母"), titles("爸爸的爸爸的妈妈"))
        assertEquals(listOf("舅表哥", "舅表弟"), titles("舅舅的儿子"))
        assertEquals(listOf("姑表哥", "姑表弟"), titles("姑妈的儿子"))
        assertEquals(listOf("外甥"), titles("姐姐的儿子"))
        assertEquals(listOf("弟妹"), titles("弟弟的媳妇"))
        assertEquals(listOf("自己"), titles("我"))
    }

    @Test
    fun naturalLanguageReverseChainAndPairModesWork() {
        assertEquals(listOf("伯父"), titles("爸爸的哥哥叫什么"))
        assertEquals(listOf("儿子"), titles("爸爸", sex = KinshipSex.MALE, reverse = true))
        assertEquals(listOf("爸爸的妈妈"), titles("爸爸的妈妈", type = KinshipType.CHAIN))
        assertEquals(listOf("夫妻"), titles("爸爸", target = "妈妈", type = KinshipType.PAIR))
        assertEquals(listOf("姨姐", "姨妹"), titles("妈妈的妹妹的女儿"))
    }

    @Test
    fun unknownNamesAndLongerChainsStayEmpty() {
        assertTrue(titles("不存在的人").isEmpty())
        assertTrue(titles("爸爸的爸爸的爸爸的爸爸的爸爸的爸爸的爸爸").isEmpty())
    }

    @Test
    fun differentialCorpusMatchesTheReferenceImplementation() {
        val failures = ArrayList<String>()
        var checked = 0
        for (line in KinshipTestFiles.goldenLines()) {
            if (line.isBlank()) continue
            val parts = line.split('\t')
            val expected = if (parts[6].isEmpty()) emptyList() else parts[6].split('\u001f')
            val actual = engine.query(
                KinshipQuery(
                    text = parts[0],
                    target = parts[1],
                    sex = when (parts[2].toInt()) {
                        1 -> KinshipSex.MALE
                        0 -> KinshipSex.FEMALE
                        else -> KinshipSex.UNSPECIFIED
                    },
                    type = when (parts[3]) {
                        "chain" -> KinshipType.CHAIN
                        "pair" -> KinshipType.PAIR
                        else -> KinshipType.TITLE
                    },
                    reverse = parts[4] == "1",
                    optimal = parts[5] == "1",
                ),
            )
            checked++
            if (actual != expected) {
                if (failures.size < 8) {
                    failures.add("${parts[0]} / ${parts[3]} -> expected $expected, got $actual")
                }
            }
        }
        assertTrue("checked $checked cases, failures: $failures", failures.isEmpty())
    }
}
