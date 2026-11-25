package fr.sync30.lesfaits;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** Independent local notebook. Never migrates or overwrites the reports database. */
final class PersonalNotes extends SQLiteOpenHelper {
    static final class Entry {
        final long id,updated;
        final String title,content;
        final boolean archived;
        Entry(long i,String t,String c,long u,boolean a){id=i;title=t;content=c;updated=u;archived=a;}
    }
    PersonalNotes(Context context){super(context.getApplicationContext(),"lesfaits_personal_notes.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE personal_notes (id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,content TEXT NOT NULL,updated INTEGER NOT NULL,archived INTEGER NOT NULL DEFAULT 0)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
    List<Entry> list(boolean archived){
        List<Entry> entries=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("personal_notes",new String[]{"id","title","content","updated","archived"},"archived=?",new String[]{archived?"1":"0"},null,null,"updated DESC,id DESC")){
            while(c.moveToNext())entries.add(new Entry(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),c.getInt(4)!=0));
        }
        return entries;
    }
    void save(long id,String title,String content){
        ContentValues v=new ContentValues();v.put("title",title);v.put("content",content);v.put("updated",System.currentTimeMillis());
        SQLiteDatabase db=getWritableDatabase();
        if(id==0)db.insertOrThrow("personal_notes",null,v);
        else if(db.update("personal_notes",v,"id=?",new String[]{Long.toString(id)})!=1)throw new IllegalStateException("Note introuvable");
    }
    void archive(long id,boolean archived){
        ContentValues v=new ContentValues();v.put("archived",archived?1:0);v.put("updated",System.currentTimeMillis());
        if(getWritableDatabase().update("personal_notes",v,"id=?",new String[]{Long.toString(id)})!=1)throw new IllegalStateException("Note introuvable");
    }
}
