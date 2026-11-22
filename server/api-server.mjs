import http from 'node:http';
import { allIncidents, sanitizePayload, saveClientSnapshot, verifyToken } from './store.mjs';

const host = process.env.SYNC30_BIND || '127.0.0.1';
const port = Number(process.env.SYNC30_API_PORT || '8787');
if ((process.env.SYNC30_API_TOKEN || '').length < 24) {
  console.error('Définir SYNC30_API_TOKEN (24 caractères minimum).'); process.exit(1);
}
function respond(res, status, value) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
  res.end(JSON.stringify(value));
}
const server = http.createServer(async (req, res) => {
  if (req.url === '/health' && req.method === 'GET') return respond(res, 200, { status: 'ok', app: 'LES FAITS SYNC 30' });
  if (!req.url.startsWith('/api/')) return respond(res, 404, { error: 'Introuvable' });
  if (!verifyToken(req.headers.authorization)) return respond(res, 401, { error: 'Non autorisé' });
  if (req.url === '/api/incidents' && req.method === 'GET') return respond(res, 200, { incidents: allIncidents() });
  if (req.url !== '/api/incidents' || req.method !== 'POST') return respond(res, 405, { error: 'Méthode non autorisée' });
  try {
    const chunks = []; let bytes = 0;
    for await (const chunk of req) {
      bytes += chunk.length; if (bytes > 5_000_000) return respond(res, 413, { error: 'Payload trop volumineux' });
      chunks.push(chunk);
    }
    const payload = sanitizePayload(JSON.parse(Buffer.concat(chunks).toString('utf8')));
    saveClientSnapshot(payload);
    return respond(res, 200, { ok: true, count: payload.incidents.length });
  } catch (err) { return respond(res, 400, { error: 'Données incorrectes ou stockage indisponible' }); }
});
server.listen(port, host, () => console.log('API LES FAITS sur http://' + host + ':' + port + ' (TLS à fournir via reverse proxy)'));
