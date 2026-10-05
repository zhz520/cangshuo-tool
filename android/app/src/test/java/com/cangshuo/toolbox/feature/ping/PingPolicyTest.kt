package com.cangshuo.toolbox.feature.ping

import com.cangshuo.toolbox.feature.ping.domain.*
import org.junit.Assert.*
import org.junit.Test

class PingPolicyTest {
    @Test fun validatesIdnAndAddressesWithoutAcceptingCommandsOrUrls() {
        assertEquals("xn--fiqs8s.cn",PingTargets.request("中国.cn",false,4).host)
        assertEquals("::1",PingTargets.request("[::1]",true,1).host)
        assertEquals("192.168.1.1",PingTargets.request("192.168.1.1",false,8).host)
        listOf("-c", "a;whoami", "a$(id)", "https://example.com", "host:80", "a\nb", "a b", "1.2.3.999", "127.01.0.1", "a..b", "fe80::1%wlan0").forEach {
            try { PingTargets.request(it,false,4);fail("Accepted invalid target") } catch (e: PingFailure) { assertEquals(PingError.INVALID_TARGET,e.reason) }
        }
    }
    @Test fun partialLossRetainsRealSummaryTimingAndPacketBounds() {
        val result=PingOutput.parse("""PING host (192.0.2.1) 56(84) bytes of data.
64 bytes from 192.0.2.1: icmp_seq=1 ttl=64 time=0.251 ms
64 bytes from 192.0.2.1: icmp_seq=3 ttl=64 time<1 ms
4 packets transmitted, 2 received, 50% packet loss
rtt min/avg/max/mdev = 0.251/0.285/0.300/0.019 ms
""",4)
        assertEquals(50.0,result.lossPercent,0.0);assertEquals(.285,result.averageMs!!,0.0)
        assertEquals("192.0.2.1",result.address);assertTrue(result.replies.last().lessThan)
    }
    @Test fun totalLossIsAResultAndDoesNotMatchZeroLossSubstring() {
        val result=PingOutput.parse("4 packets transmitted, 0 received, 100% packet loss",4)
        assertEquals(0,result.received);assertEquals(100.0,result.lossPercent,0.0);assertNull(result.averageMs)
    }
    @Test fun inconsistentCountersAndUnknownOutputAreFailures() {
        listOf("9 packets transmitted, 9 received", "4 packets transmitted, 5 received",
            "4 packets transmitted, 4 received\nrtt min/avg/max = 5/1/2 ms").forEach {
            try { PingOutput.parse(it,4);fail("Accepted invalid stats") } catch(e: PingFailure) { assertEquals(PingError.OUTPUT,e.reason) }
        }
        try { PingOutput.parse("ping: unknown host x",4);fail() } catch(e: PingFailure) { assertEquals(PingError.DNS,e.reason) }
    }
}
