import { writeFileSync, appendFileSync } from 'node:fs';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
const url='https://modeliseur-trellis-mcp.onrender.com/lesfaits/ci/signing';
if(process.env.SIGNER_URL!==url || process.env.GITHUB_REPOSITORY!=='Chasmet/Probl-me-r-curent-travail-' || process.env.GITHUB_REF!=='refs/heads/main') throw Error('Service de signature ou branche non autorisé.');
const oidcUrl=process.env.ACTIONS_ID_TOKEN_REQUEST_URL;
const oidcToken=process.env.ACTIONS_ID_TOKEN_REQUEST_TOKEN;
if(!oidcUrl||!oidcToken)throw Error('OIDC indisponible.');
let key;
for(let i=0;i<7;i++){
  try{
    const request=new URL(oidcUrl);
    request.searchParams.set('audience',url);
    const jwt=await fetch(request,{headers:{authorization:'Bearer '+oidcToken},redirect:'error',signal:AbortSignal.timeout(25000)});
    if(!jwt.ok)throw Object.assign(Error('GitHub OIDC HTTP '+jwt.status),{status:jwt.status});
    const token=(await jwt.json()).value;
    if(typeof token!=='string'||token.length<100)throw Error('Identité OIDC invalide.');
    const res=await fetch(url,{headers:{authorization:'Bearer '+token,accept:'application/json'},redirect:'error',signal:AbortSignal.timeout(75000)});
    if(!res.ok)throw Object.assign(Error('Signature LES FAITS HTTP '+res.status),{status:res.status});
    key=await res.json();break;
  }catch(e){
    if(i===6||(e.status&&![404,429,500,502,503,504].includes(e.status)))throw e;
    console.log('Signature distante temporairement indisponible. Tentative '+(i+2)+'/7.');
    await new Promise(resolve=>setTimeout(resolve,Math.min(8000+i*4000,24000)));
  }
}
if(!key||key.storeType!=='PKCS12'||!/^([0-9a-f]{64})$/i.test(key.storePassword??'')||!/^([0-9a-f]{64})$/i.test(key.keyPassword??'')||!/^([A-Za-z0-9+/=]{500,150000})$/.test(key.keystoreBase64??'')||!/^([A-Za-z0-9_-]{1,80})$/.test(key.alias??'')||!/^([0-9a-f]{64})$/i.test(key.certificateSha256??''))throw Error('Réponse de signature invalide.');
const store=join(process.env.RUNNER_TEMP,'lesfaits-official.p12');
writeFileSync(store,Buffer.from(key.keystoreBase64,'base64'),{mode:0o600});
const cert=spawnSync('keytool',['-exportcert','-storetype','PKCS12','-keystore',store,'-storepass',key.storePassword,'-alias',key.alias],{timeout:15000});
if(cert.status!==0||!cert.stdout.length)throw Error('Certificat illisible.');
const fingerprint=createHash('sha256').update(cert.stdout).digest('hex');
if(fingerprint!==key.certificateSha256.toLowerCase())throw Error('Certificat permanent inattendu.');
console.log('::add-mask::'+key.storePassword);
console.log('::add-mask::'+key.keyPassword);
appendFileSync(process.env.GITHUB_ENV,['LF_SIGNING_STORE_FILE='+store,'LF_SIGNING_PASSWORD='+key.storePassword,'LF_SIGNING_KEY_PASSWORD='+key.keyPassword,'LF_SIGNING_ALIAS='+key.alias,''].join('\n'));
console.log('Signature permanente LES FAITS vérifiée via Render OIDC.');
