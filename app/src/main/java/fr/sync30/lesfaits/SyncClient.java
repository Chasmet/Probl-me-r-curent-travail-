package fr.sync30.lesfaits;

import android.app.Activity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;

final class SyncClient {
    interface Listener { void result(boolean ok,String message); }
    static void send(Activity a,Store db,SecurePrefs prefs,Listener listener) {
        String endpoint=prefs.url().trim(),token=prefs.token();
        if(!endpoint.startsWith("https://") || token.isEmpty()) {
            listener.result(false,"Configure une URL HTTPS et un jeton dans Réglages.");return;
        }
        new Thread(()->{
            HttpsURLConnection connection=null;
            try {
                JSONArray arr=new JSONArray();
                List<Store.Incident> incidents=db.all();
                for(Store.Incident i:incidents) {
                    JSONObject o=new JSONObject();
                    o.put("id",i.id); o.put("space",i.space); o.put("title",i.title);
                    o.put("category",i.category); o.put("description",i.description);
                    o.put("location",i.location); o.put("occurred",i.occurred);
                    o.put("created",i.created); o.put("severity",i.severity);
                    o.put("status",i.status); o.put("recurring",i.recurring);
                    JSONArray notes=new JSONArray();
                    for(Store.Note n:db.notes(i.id)){
                        JSONObject entry=new JSONObject();
                        entry.put("at",n.at);entry.put("text",n.text);notes.put(entry);
                    }
                    o.put("notes",notes); arr.put(o);
                }
                JSONObject payload=new JSONObject();
                payload.put("deviceId",prefs.deviceId());
                payload.put("exportedAt",System.currentTimeMillis());
                payload.put("incidents",arr);
                byte[] body=payload.toString().getBytes(StandardCharsets.UTF_8);
                if(body.length>5_000_000)throw new Exception("Volume trop important pour une synchronisation.");
                URL url=new URL(endpoint.replaceAll("/+$","")+"/lesfaits/api/incidents");
                if(!"https".equalsIgnoreCase(url.getProtocol()))throw new Exception("HTTPS obligatoire.");
                connection=(HttpsURLConnection)url.openConnection();
                connection.setConnectTimeout(15000);connection.setReadTimeout(20000);
                connection.setRequestMethod("POST");connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
                connection.setRequestProperty("Authorization","Bearer "+token);
                connection.setFixedLengthStreamingMode(body.length);
                try(OutputStream stream=connection.getOutputStream()){stream.write(body);}
                int status=connection.getResponseCode();
                if(status<200||status>=300)throw new Exception("Serveur : HTTP "+status);
                a.runOnUiThread(()->listener.result(true,incidents.size()+" dossier(s) synchronisé(s)."));
            }catch(Exception e) {
                String message=e.getMessage()==null?"Erreur de connexion":e.getMessage();
                a.runOnUiThread(()->listener.result(false,message));
            }finally{if(connection!=null)connection.disconnect();}
        }).start();
    }
}
