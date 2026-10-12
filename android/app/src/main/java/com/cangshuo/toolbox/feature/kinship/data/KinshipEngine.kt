package com.cangshuo.toolbox.feature.kinship.data

import com.cangshuo.toolbox.feature.kinship.domain.KinshipQuery
import com.cangshuo.toolbox.feature.kinship.domain.KinshipType
import java.util.Locale

/**
 * Kotlin port of mumuy/relationship (MIT) — selectors, id arithmetic, name lookup and the
 * natural-language expression rules — reading the shipped compact tables. Behaviour is
 * verified against a differential corpus generated from the reference implementation.
 */
class KinshipEngine(private val data: KinshipData) {

    private val textAttr = listOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

    private val similar = linkedMapOf(
        "晜" to "兄", "哥" to "兄", "姐" to "姊", "侄" to "姪", "婿" to "壻", "嬷" to "嫲",
        "祖父" to "王父", "祖母" to "王母", "孙女婿" to "孙婿", "甥女婿" to "甥婿",
        "侄女婿" to "侄婿", "孙媳妇" to "孙妇", "甥媳妇" to "甥妇", "侄媳妇" to "侄妇",
        "弟媳妇" to "弟妇",
    )

    private data class FilterRule(val pattern: Regex, val replacement: String, val global: Boolean = false)

    private val filterRules = listOf(
        FilterRule(Regex("^(.+)&o([^#]+)&l"), "\$1\$2"),
        FilterRule(Regex("^(.+)&l([^#]+)&o"), "\$1\$2"),
        FilterRule(Regex("^(.+)?,o([sb](&\\d+)?)([^#]+)&l"), "\$1,x\$2\$4"),
        FilterRule(Regex("^(.+)?,l([sb](&\\d+)?)([^#]+)&o"), "\$1,x\$2\$4"),
        FilterRule(Regex("(,o[sb](&\\d+)?)+(,o[sb])(&\\d+)?"), "\$3\$4"),
        FilterRule(Regex("(,l[sb](&\\d+)?)+(,l[sb])(&\\d+)?"), "\$3\$4"),
        FilterRule(Regex("^(.*,[fh1])(,[olx][sb](&\\d+)?)+,[olx]b(.*)\$"), "\$1,xb\$4#\$1\$4"),
        FilterRule(Regex("^(.*,[mw0])(,[olx][sb](&\\d+)?)+,[olx]s(.*)\$"), "\$1,xs\$4#\$1\$4"),
        FilterRule(Regex("(,[fh1])(,[olx][sb](&\\d+)?)+,[olx]s"), "\$1,xs", global = true),
        FilterRule(Regex("(,[mw0])(,[olx][sb](&\\d+)?)+,[olx]b"), "\$1,xb", global = true),
        FilterRule(Regex("^,[olx][sb](&\\d+)?,[olx]b(.+)?\$"), ",xb\$2#\$2"),
        FilterRule(Regex("^,[olx][sb](&\\d+)?,[olx]s(.+)?\$"), ",xs\$2#\$2"),
        FilterRule(Regex("^,x([sb])\$"), ",o\$1#,l\$1"),
        FilterRule(Regex("m,h"), "f", global = true),
        FilterRule(Regex("f,w"), "m", global = true),
        FilterRule(Regex(",[xol][sb](&\\d+)?(,[mf])"), "\$2", global = true),
        FilterRule(Regex(",[mf],d(&\\d+)"), ",xs\$1"),
        FilterRule(Regex(",[mf],d&([ol])"), ",\$1s"),
        FilterRule(Regex(",[mf],s(&\\d+)"), ",xb\$1"),
        FilterRule(Regex(",[mf],s&([ol])"), ",\$1b"),
        FilterRule(Regex("^(.*,[fh1]|[xol]b(&\\d+)?),[mf],s(.*)\$"), "\$1,xb\$3#\$1\$3"),
        FilterRule(Regex("^(.*,[mw0]|[xol]s(&\\d+)?),[mf],d(.*)\$"), "\$1,xs\$3#\$1\$3"),
        FilterRule(Regex("(,[mw0]|[xol]s(&\\d+)?),[mf],s"), "\$1,xb"),
        FilterRule(Regex("(,[fh1]|[xol]b(&\\d+)?),[mf],d"), "\$1,xs"),
        FilterRule(Regex("^,[mf],s(.+)?\$"), ",xb\$1#,1\$1"),
        FilterRule(Regex("^,[mf],d(.+)?\$"), ",xs\$1#,0\$1"),
        FilterRule(Regex(",[ds](&[ol\\d]+)?,[olx]b(&\\d+)"), ",s\$2", global = true),
        FilterRule(Regex(",[ds](&[ol\\d]+)?,[olx]s(&\\d+)"), ",d\$2", global = true),
        FilterRule(Regex(",[ds]&o,ob"), ",s&o", global = true),
        FilterRule(Regex(",[ds]&o,os"), ",d&o", global = true),
        FilterRule(Regex(",[ds]&l,lb"), ",s&l", global = true),
        FilterRule(Regex(",[ds]&l,ls"), ",d&l", global = true),
        FilterRule(Regex(",[ds](&[ol\\d]+)?,[olx]s(&\\d+)?"), ",d\$2", global = true),
        FilterRule(Regex(",[ds](&[ol\\d]+)?,[olx]b(&\\d+)?"), ",s\$2", global = true),
        FilterRule(Regex("(,[mwd0]|,[olx]s)(&[ol\\d]+)?,[ds](&[ol\\d]+)?,m"), "\$1", global = true),
        FilterRule(Regex("(,[mwd0]|,[olx]s)(&[ol\\d]+)?,[ds](&[ol\\d]+)?,f"), "\$1,h", global = true),
        FilterRule(Regex("(,[fhs1]|,[olx]b)(&[ol\\d]+)?,[ds](&[ol\\d]+)?,f"), "\$1", global = true),
        FilterRule(Regex("(,[fhs1]|,[olx]b)(&[ol\\d]+)?,[ds](&[ol\\d]+)?,m"), "\$1,w", global = true),
        FilterRule(Regex("^,[ds],m(.+)?\$"), ",w\$1#\$1"),
        FilterRule(Regex("^,[ds],f(.+)?\$"), ",h\$1#\$1"),
        FilterRule(Regex(",[wh](,[ds])"), "\$1", global = true),
        FilterRule(Regex(",w,h|,h,w"), "", global = true),
        FilterRule(Regex("(.+)?\\[([^\\|]+?)\\|([^\\[\\]]*\\|[^\\[\\]]*)\\](.+)?"), "\$1\$2\$4#\$1[\$3]\$4", global = true),
        FilterRule(Regex("(.+)?\\[([^\\[\\]\\|]+?)\\|([^\\[\\]\\|]+?)?\\](.+)?"), "\$1\$2\$4#\$1\$3\$4", global = true),
    )

    private data class ExpandRule(val pattern: Regex, val alternatives: List<String>)

    private val expandRules = listOf(
        ExpandRule(Regex("^从表"), listOf("从父姑表", "从父舅表", "从父姨表", "从母姑表", "从母舅表", "从母叔表")),
        ExpandRule(Regex("^表表"), listOf("姑表叔表", "姑表姑表", "姑表舅表", "姑表姨表", "舅表叔表", "舅表姑表", "舅表舅表", "舅表姨表")),
        ExpandRule(Regex("^([夫妻内外]?)表"), listOf("\$1姑表", "\$1舅表")),
        ExpandRule(Regex("^([姑舅])表(?=[^伯叔])"), listOf("\$1表伯", "\$1表叔")),
        ExpandRule(Regex("^姻"), listOf("姑姻", "姨姻", "姊妹姻", "女姻")),
        ExpandRule(Regex("^眷"), listOf("叔眷", "舅眷", "兄弟眷", "男眷")),
        ExpandRule(Regex("^亲家"), listOf("姊妹姻", "兄弟眷")),
        ExpandRule(Regex("^([堂表姨]?)([曾高天烈太远鼻]?)(祖?)([伯叔姑舅姨])"), listOf("\$1\$4\$2\$3")),
        ExpandRule(Regex("^([曾高天烈太远鼻]?)祖?王姑"), listOf("姑\$1祖母")),
        ExpandRule(Regex("^([曾玄元来晜仍云耳])([侄甥])"), listOf("\$2\$1")),
        ExpandRule(Regex("^外表([伯叔姑舅姨])"), listOf("姑表\$1外", "舅表\$1外")),
        ExpandRule(Regex("([堂表姨]?)外甥"), listOf("\$1甥")),
        ExpandRule(Regex("^([舅叔])([曾玄外]*)孙"), listOf("\$1侄\$2孙")),
        ExpandRule(Regex("^([姨姑])([曾玄外]*)孙"), listOf("\$1甥\$2孙")),
        ExpandRule(Regex("([孙甥侄])\$"), listOf("\$1男", "\$1女")),
        ExpandRule(Regex("([姑舅姨叔])([孙外]*)([男女])\$"), listOf("\$1表侄\$2\$3", "\$1表甥\$2\$3")),
        ExpandRule(Regex("(.+)父母\$"), listOf("\$1父", "\$1母")),
        ExpandRule(Regex("(.+)公婆\$"), listOf("\$1公", "\$1婆")),
        ExpandRule(Regex("祖\$"), listOf("祖父")),
        ExpandRule(Regex("(新妇|媳)\$"), listOf("媳妇")),
        ExpandRule(Regex("嫂\$"), listOf("兄妇")),
        ExpandRule(Regex("女儿\$"), listOf("女")),
    )

    private data class ExpressionRule(val pattern: Regex, val build: (MatchResult) -> Map<String, String>)

    private val expressionRules = listOf(
        ExpressionRule(Regex("^(\\S+?)(应该|得)?(称呼|叫|喊)(\\S+?)(什么|啥)？?\$")) { match ->
            mapOf("text" to match.groupValues[4], "target" to match.groupValues[1])
        },
        ExpressionRule(Regex("^(\\S+?)(应该|得)?(如何|怎么)(称呼|叫|喊)(\\S+?)？?\$")) { match ->
            mapOf("text" to match.groupValues[5], "target" to match.groupValues[1])
        },
        ExpressionRule(Regex("^(\\S+?)是(\\S+?)的(谁|什么|什么亲戚|啥亲戚|什么人)？?\$")) { match ->
            mapOf(
                "text" to match.groupValues[1],
                "target" to match.groupValues[2],
                "type" to if (match.groupValues[1].contains('的')) "default" else "chain",
            )
        },
        ExpressionRule(Regex("^(\\S+?)(应该|得)?管(\\S+?)叫(什么|啥)？?\$")) { match ->
            mapOf("text" to match.groupValues[3], "target" to match.groupValues[1])
        },
        ExpressionRule(Regex("^(\\S+?)(和|与)(\\S+?)(之间)?是(什么|啥)关系？?\$")) { match ->
            mapOf("text" to match.groupValues[1], "target" to match.groupValues[3], "type" to "pair")
        },
        ExpressionRule(Regex("^(\\S+?)对于(\\S+?)是(什么|啥)关系？?\$")) { match ->
            mapOf("text" to match.groupValues[1], "target" to match.groupValues[2], "type" to "chain")
        },
        ExpressionRule(Regex("^(\\S+?)(指的)?是(什么|啥)(意思|关系|亲戚关系|辈分|人)？?\$")) { match ->
            mapOf("text" to match.groupValues[1], "type" to "chain")
        },
        ExpressionRule(Regex("^(\\S+?)(应该|得)?(称呼|叫|喊)(什么|啥)？?\$")) { match ->
            mapOf("text" to match.groupValues[1])
        },
        ExpressionRule(Regex("^(\\S+?)(应该|得)?(如何|怎么)(称呼|叫|喊)？?\$")) { match ->
            mapOf("text" to match.groupValues[1])
        },
        ExpressionRule(Regex("^(\\S+?)是(谁|什么|什么亲戚|啥亲戚|什么人|什么辈分|啥辈分)？?\$")) { match ->
            mapOf(
                "text" to match.groupValues[1],
                "type" to if (match.groupValues[1].contains('的')) "default" else "chain",
            )
        },
    )

    fun query(query: KinshipQuery): List<String> {
        val extracted = extractOptions(query.text)
        val text = extracted["text"] ?: query.text
        val target = extracted["target"] ?: query.target
        val type = when (extracted["type"] ?: query.type.name.lowercase(Locale.ROOT)) {
            "chain" -> KinshipType.CHAIN
            "pair" -> KinshipType.PAIR
            else -> KinshipType.TITLE
        }
        val fromSelectors = getSelectors(text)
        var toSelectors = getSelectors(target)
        if (toSelectors.isEmpty()) toSelectors = listOf("")
        val results = LinkedHashSet<String>()
        for (from in fromSelectors) {
            for (to in toSelectors) {
                for (merged in mergeSelector(from, to, query.sex.value, query.optimal)) {
                    for (id in selectorToId(merged.selector, merged.sex)) {
                        var sex = merged.sex
                        val temps = if (query.reverse) {
                            sex = if (Regex("([fhs1](&[ol\\d]+)?|[olx]b)\$").containsMatchIn(id)) 1 else 0
                            reverseId(id, merged.sex)
                        } else {
                            listOf(id)
                        }
                        when (type) {
                            KinshipType.CHAIN -> temps.forEach { temp ->
                                chainById(temp, sex).takeIf { it.isNotEmpty() }?.let { results.add(it) }
                            }

                            KinshipType.PAIR -> {
                                for (reversed in reverseId(id, merged.sex)) {
                                    results.addAll(pairsById(id, reversed))
                                }
                            }

                            KinshipType.TITLE -> temps.forEach { temp ->
                                var items = itemsById(temp)
                                if (items.isEmpty()) items = itemsById("$sex,$temp")
                                results.addAll(items)
                            }
                        }
                    }
                }
            }
        }
        return results.toList()
    }

    // ---------------------------------------------------------------- selectors

    fun expandSelector(selector: String): List<String> {
        val result = ArrayList<String>()
        val visited = HashSet<String>()

        fun visit(start: String) {
            if (!visited.add(start)) return
            var current = start
            var changed: Boolean
            do {
                changed = false
                for (rule in filterRules) {
                    val next = if (rule.global) {
                        rule.pattern.replace(current, rule.replacement)
                    } else {
                        rule.pattern.replaceFirst(current, rule.replacement)
                    }
                    if (next != current) {
                        current = next
                        changed = true
                    }
                    if (current.contains('#')) {
                        current.split('#').forEach { visit(it) }
                        return
                    }
                }
            } while (changed)
            if (Regex(",[mwd0](&[ol\\d+])?,w|,[hfs1](&[ol\\d]+)?,h").containsMatchIn(current)) return
            result.add(current)
        }

        visit(selector)
        return result
    }

    fun getSelectors(str: String): List<String> {
        var value = str
        value = value.replace(Regex("之"), "的")
        value = value.replaceFirst(Regex("吾之?(.+)"), "\$1")
        value = value.replaceFirst(Regex("我的?(.+)"), "\$1")
        if (Regex("[^娘婆岳亲]家的?(孩子|儿子|女儿)").containsMatchIn(value)) {
            value = value.replaceFirst(Regex("家的?"), "的")
        }
        value = value.replaceFirst(Regex("(舅|姑)+(爸|父|丈|妈|母)?家的?(哥|姐|弟|妹)+"), "\$1表\$3")
        value = value.replaceFirst(Regex("(舅|姑)+(爸|父|丈|妈|母)?家的?"), "\$1表")
        value = value.replaceFirst(Regex("(伯|叔)+(父|母)?家的?(哥|姐|弟|妹)+"), "堂\$3")
        value = value.replaceFirst(Regex("(伯|叔)+(父|母)?家的?"), "堂")
        value = value.replaceFirst(Regex("姨+(爸|父|丈|妈|母)?家的?(哥|姐|弟|妹)+"), "姨\$2")
        value = value.replaceFirst(Regex("姨+(爸|父|丈|妈|母)?家的?"), "姨")

        val lists = value.split('的').toMutableList()
        var result: List<String> = emptyList()
        var isMatch = true
        while (lists.isNotEmpty()) {
            val name = lists.removeAt(0)
            var items: List<String> = emptyList()
            val keywords = mutableListOf(name)

            fun getList(current: String, depth: Int) {
                if (depth > RECURSION_LIMIT) return
                for (rule in expandRules) {
                    for (word in rule.alternatives) {
                        val expanded = rule.pattern.replaceFirst(current, word)
                        if (expanded != current && keywords.add(expanded)) getList(expanded, depth + 1)
                    }
                }
                for ((word, replacement) in similar) {
                    val forward = current.replaceFirst(word, replacement)
                    val backward = current.replaceFirst(replacement, word)
                    if (forward != current) keywords.add(forward)
                    if (backward != current) keywords.add(backward)
                }
            }

            getList(name, 0)
            val itemsMap = List(3) { mutableListOf<String>() }
            for (raw in keywords) {
                val keyword = raw.replaceFirst(Regex("^[尕幺细满碎晚末尾幼]"), "小")
                val match = Regex("^[大|小]|^[一|二|三|四|五|六|七|八|九|十]+").find(keyword)
                if (match != null) {
                    val prefix = match.value
                    val candidates = listOf(
                        keyword.replaceFirst(prefix, "几"),
                        keyword.replaceFirst(prefix, ""),
                        keyword,
                    )
                    val number = zh2number(prefix)
                    candidates.forEachIndexed { index, candidate ->
                        val ids = data.reverse[candidate].orEmpty()
                        for (id in ids) {
                            val transformed = id
                                .replaceFirst(Regex("(,[hw])\$"), "&$number\$1")
                                .replaceFirst(Regex("([^hw]+)\$"), "\$1&$number")
                            if (!id.matches(Regex("^[mf,]+\$")) && !candidate.matches(Regex("^[从世]"))) {
                                itemsMap[index].add(transformed)
                            }
                        }
                    }
                }
                items = items + data.reverse[keyword].orEmpty()
            }
            for (candidate in itemsMap) {
                if (items.isEmpty()) items = candidate
            }
            if (items.isEmpty()) isMatch = false
            if (result.isEmpty()) result = listOf("")
            val next = ArrayList<String>()
            for (a in result) {
                for (b in items) next.add(a + if (b.isNotEmpty()) ",$b" else "")
            }
            result = next
        }
        return if (isMatch) filterId(result) else emptyList()
    }

    fun selectorToId(selectorIn: String, sexIn: Int): List<String> {
        var selector = if (selectorIn.startsWith(",")) selectorIn else ",$selectorIn"
        var sex = sexIn
        if (sex < 0) {
            if (Regex("^,[w1]").containsMatchIn(selector)) {
                sex = 1
            } else if (Regex("^,[h0]").containsMatchIn(selector)) {
                sex = 0
            }
        } else if (sex == 1 && Regex("^,[h0]").containsMatchIn(selector)) {
            return emptyList()
        } else if (sex == 0 && Regex("^,[w1]").containsMatchIn(selector)) {
            return emptyList()
        }
        if (sex > -1 && !selector.contains(",1") && !selector.contains(",0")) {
            selector = ",$sex$selector"
        }
        if (Regex(",[mwd0](&[ol\\d]+)?,w|,[hfs1](&[ol\\d]+)?,h").containsMatchIn(selector)) return emptyList()
        return filterId(
            expandSelector(selector).map { expanded ->
                val stripped = expanded.replaceFirst(Regex(",[01]"), "")
                if (stripped.isEmpty()) "" else stripped.substring(1)
            },
        )
    }

    private data class MergedSelector(val selector: String, val sex: Int)

    private fun mergeSelector(fromSelector: String, toSelector: String, sexIn: Int, optimal: Boolean): List<MergedSelector> {
        var mySex = sexIn
        if (mySex < 0) {
            val fromSex = when {
                Regex("^,[w1]").containsMatchIn(fromSelector) -> 1
                Regex("^,[h0]").containsMatchIn(fromSelector) -> 0
                else -> -1
            }
            val toSex = when {
                Regex("^,[w1]").containsMatchIn(toSelector) -> 1
                Regex("^,[h0]").containsMatchIn(toSelector) -> 0
                else -> -1
            }
            when {
                fromSex == -1 && toSex > -1 -> mySex = toSex
                fromSex > -1 && toSex == -1 -> mySex = fromSex
                fromSex == toSex -> mySex = fromSex
                else -> return emptyList()
            }
        }
        val fromIds = selectorToId(fromSelector, mySex)
        val toIds = selectorToId(toSelector, mySex)
        if (fromIds.isEmpty() || toIds.isEmpty()) return emptyList()
        val result = ArrayList<MergedSelector>()
        for (from in fromIds) {
            for (to in toIds) {
                var currentFrom = from
                var currentTo = to
                var currentSex = mySex
                var sex = mySex
                val selector = ",$currentTo"
                if (Regex(",([fhs1](&[ol\\d]+)?|[olx]b)(&[ol\\d]+)?\$").containsMatchIn(selector)) sex = 1
                if (Regex(",([mwd0](&[ol\\d]+)?|[olx]s)(&[ol\\d]+)?\$").containsMatchIn(selector)) sex = 0
                if (currentFrom.isNotEmpty() && currentTo.isNotEmpty()) {
                    var needsOptimal = optimal
                    if (currentFrom.contains(Regex("&\\d+")) || currentTo.contains(Regex("&\\d+"))) needsOptimal = true
                    if (needsOptimal) {
                        val merged = getOptimal(currentFrom, currentTo, currentSex, optimal)
                        currentFrom = merged.from
                        currentTo = merged.to
                        currentSex = merged.sex
                    }
                }
                val toRids = if (currentTo.isNotEmpty()) reverseId(currentTo, currentSex) else listOf("")
                for (toRid in toRids) {
                    val merged = (if (toRid.isNotEmpty()) ",$toRid" else "") +
                        (if (currentFrom.isNotEmpty()) ",$currentFrom" else "")
                    result.add(MergedSelector(merged, sex))
                }
            }
        }
        return result
    }

    private data class Optimal(val from: String, val to: String, val sex: Int)

    private fun getOptimal(fromIn: String, toIn: String, sexIn: Int, optimal: Boolean): Optimal {
        var from = fromIn
        var to = toIn
        var sex = sexIn
        val fromChain = from.split(',').toMutableList()
        val toChain = to.split(',').toMutableList()
        var index = 0
        while (index < fromChain.size && index < toChain.size) {
            val fromSegment = fromChain[index]
            val toSegment = toChain[index]
            if (fromSegment == toSegment) {
                from = fromChain.drop(index + 1).joinToString(",")
                to = toChain.drop(index + 1).joinToString(",")
                sex = if (Regex("^([fhs1](&[ol\\d]+)?|[olx]b)(&[ol\\d]+)?").containsMatchIn(fromSegment)) 1 else 0
                index++
                continue
            }
            if (getGenById(fromSegment) == getGenById(toSegment) &&
                Regex("^[xol][bs]|^[sd]").containsMatchIn(fromSegment) &&
                Regex("^[xol][bs]|^[sd]").containsMatchIn(toSegment)
            ) {
                val fromType = fromSegment.replaceFirst(Regex("&([ol\\d]+)"), "").replaceFirst(Regex("^[xol]([bs])"), "\$1")
                val toType = toSegment.replaceFirst(Regex("&([ol\\d]+)"), "").replaceFirst(Regex("^[xol]([bs])"), "\$1")
                if (fromType != toType) break
                var fromAttr = Regex("&([ol\\d]+)").find(fromSegment)?.groupValues?.get(1)
                    ?: Regex("([ol])[bs]").find(fromSegment)?.groupValues?.get(1) ?: ""
                var toAttr = Regex("&([ol\\d]+)").find(toSegment)?.groupValues?.get(1)
                    ?: Regex("([ol])[bs]").find(toSegment)?.groupValues?.get(1) ?: ""
                if (fromAttr.isNotEmpty() && toAttr.isNotEmpty()) {
                    val fromNumber = fromAttr.toIntOrNull()
                    val toNumber = toAttr.toIntOrNull()
                    when {
                        fromNumber != null && toNumber != null -> when {
                            fromNumber > toNumber -> fromChain[index] = fromSegment
                                .replaceFirst(Regex("^[xol]b|^s"), "lb")
                                .replaceFirst(Regex("^[xol]s|^d"), "ls")

                            fromNumber < toNumber -> fromChain[index] = fromSegment
                                .replaceFirst(Regex("^[xol]b|^s"), "ob")
                                .replaceFirst(Regex("^[xol]s|^d"), "os")
                        }

                        (fromNumber != null && toAttr == "o") || (fromAttr == "l" && toNumber != null) ->
                            fromChain[index] = fromSegment
                                .replaceFirst(Regex("^[xol]b|^s"), "lb")
                                .replaceFirst(Regex("^[xol]s|^d"), "ls")

                        (fromNumber != null && toAttr == "l") || (fromAttr == "o" && toNumber != null) ->
                            fromChain[index] = fromSegment
                                .replaceFirst(Regex("^[xol]b|^s"), "ob")
                                .replaceFirst(Regex("^[xol]s|^d"), "os")
                    }
                    from = fromChain.drop(index).joinToString(",")
                    to = toChain.drop(index + 1).joinToString(",")
                    sex = if (Regex("^([fhs1](&[ol\\d]+)?|[olx]b)(&[ol\\d]+)?").containsMatchIn(toChain[index])) 1 else 0
                } else if (optimal) {
                    fromAttr = Regex("([xol])[bs]").find(fromSegment)?.groupValues?.get(1) ?: ""
                    toAttr = Regex("([xol])[bs]").find(toSegment)?.groupValues?.get(1) ?: ""
                    if (fromAttr == "x" || toAttr == "x") {
                        from = fromChain.drop(index + 1).joinToString(",")
                        to = toChain.drop(index + 1).joinToString(",")
                        sex = if (Regex("^([fhs1](&[ol\\d]+)?|[olx]b)(&[ol\\d]+)?").containsMatchIn(fromSegment)) 1 else 0
                        index++
                        continue
                    }
                }
            }
            break
        }
        return Optimal(from, to, sex)
    }

    // ---------------------------------------------------------------- ids

    fun reverseId(idIn: String, sexIn: Int): List<String> {
        if (idIn.isEmpty()) return listOf("")
        val hash = mapOf(
            "f" to listOf("d", "s"), "m" to listOf("d", "s"),
            "h" to listOf("w", ""), "w" to listOf("", "h"),
            "s" to listOf("m", "f"), "d" to listOf("m", "f"),
            "lb" to listOf("os", "ob"), "ob" to listOf("ls", "lb"),
            "xb" to listOf("xs", "xb"), "ls" to listOf("os", "ob"),
            "os" to listOf("ls", "lb"), "xs" to listOf("xs", "xb"),
        )
        var age = ""
        if (idIn.endsWith("&o")) age = "&l" else if (idIn.endsWith("&l")) age = "&o"
        val id = idIn.replace(Regex("&[ol\\d+]"), "")
        var sex = sexIn
        if (sex < 0) {
            if (id.startsWith("w")) sex = 1 else if (id.startsWith("h")) sex = 0
        }

        fun doing(sexValue: Int): String {
            val sid = (",$sexValue,$id")
                .replace(Regex(",[fhs]|,[olx]b"), ",1")
                .replace(Regex(",[mwd]|,[olx]s"), ",0")
            val sidArray = sid.dropLast(2).split(',').reversed()
            val reversedId = id.split(',').reversed().mapIndexed { index, part ->
                val target = hash[part]
                val slot = sidArray.getOrNull(index)?.toIntOrNull() ?: 0
                target?.getOrNull(slot) ?: part
            }.joinToString(",")
            val gen = getGenById(reversedId)
            return reversedId + if (gen != 0) "" else age
        }

        return if (sex < 0) listOf(doing(1), doing(0)) else listOf(doing(sex))
    }

    fun filterId(arr: List<String>): List<String> {
        fun normalize(item: String): String = item
            .replace(Regex("[ol](?=[s|b])"), "x")
            .replaceFirst(Regex("&[ol]"), "")

        val sameList = arr.filter { it == normalize(it) }.toHashSet()
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (item in arr) {
            val temp = normalize(item)
            val keep = sameList.contains(item) || (item != temp && !sameList.contains(temp))
            if (keep && seen.add(item)) out.add(item)
        }
        return out
    }

    fun getGenById(id: String): Int {
        val generation = mapOf("f" to 1, "m" to 1, "s" to -1, "d" to -1)
        var gen = 0
        for (segment in id.split(',')) {
            val key = segment.replaceFirst(Regex("&[ol\\d]+"), "")
            gen += generation[key] ?: 0
        }
        return gen
    }

    fun itemsById(idIn: String): List<String> {
        val items = ArrayList<String>()
        var id = idIn

        fun getData(key: String): List<String> {
            val older = Regex("(,[sd])(,[wh])?\$").replaceFirst(key, "\$1&o\$2")
            val younger = Regex("(,[sd])(,[wh])?\$").replaceFirst(key, "\$1&l\$2")
            val ids = when {
                data.mode.containsKey(older) && data.mode.containsKey(younger) -> listOf(older, younger)
                data.mode.containsKey(key) -> listOf(key)
                else -> emptyList()
            }
            return filterId(ids).mapNotNull { data.mode[it]?.firstOrNull() }
        }

        val number = Regex("&([\\d]+)(,[hw])?\$").find(id)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (number != 0) {
            val zh = number2zh(number)
            id = id.replace(Regex("&\\d+"), "")
            val sorted = data.sort[id]
            if (sorted != null) {
                items.add(sorted.first().replace("几", zh))
            } else {
                var list = data.mode[id].orEmpty()
                if (list.isEmpty()) {
                    list = data.mode[id.replace(Regex("[ol](?=[s|b])"), "x")].orEmpty()
                }
                if (list.isNotEmpty()) {
                    val gen = getGenById(id)
                    var item = ""
                    if (gen < 4 && !id.contains(Regex("[hw],"))) {
                        for (name in data.mode[id].orEmpty()) {
                            if (item.isEmpty() && name.contains("几")) item = name.replace("几", zh)
                        }
                        if (item.isEmpty()) {
                            val first = data.mode[id]?.firstOrNull().orEmpty()
                            item = if (first.startsWith("大") || first.startsWith("小")) {
                                first.replaceFirst(Regex("^[大小]"), zh)
                            } else {
                                zh + first
                            }
                        }
                    }
                    if (item.isNotEmpty()) items.add(item)
                }
            }
        }
        if (items.isEmpty()) {
            id = id.replace(Regex("&\\d+"), "")
            items.addAll(getData(id))
        }
        if (items.isEmpty()) {
            id = id.replace(Regex("&[ol]"), "")
            items.addAll(getData(id))
        }
        if (items.isEmpty()) {
            id = id.replace(Regex("[ol](b|s)"), "x\$1")
            items.addAll(getData(id))
        }
        if (items.isEmpty()) {
            items.addAll(getData(id.replace("x", "o")))
            items.addAll(getData(id.replace("x", "l")))
        }
        return items
    }

    fun chainById(id: String, sex: Int = -1): String {
        var item = id.split(',').joinToString("的") { sign ->
            val key = sign.replaceFirst(Regex("&[ol\\d]+"), "")
            data.mode[key]?.firstOrNull()
                ?: when (key) {
                    "xb" -> "兄弟"
                    "xs" -> "姐妹"
                    else -> ""
                }
        }
        if (sex > -1 && data.mode.containsKey("$sex,$id")) {
            item = when (sex) {
                0 -> "(女性)$item"
                1 -> "(男性)$item"
                else -> item
            }
        }
        return item
    }

    fun pairsById(id1In: String, id2In: String): List<String> {
        val id1 = id1In.replace(Regex("&\\d+"), "")
        val id2 = id2In.replace(Regex("&\\d+"), "")
        val id1x = id1.replace(Regex("([ol])([bs])"), "x\$2")
        val id2x = id2.replace(Regex("([ol])([bs])"), "x\$2")
        val id1r = id1.replace(Regex("&[ol]"), "")
        val id2r = id2.replace(Regex("&[ol]"), "")
        val result = ArrayList<String>()
        val resultX = ArrayList<String>()
        val resultR = ArrayList<String>()
        for ((key, names) in data.pair) {
            val selectors = key.split('#')
            if (selectors.size <= 1) continue
            val list1 = selectorToId(selectors[0], -1)
            val list2 = selectorToId(selectors[1], -1)
            val list1r = list1.map {
                it.replaceFirst(Regex("&[ol\\d]+"), "").replaceFirst(Regex("([ol])([bs])"), "x\$2")
            }
            val list2r = list2.map {
                it.replaceFirst(Regex("&[ol\\d]+"), "").replaceFirst(Regex("([ol])([bs])"), "x\$2")
            }
            val direct = (list1.contains(id1) && list2.contains(id2)) || (list1.contains(id2) && list2.contains(id1))
            val throughX = (list1r.contains(id1x) && list2r.contains(id2x)) || (list1r.contains(id2x) && list2r.contains(id1x))
            val throughR = (list1r.contains(id1r) && list2r.contains(id2r)) || (list1r.contains(id2r) && list2r.contains(id1r))
            if (direct) result.add(names.first())
            if (throughX) resultX.add(names.first())
            if (throughR) resultR.add(names.first())
        }
        return when {
            result.isNotEmpty() -> result
            resultX.isNotEmpty() -> resultX
            else -> resultR
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun extractOptions(text: String): Map<String, String> {
        for (rule in expressionRules) {
            val match = rule.pattern.find(text) ?: continue
            return rule.build(match)
        }
        return emptyMap()
    }

    private fun zh2number(text: String): Int {
        when (text) {
            "大" -> return 1
            "小" -> return 99
        }
        val parts = text.replace(Regex("^十"), "一十").split("十").map { textAttr.indexOf(it) }.reversed()
        val unit = parts.getOrElse(0) { 0 }
        val dec = parts.getOrElse(1) { 0 }
        return dec * 10 + unit
    }

    private fun number2zh(num: Int): String {
        if (num == 1) return "大"
        if (num == 99) return "小"
        val dec = num / 10
        val unit = num % 10
        val prefix = if (dec != 0) (textAttr[dec] + "十").replace("一十", "十") else ""
        return prefix + textAttr[unit]
    }

    private companion object {
        const val RECURSION_LIMIT = 24
    }
}
