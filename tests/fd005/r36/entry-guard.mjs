import net from 'node:net';
import tls from 'node:tls';
import dns from 'node:dns';

const deny = () => { throw new Error('FD005_NETWORK_DISABLED'); };
globalThis.fetch = deny;
net.connect = deny;
net.createConnection = deny;
tls.connect = deny;
dns.lookup = deny;
dns.resolve = deny;

