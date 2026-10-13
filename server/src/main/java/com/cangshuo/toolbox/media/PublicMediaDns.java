package com.cangshuo.toolbox.media;

import java.net.*;
import org.apache.hc.client5.http.DnsResolver;

/** The HTTP connection itself uses these checked addresses, preventing a second unchecked DNS lookup. */
public final class PublicMediaDns implements DnsResolver {
    @Override public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) throw new UnknownHostException("No public address");
        for (InetAddress address : addresses) if (!isPublic(address)) throw new UnknownHostException("Non-public address rejected");
        return addresses;
    }
    @Override public String resolveCanonicalHostname(String host) throws UnknownHostException { resolve(host); return host; }
    static boolean isPublic(InetAddress ip) {
        if (ip.isAnyLocalAddress() || ip.isLoopbackAddress() || ip.isLinkLocalAddress()
                || ip.isSiteLocalAddress() || ip.isMulticastAddress()) return false;
        byte[] b = ip.getAddress();
        if (b.length == 4) {
            int a = b[0] & 255, c = b[1] & 255, d = b[2] & 255;
            return !(a == 0 || a == 10 || a == 127 || a >= 224 || a == 169 && c == 254
                    || a == 100 && c >= 64 && c <= 127 || a == 172 && c >= 16 && c <= 31
                    || a == 192 && (c == 168 || c == 0 || c == 88 && d == 99)
                    || a == 198 && (c == 18 || c == 19 || c == 51 && d == 100)
                    || a == 203 && c == 0 && d == 113);
        }
        // Only global unicast; reject transition/documentation/reserved ranges as well.
        return b.length == 16 && (b[0] & 0xe0) == 0x20
                && !(b[0] == 0x20 && b[1] == 1 && ((b[2] & 255) == 0x0d && (b[3] & 255) == 0xb8
                || b[2] == 0 || b[2] == 0x10))
                && !(b[0] == 0x20 && b[1] == 2);
    }
}
