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
        if (!m.isEmpty() && !m.equals("application/octet-stream") && m.matches("[a-z0-9.+_-]+/[a-z0-9.+_-]+")) return m;
        int dot=name.lastIndexOf('.');
        String ext=dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);
        String detected=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return detected==null?"application/octet-stream":detected;
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