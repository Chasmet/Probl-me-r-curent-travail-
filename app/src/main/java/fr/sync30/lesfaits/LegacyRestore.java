package fr.sync30.lesfaits;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Restore an explicitly selected local v0.7.1 archive into a new, empty installation. */
final class LegacyRestore {
    private static final long LIMIT=2L*1024*1024*1024;
    private static final String[] INCIDENTS={"id","space","category","title","description","location","occurred","created","severity","recurring","status"};
    private static final String[] NOTES={"id","incident_id","at","text"};
    private static final String[] REQUESTS={"request_id","incident_id"};
    private LegacyRestore(){}
    static int restore(Context context, Store store, Uri uri)throws Exception {
        File staging=new File(context.getCacheDir(),"restore-"+UUID.randomUUID());
        if(!staging.mkdir())throw new IOException("Stockage temporaire inaccessible.");
        ArrayList<File> created=new ArrayList<>();
        boolean committed=false;
        try {
            HashSet<String> entries=new HashSet<>();
            long total=0;
            try(InputStream raw=context.getContentResolver().openInputStream(uri)){
                if(raw==null)throw new IOException("Archive inaccessible.");
                try(ZipInputStream in=new ZipInputStream(raw)){
                    ZipEntry e;byte[] buffer=new byte[65536];
                    while((e=in.getNextEntry())!=null){
                        String n=e.getName();
                        if(e.isDirectory())continue;
                        if(!n.equals("lesfaits.db")&&!n.matches("photos/[A-Za-z0-9_-]{1,100}\\.(img|jpg|jpeg|png)"))
                            throw new IOException("Entrée inconnue dans la sauvegarde.");
                        if(!entries.add(n)||entries.size()>20000)throw new IOException("Archive invalide.");
                        File f=new File(staging,n);
                        if(!f.getParentFile().isDirectory()&&!f.getParentFile().mkdirs())throw new IOException("Stockage indisponible.");
                        long size=0, cap=n.equals("lesfaits.db")?128L*1024*1024:16L*1024*1024;
                        try(FileOutputStream out=new FileOutputStream(f)){
                            int count;while((count=in.read(buffer))!=-1){
                                total+=count;size+=count;
                                if(total>LIMIT||size>cap)throw new IOException("Sauvegarde trop volumineuse.");
                                out.write(buffer,0,count);
                            }
                        }
                        if(size==0)throw new IOException("Fichier vide dans la sauvegarde.");
                        in.closeEntry();
                    }
                }
            }
            if(!entries.contains("lesfaits.db"))throw new IOException("Base de données absente.");
            File oldDb=new File(staging,"lesfaits.db");
            SQLiteDatabase dest=store.getWritableDatabase();
            try(SQLiteDatabase source=SQLiteDatabase.openDatabase(oldDb.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){
                if(source.getVersion()!=2&&source.getVersion()!=1)throw new IOException("Cette restauration accepte les anciennes bases v1/v2 (LES FAITS 0.7.1).");
                try(Cursor c=source.rawQuery("PRAGMA quick_check",null)){
                    if(!c.moveToFirst()||!"ok".equals(c.getString(0)))throw new IOException("Base de données endommagée.");
                }
                dest.beginTransaction();
                try {
                    for(String t:new String[]{"incidents","notes","photos","mcp_requests","evidence_files","evidence_links"}){
                        try(Cursor c=dest.rawQuery("SELECT COUNT(*) FROM "+t,null)){
                            if(!c.moveToFirst()||c.getLong(0)!=0)throw new IOException("Restauration réservée à une installation vide. Aucune donnée existante ne sera remplacée.");
                        }
                    }
                    int count=copyRows(source,dest,"incidents",INCIDENTS);
                    copyRows(source,dest,"notes",NOTES);
                    if(source.getVersion()>=2)copyRows(source,dest,"mcp_requests",REQUESTS);
                    File dir=new File(context.getFilesDir(),"photos");
                    if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Dossier photos indisponible.");
                    try(Cursor c=source.query("photos",new String[]{"id","incident_id","path"},null,null,null,null,null)){
                        while(c.moveToNext()){
                            String name=new File(c.getString(2)).getName();
                            if(!entries.contains("photos/"+name))throw new IOException("Une photo liée est absente de la sauvegarde.");
                            File from=new File(staging,"photos/"+name);
                            File to=new File(dir,UUID.randomUUID()+".img");
                            created.add(to);
                            try(InputStream in=new FileInputStream(from);FileOutputStream out=new FileOutputStream(to)){
                                byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
                                out.getFD().sync();
                            }
                            ContentValues v=new ContentValues();v.put("id",c.getLong(0));v.put("incident_id",c.getLong(1));v.put("path",to.getAbsolutePath());
                            dest.insertOrThrow("photos",null,v);
                        }
                    }
                    dest.setTransactionSuccessful();
                    dest.endTransaction();
                    committed=true;
                    return count;
                }finally{if(dest.inTransaction())dest.endTransaction();}
            }
        }finally{
            if(!committed)for(File f:created)f.delete();
            clean(staging);
        }
    }
    private static int copyRows(SQLiteDatabase source,SQLiteDatabase dest,String table,String[] columns){
        int count=0;
        try(Cursor c=source.query(table,columns,null,null,null,null,null)){
            while(c.moveToNext()){
                ContentValues v=new ContentValues();
                for(int i=0;i<columns.length;i++){
                    if(c.isNull(i))v.putNull(columns[i]);
                    else if(c.getType(i)==Cursor.FIELD_TYPE_INTEGER)v.put(columns[i],c.getLong(i));
                    else v.put(columns[i],c.getString(i));
                }
                dest.insertOrThrow(table,null,v);count++;
            }
        }
        return count;
    }
    private static void clean(File f){
        File[] children=f.listFiles();if(children!=null)for(File child:children)clean(child);
        f.delete();
    }
}
