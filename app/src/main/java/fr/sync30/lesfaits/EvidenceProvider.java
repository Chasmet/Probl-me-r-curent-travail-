package fr.sync30.lesfaits;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Read-only content URIs for opening a chosen evidence file in an external viewer. */
public final class EvidenceProvider extends ContentProvider {
    private Store.Evidence find(Uri uri)throws FileNotFoundException{
        try{
            if(uri==null||uri.getPathSegments().size()!=1)throw new IllegalArgumentException();
            long id=Long.parseLong(uri.getLastPathSegment());
            try(Store store=new Store(getContext())){
                Store.Evidence f=store.file(id);
                if(f!=null)return f;
            }
        }catch(Exception ignored){}
        throw new FileNotFoundException("Fichier introuvable");
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){
        try{return find(uri).mime;}catch(Exception e){return "application/octet-stream";}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode))throw new FileNotFoundException("Lecture seule");
        Store.Evidence evidence=find(uri);
        try{
            File file=FileVault.safeFile(getContext(),evidence.path);
            if(!file.isFile())throw new FileNotFoundException("Pièce jointe absente");
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
        }catch(IOException e){throw new FileNotFoundException(e.getMessage());}
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){
        Store.Evidence e;
        try{e=find(uri);}catch(Exception ex){return null;}
        String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor result=new MatrixCursor(columns);
        Object[] row=new Object[columns.length];
        for(int i=0;i<columns.length;i++){
            if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]=e.name;
            else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=e.size;
        }
        result.addRow(row);
        return result;
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Lecture seule");}
    @Override public int delete(Uri uri,String selection,String[] selectionArgs){throw new UnsupportedOperationException("Lecture seule");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] selectionArgs){throw new UnsupportedOperationException("Lecture seule");}
}