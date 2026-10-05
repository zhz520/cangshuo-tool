package com.cangshuo.toolbox.feature.ping.data

import com.cangshuo.toolbox.feature.ping.domain.*
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex

class NativePingRepository : PingRepository {
    private val lock = Mutex()
    private var lastStart = Long.MIN_VALUE
    override suspend fun execute(request: PingRequest): PingResult = withContext(Dispatchers.IO) {
        if (!lock.tryLock()) throw PingFailure(PingError.BUSY)
        try {
            val now = System.nanoTime()
            if (lastStart != Long.MIN_VALUE && now-lastStart < 2_000_000_000L) throw PingFailure(PingError.BUSY)
            val binary = if (request.ipv6) "/system/bin/ping6" else "/system/bin/ping"
            if (!File(binary).canExecute()) throw PingFailure(PingError.UNSUPPORTED)
            lastStart = now
            val process = try {
                // No shell expansion: every validated argument is a separate ProcessBuilder item.
                ProcessBuilder(binary,"-n","-c",request.count.toString(),"-W","2","-w","15",request.host)
                    .redirectErrorStream(true).apply { environment()["LC_ALL"]="C";environment()["LANG"]="C" }.start()
            } catch (_: Exception) { throw PingFailure(PingError.UNSUPPORTED) }
            try {
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                try {
                    withTimeout(20_000) {
                        while (true) {
                            ensureActive()
                            val available = process.inputStream.available()
                            if (available > 0) {
                                val size = process.inputStream.read(buffer,0,minOf(available,buffer.size))
                                if (size > 0) {
                                    if (output.size()+size > 65_536) throw PingFailure(PingError.OUTPUT)
                                    output.write(buffer,0,size)
                                }
                            } else if (!process.isAlive) break
                            else delay(25)
                        }
                    }
                } catch (_: TimeoutCancellationException) { throw PingFailure(PingError.TIMEOUT) }
                PingOutput.parse(output.toString(Charsets.UTF_8.name()),request.count)
            } finally {
                process.destroyForcibly()
                runCatching { process.inputStream.close() };runCatching { process.errorStream.close() };runCatching { process.outputStream.close() }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: PingFailure) { throw e }
        catch (_: Exception) { throw PingFailure(PingError.FAILED) }
        finally { lock.unlock() }
    }
}
