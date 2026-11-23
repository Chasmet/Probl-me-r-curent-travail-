package fr.sync30.lesfaits;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class Store extends SQLiteOpenHelper {
    public static final class Incident {
        public long id, occurred, created;
        public String space, category, title, description, location, severity, status;
        public boolean recurring;
        public Incident() {
            space = "TERRAIN"; category = "Autre"; title = ""; description = "";
            location = ""; severity = "Normale"; status = "Signalé";
            occurred = System.currentTimeMillis(); created = occurred;
        }
    }
    public static final class Note {
        public final long at;
        public final String text;
        Note(long at, String text) { this.at=at; this.text=text; }
    }
    private final Context ctx;
    public Store(Context context) { super(context, "lesfaits.db", null, 2); ctx=context.getApplicationContext(); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE incidents (id INTEGER PRIMARY KEY AUTOINCREMENT, space TEXT NOT NULL, category TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, location TEXT NOT NULL, occurred INTEGER NOT NULL, created INTEGER NOT NULL, severity TEXT NOT NULL, recurring INTEGER NOT NULL, status TEXT NOT NULL)");
        db.execSQL("CREATE TABLE photos (id INTEGER PRIMARY KEY AUTOINCREMENT, incident_id INTEGER NOT NULL, path TEXT NOT NULL)");
        db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT, incident_id INTEGER NOT NULL, at INTEGER NOT NULL, text TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_incidents_occurred ON incidents(occurred)");
        db.execSQL("CREATE INDEX idx_photos_incident ON photos(incident_id)");
        db.execSQL("CREATE INDEX idx_notes_incident ON notes(incident_id)");
        db.execSQL("CREATE TABLE mcp_requests (request_id TEXT PRIMARY KEY NOT NULL, incident_id INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion<2) db.execSQL("CREATE TABLE IF NOT EXISTS mcp_requests (request_id TEXT PRIMARY KEY NOT NULL, incident_id INTEGER NOT NULL)");
    }
    private ContentValues values(Incident v) {
        ContentValues c=new ContentValues();
        c.put("space",v.space); c.put("category",v.category); c.put("title",v.title);
        c.put("description",v.description); c.put("location",v.location);
        c.put("occurred",v.occurred); c.put("created",v.created); c.put("severity",v.severity);
        c.put("recurring",v.recurring?1:0); c.put("status",v.status); return c;
    }
    public long insert(Incident incident) { return getWritableDatabase().insertOrThrow("incidents",null,values(incident)); }
    /** Une tâche MCP rejouée ne doit jamais créer de doublon. */
    public long insertMcp(Incident incident,String requestId) {
        if(requestId==null||!requestId.matches("[A-Za-z0-9_-]{8,100}"))
            throw new IllegalArgumentException("Identifiant de tâche MCP invalide");
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            try(Cursor c=db.rawQuery("SELECT incident_id FROM mcp_requests WHERE request_id=?",new String[]{requestId})) {
                if(c.moveToFirst()) {
                    long id=c.getLong(0);db.setTransactionSuccessful();return id;
                }
            }
            long id=db.insertOrThrow("incidents",null,values(incident));
            ContentValues v=new ContentValues();v.put("request_id",requestId);v.put("incident_id",id);
            db.insertOrThrow("mcp_requests",null,v);
            db.setTransactionSuccessful();return id;
        } finally {db.endTransaction();}
    }
    private Incident from(Cursor c) {
        Incident a=new Incident();
        a.id=c.getLong(c.getColumnIndexOrThrow("id"));
        a.space=c.getString(c.getColumnIndexOrThrow("space"));
        a.category=c.getString(c.getColumnIndexOrThrow("category"));
        a.title=c.getString(c.getColumnIndexOrThrow("title"));
        a.description=c.getString(c.getColumnIndexOrThrow("description"));
        a.location=c.getString(c.getColumnIndexOrThrow("location"));
        a.occurred=c.getLong(c.getColumnIndexOrThrow("occurred"));
        a.created=c.getLong(c.getColumnIndexOrThrow("created"));
        a.severity=c.getString(c.getColumnIndexOrThrow("severity"));
        a.recurring=c.getInt(c.getColumnIndexOrThrow("recurring"))!=0;
        a.status=c.getString(c.getColumnIndexOrThrow("status"));
        return a;
    }
    public List<Incident> all() {
        List<Incident> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM incidents ORDER BY occurred DESC,id DESC",null)) {
            while(c.moveToNext()) list.add(from(c));
        }
        return list;
    }
    public Incident get(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM incidents WHERE id=?",new String[]{String.valueOf(id)})){
            if(c.moveToNext())return from(c);
        }
        return null;
    }
    public void status(long id,String status) {
        ContentValues v=new ContentValues(); v.put("status",status);
        getWritableDatabase().update("incidents",v,"id=?",new String[]{String.valueOf(id)});
    }
    public void addPhoto(long id,String path) {
        ContentValues v=new ContentValues(); v.put("incident_id",id); v.put("path",path);
        getWritableDatabase().insert("photos",null,v);
    }
    public List<String> photos(long id) {
        List<String> a=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT path FROM photos WHERE incident_id=? ORDER BY id",new String[]{String.valueOf(id)})){
            while(c.moveToNext()) a.add(c.getString(0));
        }
        return a;
    }
    public void addNote(long id,String text) {
        ContentValues v=new ContentValues(); v.put("incident_id",id);
        v.put("at",System.currentTimeMillis()); v.put("text",text);
        getWritableDatabase().insert("notes",null,v);
    }
    public List<Note> notes(long id) {
        List<Note> a=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT at,text FROM notes WHERE incident_id=? ORDER BY at",new String[]{String.valueOf(id)})){
            while(c.moveToNext()) a.add(new Note(c.getLong(0),c.getString(1)));
        }
        return a;
    }
    public void delete(long id) {
        for(String p:photos(id)) new File(p).delete();
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            String[] a={String.valueOf(id)};
            db.delete("notes","incident_id=?",a);
            db.delete("photos","incident_id=?",a);
            db.delete("incidents","id=?",a);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
