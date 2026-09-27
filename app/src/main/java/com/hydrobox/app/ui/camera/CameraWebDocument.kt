package com.hydrobox.app.ui.camera

import com.hydrobox.app.api.ApiCameraSession
import org.json.JSONObject
import java.net.URI

object CameraWebDocument {
    private fun origin(playbackUrl: String): String {
        val uri = URI(playbackUrl)
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank())
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "https://${uri.host}$port"
    }

    fun relayOrigin(playbackUrl: String): String {
        return "${origin(playbackUrl)}/mobile-camera/"
    }

    fun render(session: ApiCameraSession): String {
        val playbackUrl = JSONObject.quote(session.playbackUrl)
        val bearer = JSONObject.quote(session.accessToken)
        val connectOrigin = origin(session.playbackUrl)
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
                let bearer = $bearer;
                let peer = null;
                let resourceUrl = null;
                const status = document.getElementById('status');
                const video = document.getElementById('stream');
                const waitForIce = (pc) => pc.iceGatheringState === 'complete' ? Promise.resolve() : new Promise(resolve => {
                  const timeout = setTimeout(resolve, 5000);
                  const changed = () => {
                    if (pc.iceGatheringState !== 'complete') return;
                    clearTimeout(timeout);
                    pc.removeEventListener('icegatheringstatechange', changed);
                    resolve();
                  };
                  pc.addEventListener('icegatheringstatechange', changed);
                });
                async function stop() {
                  const token = bearer;
                  bearer = null;
                  peer?.close();
                  peer = null;
                  video.srcObject = null;
                  if (resourceUrl && token) {
                    await fetch(resourceUrl, {method:'DELETE', headers:{Authorization:`Bearer ${'$'}{token}`}, keepalive:true}).catch(() => {});
                  }
                  resourceUrl = null;
                }
                globalThis.hydroboxStop = stop;
                async function start() {
                  try {
                    peer = new RTCPeerConnection();
                    peer.addTransceiver('video', {direction:'recvonly'});
                    peer.addEventListener('track', event => {
                      video.srcObject = event.streams[0];
                      status.hidden = true;
                    });
                    peer.addEventListener('connectionstatechange', () => {
                      if (['failed','disconnected','closed'].includes(peer?.connectionState)) {
                        status.hidden = false;
                        status.textContent = 'Cámara desconectada';
                      }
                    });
                    await peer.setLocalDescription(await peer.createOffer());
                    await waitForIce(peer);
                    const response = await fetch(playbackUrl, {
                      method:'POST', cache:'no-store',
                      headers:{Authorization:`Bearer ${'$'}{bearer}`, 'Content-Type':'application/sdp'},
                      body:peer.localDescription.sdp
                    });
                    if (!response.ok) throw new Error('media_rejected');
                    const location = response.headers.get('Location');
                    resourceUrl = location ? new URL(location, playbackUrl).toString() : null;
                    await peer.setRemoteDescription({type:'answer', sdp:await response.text()});
                    status.textContent = 'Esperando vídeo…';
                  } catch (_) {
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
