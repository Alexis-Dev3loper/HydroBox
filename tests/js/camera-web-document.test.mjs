import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

// Execute the actual Kotlin HTML template's script, not a second receiver implementation.
const kotlin = readFileSync(new URL('../../app/src/main/java/com/hydrobox/app/ui/camera/CameraWebDocument.kt', import.meta.url), 'utf8');
const embedded = kotlin.slice(kotlin.indexOf('<script>') + 8, kotlin.indexOf('</script>'));
const origin = 'https://media.example.test:8443';
const playback = origin + '/whep/1';
const token = 'synthetic-ephemeral-camera-bearer-token';
const sdp = 'v=0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\n';
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };

function harness({ location = '/resource/1', body = sdp, statusCode = 201,
    contentType = 'application/sdp', mediaGate, iceState = 'complete', expiry = Date.now() + 300000,
    fetchOverride } = {}) {
    const calls = [], timers = new Map(), peers = [];
    let timerId = 0;
    const status = { textContent: 'Conectando cámara…', hidden: false };
    const video = { srcObject: null, onplaying: null };
    class Peer {
        iceGatheringState = iceState; connectionState = 'connecting'; listeners = {};
        localDescription = { sdp }; closed = false; remote = false;
        constructor() { peers.push(this); }
        addTransceiver(kind, options) { assert.equal(kind, 'video'); assert.equal(options.direction, 'recvonly'); }
        addEventListener(name, fn) { this.listeners[name] = fn; }
        removeEventListener(name) { delete this.listeners[name]; }
        async createOffer() { return { sdp }; }
        async setLocalDescription() {}
        async setRemoteDescription() { this.remote = true; this.listeners.track?.({ streams: [{}], track: {} }); }
        close() { this.closed = true; this.connectionState = 'closed'; this.listeners.connectionstatechange?.(); }
    }
    const fetcher = async (url, options) => {
        calls.push({ url, options });
        if (fetchOverride) return fetchOverride(url, options);
        if (options.method === 'DELETE') return new Response(null, { status: 204 });
        if (mediaGate) await mediaGate.promise;
        const response = new Response(body, { status: statusCode, headers: { 'Content-Type': contentType } });
        return { status: response.status, body: response.body,
            headers: { get: name => name === 'Location' ? location : response.headers.get(name) } };
    };
    const events = {};
    const context = vm.createContext({ URL, Response, TextDecoder, AbortController,
        RTCPeerConnection: Peer, MediaStream: class {}, fetch: fetcher,
        document: { getElementById: id => id === 'stream' ? video : status },
        setTimeout(fn, ms) { const id = ++timerId; timers.set(id, { fn, ms }); return id; },
        clearTimeout(id) { timers.delete(id); }, addEventListener(name, fn) { events[name] = fn; },
    });
    const source = embedded.replace('$playbackUrl', JSON.stringify(playback))
        .replace('$expectedOrigin', JSON.stringify(origin)).replace('$expiresAt', String(expiry))
        .replace('$bearer', JSON.stringify(token))
        .replace('void start();', 'globalThis.testing = {mediaUrl, mediaFetch, readSdp}; globalThis.started = start();');
    assert.equal(/\$[A-Za-z]/.test(source), false, 'All Kotlin script substitutions must be accounted for');
    vm.runInContext(source, context);
    return { context, calls, timers, peers, status, video, events, started: context.started,
        stop: () => context.hydroboxStop(), boundary: context.testing };
}

test('POST and cleanup are confined, cookie-free, redirect-free and referrer-free', async () => {
    const h = harness(); await h.started; await h.stop();
    assert.deepEqual(h.calls.map(c => [c.url, c.options.method]), [[playback, 'POST'], [origin + '/resource/1', 'DELETE']]);
    for (const { options } of h.calls) {
        assert.equal(options.credentials, 'omit'); assert.equal(options.redirect, 'error');
        assert.equal(options.referrerPolicy, 'no-referrer'); assert.equal(options.cache, 'no-store');
        assert.equal(options.headers.Authorization, 'Bearer ' + token);
    }
    assert.equal(h.video.srcObject, null); assert.equal(h.video.onplaying, null);
    assert.equal(h.peers[0].closed, true); assert.equal(h.timers.size, 0);
});

test('adversarial Location never receives a bearer or starts playback', async () => {
    for (const location of ['https://evil.invalid/steal', '//evil.invalid/steal',
        'http://media.example.test:8443/resource', 'https://media.example.test/resource',
        'https://user:pass@media.example.test:8443/resource', '/resource?token=x', '/resource#secret',
        '/resource\\evil', '/resource\n', 'x'.repeat(2049), '']) {
        const h = harness({ location }); await h.started;
        assert.equal(h.calls.length, 1, location);
        assert.equal(h.calls[0].url, playback); assert.equal(h.peers[0].remote, false, location);
        assert.equal(h.video.srcObject, null); assert.equal(h.peers[0].closed, true);
    }
});

test('relative Location resolves within the same effective origin', async () => {
    const h = harness({ location: '../resource/1' }); await h.started; await h.stop();
    assert.equal(h.calls.at(-1).url, origin + '/resource/1');
});

test('track is not playback: waiting remains visible until video playing', async () => {
    const h = harness(); await h.started;
    assert.equal(h.status.hidden, false); assert.equal(h.status.textContent, 'Esperando vídeo…');
    h.video.onplaying(); assert.equal(h.status.hidden, true); await h.stop();
});

test('exit during POST closes a late resource without restoring token, peer or video', async () => {
    const mediaGate = deferred(), h = harness({ mediaGate });
    while (!h.calls.length) await new Promise(r => setImmediate(r));
    h.events.pagehide(); mediaGate.resolve(); await h.started;
    assert.equal(h.video.srcObject, null); assert.equal(h.peers[0].remote, false);
    assert.equal(h.calls.filter(c => c.options.method === 'DELETE').length, 1);
    await h.stop(); assert.equal(h.calls.length, 2); // repeated teardown must not resend secrets
});

test('exit while gathering ICE prevents media POST and removes the timed-out listener', async () => {
    const h = harness({ iceState: 'gathering' });
    while (!h.peers[0]?.listeners.icegatheringstatechange) await new Promise(r => setImmediate(r));
    await h.stop();
    const iceTimer = [...h.timers.values()].find(t => t.ms === 5000);
    iceTimer.fn(); await h.started;
    assert.equal(h.calls.length, 0); assert.equal(h.peers[0].listeners.icegatheringstatechange, undefined);
    assert.equal(h.timers.size, 0);
});

test('expiry closes media and clears video; expired grants never fetch', async () => {
    const h = harness(); await h.started;
    [...h.timers.values()][0].fn();
    assert.equal(h.video.srcObject, null); assert.equal(h.peers[0].closed, true);
    assert.equal(h.status.textContent, 'Sesión de cámara expirada');
    assert.equal(h.calls.at(-1).options.method, 'DELETE');
    const expired = harness({ expiry: Date.now() - 1 }); await expired.started;
    assert.equal(expired.calls.length, 0); assert.equal(expired.peers.length, 0);
});

test('bad status, MIME, SDP and oversized bodies fail closed', async () => {
    for (const options of [{ statusCode: 200 }, { contentType: 'text/html' }, { body: '<html>bad</html>' },
        { body: sdp + 'x'.repeat(65536) }]) {
        const h = harness(options); await h.started;
        assert.equal(h.peers[0].remote, false); assert.equal(h.peers[0].closed, true);
        assert.equal(h.video.srcObject, null);
    }
});

test('media network deadline aborts the request and clears the local peer', async () => {
    const h = harness({ fetchOverride: (_, options) => new Promise((_, reject) =>
        options.signal.addEventListener('abort', () => reject(new Error('aborted')))) });
    while (!h.calls.length) await new Promise(r => setImmediate(r));
    [...h.timers.values()].find(t => t.ms === 10000).fn(); await h.started;
    assert.equal(h.calls[0].options.signal.aborted, true); assert.equal(h.peers[0].closed, true);
});

test('partial plausible SDP is rejected when its reader deadline expires', async () => {
    const h = harness(); await h.started; await h.stop();
    const stream = new ReadableStream({ start(controller) { controller.enqueue(new TextEncoder().encode(sdp)); } });
    const reading = h.boundary.readSdp(new Response(stream, { status: 201, headers: { 'Content-Type': 'application/sdp' } }));
    await new Promise(r => setImmediate(r));
    [...h.timers.values()].find(t => t.ms === 10000).fn();
    await assert.rejects(reading, /response_timeout/);
});

test('guard rejects header injection and bad destinations before touching fetch', async () => {
    const h = harness(); await h.started; await h.stop(); const count = h.calls.length;
    await assert.rejects(h.boundary.mediaFetch(playback, token + '\r\nInjected: value', { method: 'POST' }), /invalid_token/);
    await assert.rejects(h.boundary.mediaFetch('https://evil.invalid/steal', token, { method: 'DELETE' }), /origin_denied/);
    assert.equal(h.calls.length, count);
});

test('peer disconnection stops video and requests confined cleanup', async () => {
    const h = harness(); await h.started; h.video.onplaying();
    h.peers[0].connectionState = 'disconnected'; h.peers[0].listeners.connectionstatechange();
    assert.equal(h.video.srcObject, null); assert.equal(h.status.hidden, false);
    assert.equal(h.status.textContent, 'Cámara desconectada');
    assert.equal(h.calls.at(-1).options.method, 'DELETE');
});
