package com.cangshuo.toolbox.core.tool

import com.cangshuo.toolbox.core.model.ToolMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Application-wide catalog snapshots. Reads never wait for a network request. */
class ToolRegistryStore(private var bundled: ToolRegistry) {
    private var remote = emptyList<ToolDefinition>()
    private val mutableSnapshots = MutableStateFlow(bundled)
    val snapshots = mutableSnapshots.asStateFlow()
    val current: ToolRegistry get() = snapshots.value

    /** Replace the previous remote snapshot only after a successful fetch. */
    @Synchronized fun replaceRemoteWebTools(definitions: Collection<ToolDefinition>) {
        require(definitions.all { it.metadata.mode == ToolMode.WEB }) { "Only web tools may be added remotely" }
        // Always merge from the bundled catalog, so removed remote tools do not linger.
        val next = bundled.withExtra(definitions)
        remote = definitions.toList()
        mutableSnapshots.value = next
    }

    @Synchronized fun replaceBundled(definitions: ToolRegistry) {
        val next=definitions.withExtra(remote)
        bundled=definitions
        mutableSnapshots.value=next
    }
}
