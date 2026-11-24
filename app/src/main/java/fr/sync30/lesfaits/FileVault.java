package fr.sync30.lesfaits;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.UUID;

/** Local, private evidence library. No remote upload or automatic sharing. */
final class FileVault {
    static final long MAX_FILE=250L*1024*1024;
    private FileVault(){}

    static File directory(Context context)throws IOException{
        File dir=new File(context.getFilesDir(),"evidence");
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("Stockage privé indisponible");
        return dir.getCanonicalFile();
    }
    static File safeFile(Context context,String path)throws IOException{
        File dir=directory(context);
        File f=new File(path).getCanonicalFile();
        if(!dir.equals(f.getParentFile()))throw new IOException("Chemin de fichier non autorisé");
        return f;
    }
    static String allowedMime(String mime,String name)throws IOException{
        String m=(mime==null?"":mime).toLowerCase(Locale.ROOT).trim();
        String n=(name==null?"":name).toLowerCase(Locale.ROOT);
        if("application/octet-stream".equals(m)||m.isEmpty()){
            if(n.endsWith(".pdf"))m="application/pdf";
            else if(n.endsWith(".txt"))m="text/plain";
            else if(n.endsWith(".docx"))m="application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            else if(n.endsWith(".xlsx"))m="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            else if(n.endsWith(".mp4"))m="video/mp4";
            else if(n.endsWith(".jpg")||n.endsWith(".jpeg"))m="image/jpeg";
            else if(n.endsWith(".png"))m="image/png";
        }
        if(m.startsWith("image/")||m.startsWith("video/")||m.startsWith("audio/")
            ||m.equals("application/pdf")||m.equals("text/plain")
            ||m.equals("application/msword")||m.equals("application/vnd.ms-excel")
            ||m.equals("application/vnd.ms-powerpoint")
            ||m.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            ||m.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            ||m.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")
            ||m.equals("application/rtf")||m.equals("text/csv"))
            return m;
        throw new IOException("Type refusé : photos, vidéos, audio, PDF et documents uniquement");
    }
    static String displayName(String input){
        String clean=input==null?"":input.replaceAll("[\\\\/\\p{Cntrl}]", "_").trim();
        if(clean.isEmpty())clean="Pièce_justificative";
        return clean.length()>160?clean.substring(0,160):clean;
    }
    static long importUri(Context context,Store store,Uri uri)throws Exception{
        if(uri==null||!"content".equals(uri.getScheme()))throw new IOException("Source de fichier non autorisée");
        ContentResolver resolver=context.getContentResolver();
        String name="Pièce_justificative";long announced=-1;
        try(Cursor c=resolver.query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
            if(c!=null&&c.moveToFirst()){
                int nameIndex=c.getColumnIndex(OpenableColumns.DISPLAY_NAME),sizeIndex=c.getColumnIndex(OpenableColumns.SIZE);
                if(nameIndex>=0&&!c.isNull(nameIndex))name=c.getString(nameIndex);
                if(sizeIndex>=0&&!c.isNull(sizeIndex))announced=c.getLong(sizeIndex);
            }
        }catch(SecurityException e){throw new IOException("Accès refusé au fichier sélectionné",e);}
        name=displayName(name);
        String mime=allowedMime(resolver.getType(uri),name);
        if(announced>MAX_FILE)throw new IOException(name+" : 250 Mo maximum par fichier");
        File dir=directory(context);
        String id=UUID.randomUUID().toString();
        File temp=new File(dir,id+".part"),dest=new File(dir,id+".bin");
        long size=0;boolean finished=false;
        try{
            try(InputStream in=resolver.openInputStream(uri);OutputStream out=new FileOutputStream(temp)){
                if(in==null)throw new IOException("Impossible de lire le fichier "+name);
                byte[] buffer=new byte[65536];int n;
                while((n=in.read(buffer))!=-1){
                    size+=n;
                    if(size>MAX_FILE)throw new IOException(name+" : 250 Mo maximum par fichier");
                    out.write(buffer,0,n);
                }
            }
            if(size==0)throw new IOException("Le fichier est vide");
            if(!temp.renameTo(dest))throw new IOException("Impossible de finaliser la copie");
            long fileId=store.addEvidence(name,mime,dest.getCanonicalPath(),size);
            finished=true;
            return fileId;
        }finally{
            if(!finished){temp.delete();dest.delete();}
        }
    }
    static String sizeLabel(long bytes){
        if(bytes>=1024L*1024L)return String.format(Locale.FRANCE,"%.1f Mo",bytes/(1024.0*1024.0));
        if(bytes>=1024L)return String.format(Locale.FRANCE,"%.0f Ko",bytes/1024.0);
        return bytes+" o";
    }
    static String kind(String mime){
        return mime.startsWith("image/")?"Photo":mime.startsWith("video/")?"Vidéo":
            mime.startsWith("audio/")?"Audio":mime.equals("application/pdf")?"PDF":"Document";
    }
}