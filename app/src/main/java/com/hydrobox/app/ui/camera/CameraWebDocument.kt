package com.hydrobox.app.ui.camera

import com.hydrobox.app.api.ApiCameraSession
import com.hydrobox.app.api.CameraMediaPolicy
import org.json.JSONObject

object CameraWebDocument {
    private fun scriptString(value: String): String = JSONObject.quote(value)
        .replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
        .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")

    fun relayOrigin(playbackUrl: String): String {
        return "${CameraMediaPolicy.origin(playbackUrl)}/mobile-camera/"
    }

    fun render(session: ApiCameraSession): String {
        val connectOrigin = CameraMediaPolicy.origin(session.playbackUrl)
        CameraMediaPolicy.validateToken(session.accessToken)
        val playbackUrl = scriptString(session.playbackUrl)
        val bearer = scriptString(session.accessToken)
        val expectedOrigin = scriptString(connectOrigin)
        val expiresAt = session.expiresAt.toEpochMilli()
        return """
            <!doctype html>
            <html lang="es">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
              <meta http-equiv="Content-Security-Policy" content="default-src 'none'; connect-src $connectOrigin; media-src blob:; style-src 'unsafe-inline'; script-src 'unsafe-inline'">
              <style>
                html,body{width:100%;height:100%;margin:0;background:#111827;color:#e2e8f0;font-family:sans-serif}
                main{position:relative;width:100%;height:100%;display:grid;place-items:center;overflow:hidden}
                video{width:100%;height:100%;object-fit:contain;background:#020617}
                #status{position:absolute;inset:auto 12px 12px 12px;padding:10px 12px;border-radius:8px;background:rgba(15,23,42,.88);text-align:center}
              </style>
            </head>
            <body><main><video id="stream" autoplay muted playsinline></video><div id="status">Conectando cámara…</div></main>
            <script>
              (() => {
                const playbackUrl = $playbackUrl;
                const expectedOrigin = new URL($expectedOrigin).origin;
                const expiresAt = $expiresAt;
                let bearer = $bearer;
                let peer = null;
                let resourceUrl = null;
                let generation = 0;
                let stopped = false;
                let expiryTimer = null;
                const status = document.getElementById('status');
                const video = document.getElementById('stream');
                function mediaUrl(value, base) {
                  if (typeof value !== 'string' || !value || value.length > 2048
                    || /[\u0000-\u0020\u007f\\]/.test(value)) throw new Error('invalid_url');
                  const url = new URL(value, base);
                  if (url.protocol !== 'https:' || url.origin !== expectedOrigin
                    || url.username || url.password || url.search || url.hash) throw new Error('origin_denied');
                  return url.href;
                }
                async function mediaFetch(url, token, options) {
                  const target = mediaUrl(url);
                  if (typeof token !== 'string' || token.length < 32 || token.length > 8192
                    || /[^\x21-\x7e]/.test(token)) throw new Error('invalid_token');
                  const controller = new AbortController();
                  const timer = setTimeout(() => controller.abort(), 10000);
                  try {
                    return await fetch(target, {...options,
                      headers:{...options.headers, Authorization:'Bearer ' + token},
                      credentials:'omit', redirect:'error', referrerPolicy:'no-referrer',
                      cache:'no-store', signal:controller.signal});
                  } finally { clearTimeout(timer); }
                }
                async function readSdp(response) {
                  if (response.status !== 201 || !response.body
                    || response.headers.get('Content-Type')?.split(';')[0].trim().toLowerCase() !== 'application/sdp')
                    throw new Error('invalid_response');
                  const reader = response.body.getReader();
                  const decoder = new TextDecoder('utf-8', {fatal:true});
                  let bytes = 0, text = '', expired = false;
                  const timer = setTimeout(() => { expired = true; void reader.cancel().catch(() => {}); }, 10000);
                  try {
                    while (true) {
                      const {done, value} = await reader.read();
                      if (done) break;
                      bytes += value.byteLength;
                      if (bytes > 65536) throw new Error('response_too_large');
                      text += decoder.decode(value, {stream:true});
                    }
                    if (expired) throw new Error('response_timeout');
                    text += decoder.decode();
                    if (!text.startsWith('v=0') || !text.includes('m=video ')) throw new Error('invalid_sdp');
                    return text;
                  } finally {
                    clearTimeout(timer);
                    await reader.cancel().catch(() => {});
                    reader.releaseLock();
                  }
                }
                const waitForIce = (pc) => pc.iceGatheringState === 'complete' ? Promise.resolve() : new Promise(resolve => {
                  const done = () => {
                    clearTimeout(timeout);
                    pc.removeEventListener('icegatheringstatechange', changed);
                    resolve();
                  };
                  const timeout = setTimeout(done, 5000);
                  const changed = () => { if (pc.iceGatheringState === 'complete') done(); };
                  pc.addEventListener('icegatheringstatechange', changed);
                });
                async function stop() {
                  stopped = true;
                  generation++;
                  clearTimeout(expiryTimer);
                  expiryTimer = null;
                  const token = bearer;
                  const resource = resourceUrl;
                  bearer = null;
                  resourceUrl = null;
                  peer?.close();
                  peer = null;
                  video.onplaying = null;
                  video.srcObject = null;
                  status.hidden = false;
                  status.textContent = 'Cámara cerrada';
                  if (resource && token) {
                    await mediaFetch(resource, token, {method:'DELETE', keepalive:true}).catch(() => {});
                  }
                }
                globalThis.hydroboxStop = stop;
                async function start() {
                  if (stopped) return;
                  const current = ++generation;
                  const token = bearer;
                  try {
                    const target = mediaUrl(playbackUrl);
                    const remaining = expiresAt - Date.now();
                    if (!Number.isFinite(remaining) || remaining <= 0) throw new Error('expired_session');
                    expiryTimer = setTimeout(() => {
                      void stop(); status.textContent = 'Sesión de cámara expirada';
                    }, Math.min(remaining, 2147483647));
                    const pc = new RTCPeerConnection();
                    peer = pc;
                    pc.addTransceiver('video', {direction:'recvonly'});
                    pc.addEventListener('track', event => {
                      if (current !== generation) return;
                      video.srcObject = event.streams[0] || new MediaStream([event.track]);
                    });
                    video.onplaying = () => {
                      if (current !== generation || !video.srcObject) return;
                      status.hidden = true;
                    };
                    pc.addEventListener('connectionstatechange', () => {
                      if (current !== generation) return;
                      if (['failed','disconnected','closed'].includes(pc.connectionState)) {
                        void stop();
                        status.textContent = 'Cámara desconectada';
                      }
                    });
                    await pc.setLocalDescription(await pc.createOffer());
                    await waitForIce(pc);
                    if (current !== generation) return;
                    const response = await mediaFetch(target, token, {
                      method:'POST', headers:{'Content-Type':'application/sdp'}, body:pc.localDescription.sdp
                    });
                    if (response.status !== 201) throw new Error('media_rejected');
                    const resource = mediaUrl(response.headers.get('Location'), target);
                    if (current !== generation) {
                      await mediaFetch(resource, token, {method:'DELETE', keepalive:true}).catch(() => {});
                      return;
                    }
                    resourceUrl = resource;
                    const sdp = await readSdp(response);
                    if (current !== generation) return;
                    await pc.setRemoteDescription({type:'answer', sdp});
                    if (current !== generation) return;
                    status.textContent = 'Esperando vídeo…';
                  } catch (_) {
                    if (current !== generation) return;
                    await stop();
                    status.hidden = false;
                    status.textContent = 'No fue posible conectar la cámara';
                  }
                }
                addEventListener('pagehide', () => { void stop(); });
                void start();
              })();
            </script></body></html>
        """.trimIndent()
    }
}
