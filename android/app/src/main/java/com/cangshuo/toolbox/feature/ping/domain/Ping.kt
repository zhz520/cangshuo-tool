package com.cangshuo.toolbox.feature.ping.domain

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale

enum class PingError { INVALID_TARGET, BUSY, TIMEOUT, DNS, UNSUPPORTED, OUTPUT, FAILED }
class PingFailure(val reason: PingError) : IllegalStateException(reason.name)
data class PingRequest(val host: String, val ipv6: Boolean, val count: Int) {
    override fun toString() = "PingRequest[redacted]"
}
data class PingReply(val sequence: Int, val timeMs: Double, val lessThan: Boolean)
data class PingResult(val transmitted: Int, val received: Int, val minMs: Double?, val averageMs: Double?, val maxMs: Double?,
    val address: String?, val replies: List<PingReply>) {
    val lossPercent get() = (transmitted-received)*100.0/transmitted
    override fun toString() = "PingResult[redacted]"
}
object PingTargets {
    fun request(input: String, ipv6: Boolean, count: Int): PingRequest {
        fun invalid(): Nothing = throw PingFailure(PingError.INVALID_TARGET)
        if (input.length > 256 || input.any { Character.isISOControl(it) } || count !in setOf(1,4,8)) invalid()
        val value = input.trim().removeSurrounding("[","]")
        if (value.isEmpty() || value.contains('%')) invalid()
        val host = if (':' in value) {
            if (!ipv6 || !Regex("[0-9a-fA-F:.]+").matches(value)) invalid()
            if (runCatching { InetAddress.getByName(value) is Inet6Address }.getOrDefault(false).not()) invalid()
            value.lowercase(Locale.ROOT)
        } else {
            val ascii = runCatching { IDN.toASCII(value,IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT) }.getOrElse { invalid() }
            if (ascii.length !in 1..253 || !Regex("[a-z0-9][a-z0-9.-]*").matches(ascii) || ascii.endsWith('.')) invalid()
            if (Regex("[0-9.]+").matches(ascii)) {
                val parts = ascii.split('.')
                if (ipv6 || parts.size != 4 || parts.any { it.isEmpty() || it.length > 3 || (it.length > 1 && it.startsWith('0')) || it.toIntOrNull() !in 0..255 }) invalid()
            } else if (ascii.split('.').any { it.length !in 1..63 || it.startsWith('-') || it.endsWith('-') }) invalid()
            ascii
        }
        return PingRequest(host,ipv6,count)
    }
}
object PingOutput {
    private val summary = Regex("(\\d+) packets transmitted,\\s*(\\d+) (?:packets )?received",RegexOption.IGNORE_CASE)
    private val timing = Regex("(?:rtt|round-trip)[^\\r\\n]*?=\\s*([0-9]+(?:\\.[0-9]+)?)/([0-9]+(?:\\.[0-9]+)?)/([0-9]+(?:\\.[0-9]+)?)",RegexOption.IGNORE_CASE)
    private val reply = Regex("icmp_seq[= ](\\d+)[^\\r\\n]*?time([=<])([0-9]+(?:\\.[0-9]+)?)\\s*ms",RegexOption.IGNORE_CASE)
    fun parse(output: String, count: Int): PingResult {
        if (output.length > 65_536) throw PingFailure(PingError.OUTPUT)
        val match = summary.find(output) ?: throw PingFailure(when {
            output.contains("unknown host",true) || output.contains("bad address",true) || output.contains("Name or service not known",true) -> PingError.DNS
            output.contains("not permitted",true) || output.contains("permission denied",true) -> PingError.UNSUPPORTED
            else -> PingError.FAILED
        })
        val sent = match.groupValues[1].toIntOrNull() ?: throw PingFailure(PingError.OUTPUT)
        val received = match.groupValues[2].toIntOrNull() ?: throw PingFailure(PingError.OUTPUT)
        if (sent !in 1..count || received !in 0..sent) throw PingFailure(PingError.OUTPUT)
        val times = if (received > 0) timing.find(output)?.groupValues?.drop(1)?.map { it.toDoubleOrNull() } else null
        if (times != null && (times.any { it == null || !it.isFinite() || it < 0 } || times[0]!! > times[1]!! || times[1]!! > times[2]!!)) throw PingFailure(PingError.OUTPUT)
        val replies = reply.findAll(output).mapNotNull {
            val sequence = it.groupValues[1].toIntOrNull()
            val time = it.groupValues[3].toDoubleOrNull()
            if (sequence == null || sequence !in 0..count || time == null || !time.isFinite() || time < 0) null
            else PingReply(sequence,time,it.groupValues[2] == "<")
        }.distinctBy { it.sequence }.take(count).toList()
        val address = Regex("^PING [^\\r\\n(]+\\(([0-9a-fA-F:.]+)\\)",RegexOption.MULTILINE).find(output)?.groupValues?.get(1)
        return PingResult(sent,received,times?.get(0),times?.get(1),times?.get(2),address,replies)
    }
}
interface PingRepository { suspend fun execute(request: PingRequest): PingResult }
class ExecutePingUseCase(private val repository: PingRepository) {
    suspend operator fun invoke(input: String, ipv6: Boolean, count: Int) = repository.execute(PingTargets.request(input,ipv6,count))
}
