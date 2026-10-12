package com.cangshuo.toolbox.feature.kinship.data

/**
 * The three shipped tables plus the reverse index, assembled exactly like the reference
 * mode module: reverse = names of (mode + input + sort), in insertion order, mapped to ids.
 */
class KinshipData(
    val mode: LinkedHashMap<String, List<String>>,
    val input: LinkedHashMap<String, List<String>>,
    val sort: LinkedHashMap<String, List<String>>,
    val pair: LinkedHashMap<String, List<String>>,
) {
    val reverse: HashMap<String, MutableList<String>> = buildReverse()

    private fun buildReverse(): HashMap<String, MutableList<String>> {
        val merged = LinkedHashMap<String, MutableList<String>>()
        for ((key, names) in mode) merged[key] = names.toMutableList()
        for ((key, names) in input) merged.getOrPut(key) { mutableListOf() }.addAll(names)
        for ((key, names) in sort) merged.getOrPut(key) { mutableListOf() }.addAll(names)
        val result = HashMap<String, MutableList<String>>()
        for ((key, names) in merged) {
            for (name in names) result.getOrPut(name) { mutableListOf() }.add(key)
        }
        return result
    }

    companion object {
        /** [readAsset] receives a path such as `kinship/mode.json`. */
        fun load(readAsset: (String) -> String): KinshipData = KinshipData(
            mode = link(KinshipJson(readAsset("kinship/mode.json")).parsePairs()),
            input = link(KinshipJson(readAsset("kinship/input.json")).parsePairs()),
            sort = link(KinshipJson(readAsset("kinship/sort.json")).parsePairs()),
            pair = link(KinshipJson(readAsset("kinship/pair.json")).parsePairs()),
        )

        private fun link(pairs: List<Pair<String, List<String>>>): LinkedHashMap<String, List<String>> {
            val map = LinkedHashMap<String, List<String>>(pairs.size)
            for ((key, value) in pairs) map[key] = value
            return map
        }
    }
}
