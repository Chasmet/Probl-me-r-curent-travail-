import test from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

test('API sécurisée : authentification et import/export', async () => {
  const folder=fs.mkdtempSync(path.join(os.tmpdir(),'sync30-'));
  const port=30900+Math.floor(Math.random()*900);
  const key='test_token_de_longueur_suffisante_123456789';
  const env={...process.env,SYNC30_BIND:'127.0.0.1',SYNC30_API_PORT:String(port),SYNC30_API_TOKEN:key,SYNC30_DATA_DIR:folder};
  const child=spawn(process.execPath,['api-server.mjs'],{cwd:new URL('.',import.meta.url).pathname,env,stdio:'ignore'});
  try {
    const u='http://127.0.0.1:'+port;
    let ready=false;
    for(let t=0;t<50;t++){try{const r=await fetch(u+'/health');if(r.ok){ready=true;break;}}catch{} await new Promise(r=>setTimeout(r,100));}
    assert.equal(ready,true,'Le serveur démarre');
    assert.equal((await fetch(u+'/api/incidents')).status,401);
    const payload={deviceId:'12345678-1234-1234-1234-123456789abc',incidents:[{id:1,title:'Contrôle',description:'Essai',space:'TERRAIN',recurring:true,notes:[]}]};
    const put=await fetch(u+'/api/incidents',{method:'POST',headers:{authorization:'Bearer '+key,'content-type':'application/json'},body:JSON.stringify(payload)});
    assert.equal(put.status,200);
    const r=await fetch(u+'/api/incidents',{headers:{authorization:'Bearer '+key}});
    const body=await r.json();
    assert.equal(body.incidents.length,1);
    assert.equal(body.incidents[0].title,'Contrôle');
    assert.equal(body.incidents[0].recurring,true);
  } finally { child.kill();fs.rmSync(folder,{recursive:true,force:true}); }
});
