import { writeFileSync, appendFileSync } from 'node:fs';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

// GitHub Actions Secrets only. Never use temporary signing keys or external signers.
const base64=(process.env.LF_SIGNING_P12_BASE64||'').trim();
const password=process.env.LF_SIGNING_PASSWORD||'';
const keyPassword=process.env.LF_SIGNING_KEY_PASSWORD||password;
const hasBase64=base64.length>0, hasPassword=password.length>0;
if(hasBase64!==hasPassword){
  throw Error('Signature GitHub Secrets partiellement configurée : renseigner le PKCS12 et son mot de passe.');
}
if(!hasBase64){
  throw Error('Clé permanente absente dans GitHub Actions Secrets. Publication bloquée sans serveur externe ni clé temporaire.');
}else{
  if(process.env.GITHUB_REPOSITORY!=='Chasmet/Probl-me-r-curent-travail-'||
      process.env.GITHUB_REF!=='refs/heads/main'){
    throw Error('Signature réservée à la branche officielle main de LES FAITS.');
  }
  if(!process.env.RUNNER_TEMP||!process.env.GITHUB_ENV){
    throw Error('Environnement GitHub Actions incomplet.');
  }
  if(!/^[A-Za-z0-9+/=\r\n]+$/.test(base64)||base64.length>200000){
    throw Error('Keystore PKCS12 invalide.');
  }
  const decoded=Buffer.from(base64,'base64');
  if(decoded.length<500||decoded.length>150000)throw Error('Taille de keystore PKCS12 incorrecte.');
  const store=join(process.env.RUNNER_TEMP,'lesfaits-official.p12');
  writeFileSync(store,decoded,{mode:0o600});
  const verify=spawnSync('keytool',['-list','-v','-J-Duser.language=en',
    '-storetype','PKCS12','-keystore',store,'-storepass',password],{encoding:'utf8',timeout:15000});
  if(verify.status!==0)throw Error('Keystore PKCS12 GitHub illisible ou mot de passe incorrect.');
  const aliases=[...verify.stdout.matchAll(/^Alias name:\s*(.+?)\s*$/gmi)].map(m=>m[1].trim());
  if(aliases.length!==1||!/^[\w .-]{1,100}$/.test(aliases[0])){
    throw Error('Le keystore doit contenir un seul alias compatible.');
  }
  const expected=(process.env.LF_EXPECTED_CERT_SHA256||'').replace(/[:\s]/g,'').toUpperCase();
  if(!expected)throw Error('LF_EXPECTED_CERT_SHA256 obligatoire pour verrouiller la clé permanente.');
  if(expected){
    if(!/^[0-9A-F]{64}$/.test(expected))throw Error('Empreinte SHA256 attendue invalide.');
    const found=verify.stdout.match(/SHA256:\s*([0-9A-Fa-f:]{64,95})/i)?.[1]?.replace(/:/g,'').toUpperCase();
    if(found!==expected)throw Error('Certificat différent de la signature officielle attendue.');
  }
  process.stdout.write('::add-mask::'+password+'\n');
  process.stdout.write('::add-mask::'+keyPassword+'\n');
  appendFileSync(process.env.GITHUB_ENV,[
    'LF_SIGNING_STORE_FILE='+store,
    'LF_SIGNING_PASSWORD='+password,
    'LF_SIGNING_KEY_PASSWORD='+keyPassword,
    'LF_SIGNING_ALIAS='+aliases[0],
    ''
  ].join('\n'));
  process.stdout.write('Signature APK récupérée depuis GitHub Actions Secrets. Aucun service de signature externe.\n');
}
