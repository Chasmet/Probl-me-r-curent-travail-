import http from 'node:http';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { z } from 'zod';
import { allIncidents, verifyToken } from './store.mjs';

const host = process.env.SYNC30_BIND || '127.0.0.1';
const port = Number(process.env.SYNC30_MCP_PORT || 8788);
if ((process.env.SYNC30_API_TOKEN || '').length < 24) {
  console.error('Définir SYNC30_API_TOKEN (24 caractères minimum).');process.exit(1);
}
function api() {
  const mcp = new McpServer({ name: 'les-faits-sync30', version: '0.1.0' });
  mcp.registerTool('lister_les_faits', {
    description: 'Consulte les faits enregistrés par le propriétaire, sans les modifier.',
    inputSchema: { espace: z.enum(['TOUT', 'TERRAIN', 'PERSONNEL']).optional(), recurrent_uniquement: z.boolean().optional(), limite: z.number().int().min(1).max(100).optional() }
  }, async ({ espace = 'TOUT', recurrent_uniquement = false, limite = 50 }) => {
    const list = allIncidents().filter(i => (espace === 'TOUT' || i.space === espace) && (!recurrent_uniquement || i.recurring)).slice(0, limite);
    return { content: [{ type: 'text', text: JSON.stringify(list) }] };
  });
  mcp.registerTool('lire_un_fait', {
    description: 'Lit le détail et le suivi du signalement correspondant à un ID et un appareil.',
    inputSchema: { id: z.number().int().min(1), deviceId: z.string().min(12) }
  }, async ({ id, deviceId }) => {
    const item = allIncidents().find(x => x.id === id && x.deviceId === deviceId);
    return { content: [{ type: 'text', text: JSON.stringify(item || { error: 'Introuvable' }) }] };
  });
  mcp.registerTool('statistiques_les_faits', {
    description: 'Calcule le total, les états et le nombre de faits récurrents.',
    inputSchema: {}
  }, async () => {
    const a = allIncidents();const byStatus = {};
    for (const i of a) byStatus[i.status] = (byStatus[i.status] || 0) + 1;
    return { content: [{ type: 'text', text: JSON.stringify({ total: a.length, recurrents: a.filter(i => i.recurring).length, parStatut: byStatus }) }] };
  });
  return mcp;
}
function send(res, code, text) {
  res.writeHead(code, { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' });res.end(text);
}
http.createServer(async (req, res) => {
  if (req.url === '/health' && req.method === 'GET') return send(res, 200, 'LES FAITS MCP OK');
  if (req.url !== '/mcp') return send(res, 404, 'Not found');
  if (!verifyToken(req.headers.authorization)) return send(res, 401, 'Unauthorized');
  if (req.method !== 'POST') return send(res, 405, 'Only POST is available');
  try {
    const chunks=[];let size=0;
    for await (const c of req) { size+=c.length; if(size>1_000_000)return send(res,413,'Too large');chunks.push(c); }
    const request=JSON.parse(Buffer.concat(chunks).toString('utf8'));
    const mcp=api();
    const transport=new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
    res.on('close',()=>{transport.close().catch(()=>{});mcp.close().catch(()=>{});});
    await mcp.connect(transport);
    await transport.handleRequest(req,res,request);
  } catch (err) { if(!res.headersSent)send(res,400,'Invalid MCP request'); }
}).listen(port,host,()=>console.log('MCP LES FAITS sur http://' + host + ':' + port + '/mcp (accès local uniquement recommandé)'));
