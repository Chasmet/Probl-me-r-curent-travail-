import { writeFileSync, appendFileSync } from "node:fs";
import { join } from "node:path";
import { spawnSync } from "node:child_process";

const url=process.env.SIGNER_URL;
const oidcUrl=process.env.ACTIONS_ID_TOKEN_REQUEST_URL;
const oidcToken=process.env.ACTIONS_ID_TOKEN_REQUEST_TOKEN;
if(!url?.startsWith("https://chk-universal-1.taild9e4de.ts.net/lesfaits/api/ci/signing")
  || !oidcUrl || !oidcToken) throw Error("Signer OIDC manquant ou URL interdite.");
const request=new URL(oidcUrl);
request.searchParams.set("audience",url);
const jwt=await fetch(request,{
  headers:{authorization:"Bearer "+oidcToken},redirect:"error",signal:AbortSignal.timeout(12000)});
if(!jwt.ok)throw Error("GitHub OIDC unavailable: HTTP "+jwt.status);
const body=await jwt.json();
if(typeof body.value!=="string"||body.value.length<100)throw Error("GitHub OIDC token invalid");
const result=await fetch(url,{
  headers:{authorization:"Bearer "+body.value,accept:"application/json"},
  redirect:"error",signal:AbortSignal.timeout(90000)});
if(!result.ok)throw Error("Le serveur CHK Universal doit être mis à jour et en ligne pour la signature : HTTP "+result.status);
const key=await result.json();
if(!/^[0-9a-f]{64}$/.test(key.storePassword??"") || !/^[a-zA-Z0-9+/=]{500,150000}$/.test(key.keystoreBase64??"")
  || key.storeType!=="PKCS12")throw Error("Réponse de signature invalide.");
const store=join(process.env.RUNNER_TEMP,"lesfaits-official.p12");
writeFileSync(store,Buffer.from(key.keystoreBase64,"base64"),{mode:0o600});
const verify=spawnSync("keytool",["-list","-v","-J-Duser.language=en","-storetype","PKCS12",
  "-keystore",store,"-storepass",key.storePassword],{encoding:"utf8"});
if(verify.status!==0)throw Error("Keystore illisible.");
const alias=/^Alias name:\s*(\S.+|\S)$/mi.exec(verify.stdout)?.[1]?.trim();
if(!alias || !/^[\w .-]{1,100}$/.test(alias))throw Error("Alias de signature non trouvé.");
process.stdout.write("::add-mask::"+key.storePassword+"\n");
process.stdout.write("::add-mask::"+key.keyPassword+"\n");
appendFileSync(process.env.GITHUB_ENV,[
  "LF_SIGNING_STORE_FILE="+store,
  "LF_SIGNING_PASSWORD="+key.storePassword,
  "LF_SIGNING_KEY_PASSWORD="+key.keyPassword,
  "LF_SIGNING_ALIAS="+alias,""
].join("\n"));
process.stdout.write("Signature stable disponible depuis le serveur privé.\n");
