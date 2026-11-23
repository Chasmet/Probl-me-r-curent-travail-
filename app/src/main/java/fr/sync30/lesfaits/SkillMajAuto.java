package fr.sync30.lesfaits;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

/**
 * SkillMajAuto — moteur de mise à jour autonome de LES FAITS, inspiré de XDebt.
 * Vérifie GitHub Releases automatiquement à l’ouverture et via JobScheduler,
 * récupère l’APK officiel, contrôle SHA-256, package et certificat puis lance
 * PackageInstaller avec confirmation Android. Sans accès au serveur Windows.
 * Android requires explicit user approval before installation. Updates are
 * never installed silently, and the on-device signing certificate is checked.
 */
final class SkillMajAuto {
    private static final String TAG="LesFaitsSkillMajAuto";
    private static final String API="https://api.github.com/repos/Chasmet/Probl-me-r-curent-travail-/releases/latest";
    private static final String DOWNLOAD_PREFIX="https://github.com/Chasmet/Probl-me-r-curent-travail-/releases/download/";
    private static final ExecutorService pool=Executors.newSingleThreadExecutor();
    private static final long CHECK_INTERVAL=12L*60*60*1000;
    private static final long MAX_APK=150L*1024*1024;
    private static final String PREFS="lesfaits_skillmajauto";
    private static final int JOB_ID=53030;
    private static final int NOTIFICATION_ID=53031;
    private static final String CHANNEL_ID="lesfaits_skillmajauto";
    private static final String OPEN_ACTION="fr.sync30.lesfaits.UPDATE_OPEN";
    static final class Release {
        final String version,url,digest,notes;
        Release(String v,String u,String d,String n){version=v;url=u;digest=d;notes=n;}
    }
    private SkillMajAuto(){}
    static boolean autoEnabled(Context ctx){
        return ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean("automatic",
            ctx.getSharedPreferences("lesfaits_updater",Context.MODE_PRIVATE).getBoolean("automatic",true));
    }
    static void setAuto(Context ctx,boolean enabled){
        ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean("automatic",enabled).apply();
        schedule(ctx);
    }
    static void onAppOpen(Activity activity) {
        schedule(activity);
        if(!autoEnabled(activity))return;
        long now=System.currentTimeMillis();
        long last=activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getLong("last_check",0);
        if(last>0 && now-last<CHECK_INTERVAL && now>=last)return;
        check(activity,false);
    }
    /** Periodic check when Android permits it. Installation is never silent. */
    static void schedule(Context ctx){
        JobScheduler jobs=(JobScheduler)ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(jobs==null)return;
        if(!autoEnabled(ctx)){jobs.cancel(JOB_ID);return;}
        // Re-opening the app must not reset the daily interval.
        if(jobs.getPendingJob(JOB_ID)!=null)return;
        JobInfo job=new JobInfo.Builder(JOB_ID,new ComponentName(ctx,UpdateCheckJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setPeriodic(CHECK_INTERVAL)
            .build();
        jobs.schedule(job);
    }
    static void checkBackground(Context ctx,Runnable done){
        pool.execute(()->{
            try{
                if(!autoEnabled(ctx))return;
                Release release=latest();
                if(release!=null && compareVersion(release.version,versionName(ctx))>0)notifyUpdate(ctx,release);
            }catch(Exception e){Log.w(TAG,"Background check failed: "+e.getClass().getSimpleName());}
            finally{done.run();}
        });
    }
    private static void notifyUpdate(Context ctx,Release release){
        if(Build.VERSION.SDK_INT>=33 &&
            ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)return;
        android.content.SharedPreferences prefs=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if(release.version.equals(prefs.getString("notified_version","")))return;
        NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        NotificationChannel channel=new NotificationChannel(CHANNEL_ID,"Mises à jour LES FAITS",NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Notification des nouvelles versions officielles");
        nm.createNotificationChannel(channel);
        Intent open=new Intent(ctx,MainActivity.class).setAction(OPEN_ACTION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap=PendingIntent.getActivity(ctx,NOTIFICATION_ID,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notice=new Notification.Builder(ctx,CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Nouvelle version LES FAITS")
            .setContentText("Version "+release.version+" disponible. Vérifier et installer.")
            .setContentIntent(tap).setAutoCancel(true).build();
        nm.notify(NOTIFICATION_ID,notice);
        prefs.edit().putString("notified_version",release.version).apply();
    }
    static void onNotificationTap(Activity activity,Intent intent){
        if(intent==null || !OPEN_ACTION.equals(intent.getAction()))return;
        intent.setAction(null);
        check(activity,true);
    }
    static void check(Activity activity,boolean manual) {
        pool.execute(()->{
            try {
                Release release=latest();
                long now=System.currentTimeMillis();
                activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putLong("last_check",now).apply();
                boolean newer=release!=null && compareVersion(release.version,versionName(activity))>0;
                activity.runOnUiThread(()->{
                    if(activity.isFinishing()||activity.isDestroyed())return;
                    if(newer) promptUpdate(activity,release);
                    else if(manual){
                        new AlertDialog.Builder(activity).setTitle("Mises à jour")
                            .setMessage(release==null?"Aucune APK officielle publiée sur GitHub Releases. La recherche automatique est prête, mais la première version signée doit encore être publiée.":"Aucune version plus récente n'est disponible.")
                            .setPositiveButton("OK",null).show();
                    }
                });
            } catch(Exception e){
                Log.w(TAG,"Check failed: "+e.getClass().getSimpleName());
                if(manual)activity.runOnUiThread(()->new AlertDialog.Builder(activity)
                    .setTitle("Vérification impossible")
                    .setMessage("Connexion à GitHub indisponible. Réessaie lorsque le réseau est disponible.")
                    .setPositiveButton("OK",null).show());
            }
        });
    }
    static String versionName(Context context) {
        try{return context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName;}
        catch(Exception e){return "0.0.0";}
    }
    private static int compareVersion(String a,String b){
        String[] left=a.replaceFirst("^[vV]","").split("\\.");
        String[] right=b.replaceFirst("^[vV]","").split("\\.");
        for(int i=0;i<Math.max(left.length,right.length);i++){
            int x=i<left.length?parsePart(left[i]):0;
            int y=i<right.length?parsePart(right[i]):0;
            if(x!=y)return Integer.compare(x,y);
        }
        return 0;
    }
    private static int parsePart(String part) {
        try{return Integer.parseInt(part.replaceAll("[^0-9].*$",""));}
        catch(Exception e){return 0;}
    }
    private static Release latest()throws Exception {
        HttpsURLConnection c=null;
        try{
            c=(HttpsURLConnection)new URL(API).openConnection();
            c.setConnectTimeout(12000);c.setReadTimeout(12000);
            c.setRequestProperty("Accept","application/vnd.github+json");
            c.setRequestProperty("User-Agent","LES-FAITS-SYNC30-Android");
            int status=c.getResponseCode();
            if(status==404)return null;
            if(status!=200)throw new Exception("GitHub HTTP "+status);
            String payload=readSmall(c.getInputStream(),150000);
            JSONObject json=new JSONObject(payload);
            if(json.optBoolean("draft")||json.optBoolean("prerelease"))return null;
            String tag=json.optString("tag_name","");
            if(!tag.matches("v?\\d+\\.\\d+\\.\\d+"))return null;
            JSONArray assets=json.optJSONArray("assets");
            if(assets==null)return null;
            for(int i=0;i<assets.length();i++){
                JSONObject item=assets.optJSONObject(i);
                if(item==null)continue;
                String name=item.optString("name","");
                String url=item.optString("browser_download_url","");
                String digest=item.optString("digest","");
                if(name.equals("LES-FAITS-SYNC30.apk") && url.startsWith(DOWNLOAD_PREFIX)
                    && url.endsWith("/LES-FAITS-SYNC30.apk")
                    && digest.matches("sha256:[0-9a-fA-F]{64}")
                    && item.optLong("size",0)>0 && item.optLong("size",0)<=MAX_APK)
                    return new Release(tag,url,digest.substring(7),json.optString("body",""));
            }
            return null;
        }finally{if(c!=null)c.disconnect();}
    }
    private static String readSmall(InputStream input,int limit)throws Exception{
        try(InputStream in=input;java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n,total=0;
            while((n=in.read(b))!=-1){
                total+=n;if(total>limit)throw new Exception("Réponse trop volumineuse");
                out.write(b,0,n);
            }
            return out.toString("UTF-8");
        }
    }
    private static void promptUpdate(Activity activity,Release release){
        String text="Nouvelle version "+release.version+" disponible. La mise à jour conserve tes signalements et photos. Android demandera ton accord avant l'installation.";
        if(!release.notes.trim().isEmpty())text+="\n\n"+release.notes.substring(0,Math.min(550,release.notes.length()));
        new AlertDialog.Builder(activity).setTitle("LES FAITS · Mise à jour")
            .setMessage(text)
            .setNegativeButton("Plus tard",null)
            .setPositiveButton("Télécharger", (d,w)->startDownload(activity,release)).show();
    }
    private static void startDownload(Activity activity,Release release){
        if(Build.VERSION.SDK_INT>=26 && !activity.getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(activity)
                .setTitle("Autorisation d'installation Android")
                .setMessage("Autorise LES FAITS à installer les APK depuis cette source, puis relance la mise à jour. Cela reste soumis à ton approbation.")
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Ouvrir les réglages",(d,w)->{
                    try{
                        Intent i=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:"+activity.getPackageName()));
                        activity.startActivity(i);
                    }catch(Exception e){Toast.makeText(activity,"Réglage indisponible.",Toast.LENGTH_LONG).show();}
                }).show();
            return;
        }
        final android.app.ProgressDialog progress=new android.app.ProgressDialog(activity);
        progress.setTitle("Téléchargement de la mise à jour");
        progress.setMessage("Vérification de l'APK signé…");
        progress.setIndeterminate(true);progress.setCancelable(false);progress.show();
        pool.execute(()->{
            File dest=new File(activity.getCacheDir(),"lesfaits-update.apk");
            try{
                download(release,dest);
                verifyApk(activity,dest);
                activity.runOnUiThread(()->{
                    progress.dismiss();
                    if(!activity.isFinishing())install(activity,dest);
                });
            }catch(Exception e){
                dest.delete();
                Log.w(TAG,"Update failed",e);
                String message=e.getMessage()==null?"Erreur inconnue":e.getMessage();
                activity.runOnUiThread(()->{
                    progress.dismiss();
                    if(!activity.isFinishing())new AlertDialog.Builder(activity)
                        .setTitle("Mise à jour non installée").setMessage(message)
                        .setPositiveButton("OK",null).show();
                });
            }
        });
    }
    private static void download(Release release,File dest)throws Exception{
        URL url=new URL(release.url);
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)url.openConnection();
            c.setConnectTimeout(15000);c.setReadTimeout(25000);
            c.setRequestProperty("User-Agent","LES-FAITS-SYNC30-Android");
            c.setInstanceFollowRedirects(true);
            if(c.getResponseCode()!=200)throw new Exception("Téléchargement indisponible ("+c.getResponseCode()+").");
            if(!"https".equalsIgnoreCase(c.getURL().getProtocol()))throw new Exception("Connexion non sécurisée.");
            long contentLength=c.getContentLengthLong();
            if(contentLength>MAX_APK)throw new Exception("APK trop volumineux.");
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(dest)){
                byte[] buffer=new byte[8192];int n;long size=0;
                while((n=in.read(buffer))!=-1){
                    size+=n;if(size>MAX_APK)throw new Exception("APK trop volumineux.");
                    sha.update(buffer,0,n);out.write(buffer,0,n);
                }
                if(size<1024)throw new Exception("APK incomplet.");
            }
            byte[] digest=sha.digest();StringBuilder hex=new StringBuilder();
            for(byte b:digest)hex.append(String.format(Locale.ROOT,"%02x",b&255));
            if(!hex.toString().equalsIgnoreCase(release.digest))throw new Exception("Empreinte SHA-256 incorrecte : téléchargement rejeté.");
        }finally{if(c!=null)c.disconnect();}
    }
    private static byte[][] certs(PackageInfo info){
        if(Build.VERSION.SDK_INT>=28){
            if(info.signingInfo==null)return new byte[0][];
            android.content.pm.Signature[] signs=info.signingInfo.hasMultipleSigners()
                ?info.signingInfo.getApkContentsSigners():info.signingInfo.getSigningCertificateHistory();
            byte[][] certs=new byte[signs.length][];
            for(int i=0;i<signs.length;i++)certs[i]=signs[i].toByteArray();
            return certs;
        }
        if(info.signatures==null)return new byte[0][];
        byte[][] certs=new byte[info.signatures.length][];
        for(int i=0;i<info.signatures.length;i++)certs[i]=info.signatures[i].toByteArray();
        return certs;
    }
    private static boolean commonSigner(byte[][] a,byte[][] b){
        for(byte[] x:a)for(byte[] y:b)if(MessageDigest.isEqual(x,y))return true;
        return false;
    }
    private static void verifyApk(Activity activity,File apk)throws Exception{
        PackageManager pm=activity.getPackageManager();
        int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo archive=pm.getPackageArchiveInfo(apk.getAbsolutePath(),flags);
        if(archive==null||!activity.getPackageName().equals(archive.packageName))
            throw new Exception("Le fichier téléchargé ne correspond pas à LES FAITS.");
        PackageInfo installed=pm.getPackageInfo(activity.getPackageName(),flags);
        long newVersion=Build.VERSION.SDK_INT>=28?archive.getLongVersionCode():archive.versionCode;
        long oldVersion=Build.VERSION.SDK_INT>=28?installed.getLongVersionCode():installed.versionCode;
        if(newVersion<=oldVersion)throw new Exception("L'APK ne contient pas une version plus récente.");
        if(!commonSigner(certs(archive),certs(installed)))
            throw new Exception("Signature différente. Une installation sans perte des données n'est pas possible : aucune désinstallation automatique ne sera lancée.");
    }
    private static void install(Activity activity,File apk){
        PackageInstaller installer=activity.getPackageManager().getPackageInstaller();
        PackageInstaller.Session session=null;
        int sessionId=-1;
        try{
            PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(activity.getPackageName());
            if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
            sessionId=installer.createSession(params);
            session=installer.openSession(sessionId);
            try(InputStream in=new java.io.FileInputStream(apk);
                OutputStream out=session.openWrite("base.apk",0,apk.length())){
                byte[] buf=new byte[8192];int n;
                while((n=in.read(buf))!=-1)out.write(buf,0,n);
                session.fsync(out);
            }
            Intent result=new Intent(activity,UpdateReceiver.class).setAction("fr.sync30.lesfaits.UPDATE_STATUS");
            PendingIntent pending=PendingIntent.getBroadcast(activity,sessionId,result,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
            session.commit(pending.getIntentSender());
            Toast.makeText(activity,"Valide l'installation proposée par Android.",Toast.LENGTH_LONG).show();
        }catch(Exception e){
            if(sessionId!=-1)try{installer.abandonSession(sessionId);}catch(Exception ignored){}
            new AlertDialog.Builder(activity).setTitle("Installation impossible")
                .setMessage(e.getMessage()).setPositiveButton("OK",null).show();
        }finally{if(session!=null)session.close();}
    }
}
