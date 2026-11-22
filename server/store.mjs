import fs from 'node:fs';
import path from 'node:path';
import { randomBytes, timingSafeEqual } from 'node:crypto';

const directory = path.resolve(process.env.SYNC30_DATA_DIR || new URL('./data/', import.meta.url).pathname);
const dbFile = path.join(directory, 'incidents.json');

export function verifyToken(header) {
  const expected = process.env.SYNC30_API_TOKEN || '';
  const received = (header || '').replace(/^Bearer\s+/i, '');
  if (expected.length < 24 || received.length !== expected.length) return false;
  return timingSafeEqual(Buffer.from(expected), Buffer.from(received));
}
export function readData() {
  try {
    const value = JSON.parse(fs.readFileSync(dbFile, 'utf8'));
    if (value && typeof value === 'object' && !Array.isArray(value)) return value;
  } catch (err) { if (err.code !== 'ENOENT') throw err; }
  return {};
}
function limited(value, max = 10000) { return String(value ?? '').slice(0, max); }
export function sanitizePayload(data) {
  if (!data || typeof data !== 'object' || !Array.isArray(data.incidents) || data.incidents.length > 10000) throw new Error('Corps JSON invalide');
  const deviceId = limited(data.deviceId, 70);
  if (!/^[a-zA-Z0-9-]{12,70}$/.test(deviceId)) throw new Error('Identifiant client invalide');
  const incidents = data.incidents.map(item => {
    if (!item || !Number.isSafeInteger(item.id) || item.id < 1) throw new Error('ID invalide');
    const n = (v) => Number.isFinite(Number(v)) ? Number(v) : 0;
    const notes = Array.isArray(item.notes) ? item.notes.slice(0, 2000).map(x => ({ at: n(x.at), text: limited(x.text, 10000) })) : [];
    return {
      id: item.id,
      space: item.space === 'PERSONNEL' ? 'PERSONNEL' : 'TERRAIN',
      category: limited(item.category, 250), title: limited(item.title, 500),
      description: limited(item.description, 15000), location: limited(item.location, 1000),
      occurred: n(item.occurred), created: n(item.created),
      severity: limited(item.severity, 50), status: limited(item.status, 50),
      recurring: Boolean(item.recurring), notes
    };
  });
  return { deviceId, exportedAt: Date.now(), incidents };
}
export function saveClientSnapshot(payload) {
  fs.mkdirSync(directory, { recursive: true, mode: 0o700 });
  const db = readData();
  db[payload.deviceId] = { exportedAt: payload.exportedAt, incidents: payload.incidents };
  const tmp = dbFile + '.' + randomBytes(6).toString('hex') + '.tmp';
  try {
    fs.writeFileSync(tmp, JSON.stringify(db), { mode: 0o600 });
    fs.renameSync(tmp, dbFile);
  } finally { try { fs.unlinkSync(tmp); } catch {} }
}
export function allIncidents() {
  return Object.entries(readData()).flatMap(([deviceId, value]) =>
    (value.incidents || []).map(item => ({ ...item, deviceId })));
}
