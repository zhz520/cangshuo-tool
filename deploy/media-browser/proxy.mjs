import http from 'node:http';
import net from 'node:net';
import {lookup} from 'node:dns/promises';
import {allowedHost, publicAddress} from './policy.mjs';

// CONNECT tunnels bind to the checked address. Chromium retains the original hostname for TLS/SNI.
// The proxy is loopback-only and permits only HTTPS to owned public platform hosts.
export async function startProxy() {
  const server = http.createServer((_req, res) => {res.writeHead(403); res.end();});
  const sockets = new Set();
  server.on('connect', async (req, downstream, head) => {
    let upstream, connected = false;
    const deny = () => {
      if (!downstream.destroyed) {
        if (connected) downstream.destroy();
        else downstream.end('HTTP/1.1 403 Forbidden\r\n\r\n');
      }
      upstream?.destroy();
    };
    try {
      const match = /^([a-z0-9.-]+):443$/.exec(req.url || '');
      if (!match || !allowedHost(match[1]) || head.length) return deny();
      const addresses = await lookup(match[1], {all: true});
      if (!addresses.length || addresses.some(row => !publicAddress(row.address))) return deny();
      const address = addresses.find(row => row.family === 4) || addresses[0];
      upstream = net.connect({host: address.address, port: 443, family: address.family});
      sockets.add(downstream); sockets.add(upstream);
      upstream.setTimeout(15_000, () => {upstream.destroy(); downstream.destroy();});
      downstream.setTimeout(25_000, () => {upstream.destroy(); downstream.destroy();});
      downstream.once('close', () => {sockets.delete(downstream); upstream.destroy();});
      upstream.once('close', () => {sockets.delete(upstream); downstream.destroy();});
      upstream.on('error', deny); downstream.on('error', () => upstream.destroy());
      upstream.once('connect', () => {
        connected = true;
        downstream.write('HTTP/1.1 200 Connection Established\r\n\r\n');
        downstream.pipe(upstream); upstream.pipe(downstream);
      });
    } catch {deny();}
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  return {url: 'http://127.0.0.1:' + server.address().port, close: async () => {
    for (const socket of sockets) socket.destroy();
    await new Promise(resolve => server.close(resolve));
  }};
}
