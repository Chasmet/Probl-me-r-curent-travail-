package fr.sync30.lesfaits;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.net.ssl.HttpsURLConnection;

final class OAuthClient {
    static final String BASE="https://chk-universal-1.taild9e4de.ts.net";
    static final String MCP_URL=BASE+"/mcp";
    private static final String REDIRECT="fr.sync30.lesfaits://oauth/callback";
    interface Listener{void result(boolean ok,String message);}
    private OAuthClient(){}
    private static String random(int count){
        byte[] b=new byte[count];new SecureRandom().nextBytes(b);
        return Base64.encodeToString(b,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
    }
    private static String enc(String value)throws Exception{return URLEncoder.encode(value,"UTF-8");}
    private static String query(String... parts)throws Exception{
        StringBuilder result=new StringBuilder();
        for(int i=0;i<parts.length;i+=2){
            if(result.length()>0)result.append("&");
            result.append(enc(parts[i])).append("=").append(enc(parts[i+1]));
        }
        return result.toString();
    }
    private static JSONObject post(String path,String data,String contentType)throws Exception{
        HttpsURLConnection c=null;
        try{
            c=(HttpsURLConnection)new URL(BASE+path).openConnection();
            c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type",contentType);c.setRequestProperty("Accept","application/json");
            c.setDoOutput(true);
            byte[] body=data.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(body.length);
            try(OutputStream out=c.getOutputStream()){out.write(body);}
            int status=c.getResponseCode();
            InputStream src=status>=400?c.getErrorStream():c.getInputStream();
            String response="";
            if(src!=null)try(InputStream in=src;ByteArrayOutputStream buff=new ByteArrayOutputStream()){
                byte[] bytes=new byte[4096];int n;int total=0;
                while((n=in.read(bytes))!=-1){total+=n;if(total>100000)throw new Exception("Réponse serveur trop longue");buff.write(bytes,0,n);}
                response=buff.toString("UTF-8");
            }
            if(status<200||status>=300)throw new Exception("Serveur OAuth : HTTP "+status+" "+response.substring(0,Math.min(180,response.length())));
            return new JSONObject(response);
        }finally{if(c!=null)c.disconnect();}
    }
    static void begin(Activity activity,SecurePrefs prefs,Listener callback){
        new Thread(()->{
            try{
                String clientId=prefs.value("client_id");
                if(clientId.isEmpty()){
                    JSONObject registration=new JSONObject();
                    registration.put("client_name","LES FAITS — SYNC 30");
                    registration.put("token_endpoint_auth_method","none");
                    registration.put("redirect_uris",new org.json.JSONArray().put(REDIRECT));
                    clientId=post("/oauth/register",registration.toString(),"application/json").getString("client_id");
                    prefs.value("client_id",clientId);
                }
                String verifier=random(40),state=random(24);
                byte[] hash=MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
                String challenge=Base64.encodeToString(hash,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
                prefs.protectedValue("verifier",verifier);
                prefs.protectedValue("state",state);
                Uri authorize=Uri.parse(BASE+"/oauth/authorize").buildUpon()
                    .appendQueryParameter("response_type","code")
                    .appendQueryParameter("client_id",clientId)
                    .appendQueryParameter("redirect_uri",REDIRECT)
                    .appendQueryParameter("code_challenge_method","S256")
                    .appendQueryParameter("code_challenge",challenge)
                    .appendQueryParameter("resource",MCP_URL)
                    .appendQueryParameter("scope","mcp:write")
                    .appendQueryParameter("state",state).build();
                activity.runOnUiThread(()->{
                    try{activity.startActivity(new Intent(Intent.ACTION_VIEW,authorize));}
                    catch(Exception e){callback.result(false,"Navigateur introuvable : "+e.getMessage());}
                });
            }catch(Exception e){activity.runOnUiThread(()->callback.result(false,e.getMessage()));}
        }).start();
    }
    static void complete(Activity activity,SecurePrefs prefs,Uri callbackUri,Listener callback){
        String expected=prefs.protectedValue("state");
        String state=callbackUri.getQueryParameter("state"),code=callbackUri.getQueryParameter("code");
        String iss=callbackUri.getQueryParameter("iss");
        if(expected.isEmpty()||!expected.equals(state)||!BASE.equals(iss)||code==null||code.isEmpty()){
            callback.result(false,"Réponse OAuth invalide ou autorisation expirée.");return;
        }
        String verifier=prefs.protectedValue("verifier");
        if(verifier.isEmpty()){callback.result(false,"Session de connexion introuvable.");return;}
        new Thread(()->{
            try{
                JSONObject tokens=post("/oauth/token",query(
                    "grant_type","authorization_code","client_id",prefs.value("client_id"),
                    "code",code,"redirect_uri",REDIRECT,"resource",MCP_URL,
                    "code_verifier",verifier),"application/x-www-form-urlencoded");
                save(prefs,tokens);
                prefs.protectedValue("state","");prefs.protectedValue("verifier","");
                activity.runOnUiThread(()->callback.result(true,"LES FAITS est autorisé sur ton serveur. Aucun jeton à saisir."));
            }catch(Exception e){activity.runOnUiThread(()->callback.result(false,e.getMessage()));}
        }).start();
    }
    private static void save(SecurePrefs p,JSONObject data)throws Exception {
        String access=data.getString("access_token"),refresh=data.getString("refresh_token");
        p.protectedValue("access",access);p.protectedValue("refresh",refresh);
        long expiration=System.currentTimeMillis()+Math.max(30,data.optLong("expires_in",3600)-60)*1000;
        p.value("expiration",String.valueOf(expiration));
    }
    static String validAccessToken(SecurePrefs prefs)throws Exception {
        String access=prefs.protectedValue("access");
        long expire;
        try{expire=Long.parseLong(prefs.value("expiration"));}catch(Exception e){expire=0;}
        if(!access.isEmpty()&&expire>System.currentTimeMillis())return access;
        String refresh=prefs.protectedValue("refresh");
        if(refresh.isEmpty())return "";
        JSONObject tokens=post("/oauth/token",query(
            "grant_type","refresh_token","client_id",prefs.value("client_id"),
            "refresh_token",refresh,"resource",MCP_URL),"application/x-www-form-urlencoded");
        save(prefs,tokens);
        return prefs.protectedValue("access");
    }
}
