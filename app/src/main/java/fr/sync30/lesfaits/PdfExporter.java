package fr.sync30.lesfaits;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class PdfExporter {
    private final PdfDocument doc=new PdfDocument();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Canvas canvas;
    private int pageNum=0;
    private float y=0f;
    private final Store store;
    private final SimpleDateFormat df=new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.FRANCE);
    private PdfDocument.Page page;
    private PdfExporter(Store store){this.store=store;}
    static void write(OutputStream out,List<Store.Incident> incidents,Store store)throws IOException {
        PdfExporter p=new PdfExporter(store);
        try {
            p.start();
            p.line("LES FAITS  /  SYNC 30",20,true,0xFF0C3479);
            p.line("Rapport factuel - export personnel",10,false,Color.DKGRAY);
            p.rule();
            for(Store.Incident item:incidents)p.incident(item);
            p.line("Fin du rapport  •  "+incidents.size()+" signalement(s)",11,true,0xFF0C3479);
            p.finish();
            p.doc.writeTo(out);
        }finally{p.doc.close();}
    }
    private void start(){
        pageNum++;
        PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,pageNum).create();
        page=doc.startPage(info); canvas=page.getCanvas();y=54;
        paint.setColor(0xFFF6F9FF);canvas.drawColor(Color.WHITE);
        paint.setTextSize(9);paint.setTypeface(Typeface.DEFAULT);
        paint.setColor(0xFF65768B);canvas.drawText("LES FAITS • SYNC 30",42,25,paint);
    }
    private void finish() {
        if(page!=null){
            paint.setColor(0xFF65768B);paint.setTextSize(9);paint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("Page "+pageNum,490,815,paint);
            doc.finishPage(page);page=null;
        }
    }
    private void room(float required){if(y+required>785){finish();start();}}
    private void line(String text,int size,boolean bold,int color) {
        paint.setColor(color);paint.setTextSize(size);
        paint.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));
        float limit=508f;
        for(String para:(text==null?"":text).split("\n",-1)){
            String[] words=para.split(" ");
            StringBuilder current=new StringBuilder();
            if(words.length==0){room(size+5);y+=size+5;continue;}
            for(String word:words) {
                String test=current.length()==0?word:current+" "+word;
                if(paint.measureText(test)>limit && current.length()>0){
                    room(size+6);canvas.drawText(current.toString(),42,y,paint);y+=size+6;current=new StringBuilder(word);
                } else current=new StringBuilder(test);
                while(paint.measureText(current.toString())>limit && current.length()>1){
                    int len=current.length()-1;
                    String part=current.substring(0,len);
                    room(size+6);canvas.drawText(part,42,y,paint);y+=size+6;
                    current=new StringBuilder(current.substring(len));
                }
            }
            room(size+6);canvas.drawText(current.toString(),42,y,paint);y+=size+6;
        }
    }
    private void rule() {
        room(16);paint.setColor(0xFFDFE7F2);paint.setStrokeWidth(1);
        canvas.drawLine(42,y,552,y,paint);y+=18;
    }
    private void incident(Store.Incident i) {
        room(105);
        line("#"+i.id+"  "+i.title,15,true,0xFF0C3479);
        line("Espace : "+("TERRAIN".equals(i.space)?"TRACE - Personnel / Terrain":"RAPPORT - Affaires personnelles"),10,false,0xFF263B58);
        line("Date des faits : "+df.format(new Date(i.occurred)),10,false,Color.DKGRAY);
        line("Catégorie : "+i.category+"  •  Urgence : "+i.severity,10,false,Color.DKGRAY);
        line("Statut : "+i.status+"  •  Récurrent : "+(i.recurring?"Oui":"Non"),10,false,Color.DKGRAY);
        if(!i.location.trim().isEmpty())line("Lieu : "+i.location,10,false,Color.DKGRAY);
        line("Description :",11,true,0xFF0C3479);
        line(i.description,11,false,0xFF222222);
        List<Store.Note> notes=store.notes(i.id);
        if(!notes.isEmpty()){
            line("Suivi / observations :",11,true,0xFF0C3479);
            for(Store.Note n:notes)line(df.format(new Date(n.at))+" : "+n.text,10,false,0xFF333333);
        }
        int shown=0;
        for(String path:store.photos(i.id)) {
            if(shown++>=2)break;
            try {
                BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=4;
                Bitmap bitmap=BitmapFactory.decodeFile(path,opts);
                if(bitmap!=null){
                    float iw=bitmap.getWidth(),ih=bitmap.getHeight();
                    float w=Math.min(300f,iw),h=w*ih/iw;
                    if(h>210f){h=210f;w=h*iw/ih;}
                    room(h+20f);
                    canvas.drawBitmap(bitmap,null,new android.graphics.RectF(42,y,42+w,y+h),paint);
                    y+=h+12;bitmap.recycle();
                }
            }catch(Exception ignored){}
        }
        rule();
    }
}
