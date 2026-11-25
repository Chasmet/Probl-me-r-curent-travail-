package fr.sync30.lesfaits;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.view.View;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** A session belongs to the visible report editor. Only named fields accept input. */
final class ReportControls {
    private static ReportControls active;
    final View editor;
    final long reportId;
    final String session = java.util.UUID.randomUUID().toString();
    final LinkedHashMap<String,EditText> fields = new LinkedHashMap<>();

    ReportControls(View editor, long id, EditText title, EditText description, EditText location) {
        this.editor=editor;
        reportId=id;
        fields.put("titre", title);
        fields.put("description", description);
        fields.put("lieu", location);
    }

    void enable(boolean enabled) { if (enabled) active=this; else if (active==this) active=null; }
    static void leave() { active=null; }

    static JSONObject run(String action, JSONObject args) throws Exception {
        FutureTask<JSONObject> task = new FutureTask<>(() -> {
            ReportControls controls=active;
            if (controls==null || !controls.editor.hasWindowFocus() || !controls.editor.isShown())
                throw new IllegalStateException("Ouvre le rapport et active l’assistance dans son formulaire.");
            return controls.apply(action,args);
        });
        new Handler(Looper.getMainLooper()).post(task);
        try { return task.get(5, TimeUnit.SECONDS); }
        catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalArgumentException(e.getCause().getMessage());
        } finally { task.cancel(false); }
    }

    private JSONObject apply(String action, JSONObject args) throws Exception {
        JSONObject result=new JSONObject().put("ok",true).put("rapport_id",reportId).put("session",session);
        if ("lesfaits_vision".equals(action)) {
            int width=editor.getWidth(), height=editor.getHeight();
            float scale=Math.min(1f,1000f/Math.max(width,height));
            Bitmap image=Bitmap.createBitmap(Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale)),Bitmap.Config.ARGB_8888);
            try {
                Canvas canvas=new Canvas(image);
                canvas.scale(scale,scale);
                editor.draw(canvas);
                EvidenceReader.putImage(result,image);
            } finally { image.recycle(); }
            result.put("champs",new org.json.JSONArray(fields.keySet()));
        } else {
            if (args==null || !session.equals(args.optString("session"))) throw new IllegalArgumentException("Session expirée. Utilise lesfaits_vision.");
            EditText field=fields.get(args.optString("champ"));
            if (field==null) throw new IllegalArgumentException("Champ attendu : titre, description ou lieu.");
            if ("lesfaits_clavier".equals(action)) {
                String value=args.getString("texte");
                if (value.length()>25000) throw new IllegalArgumentException("Texte trop long");
                field.setText(value);
                field.setSelection(value.length());
            }
            field.requestFocus();
            field.requestRectangleOnScreen(new android.graphics.Rect(0,0,field.getWidth(),Math.min(100,field.getHeight())));
            result.put("champ",args.getString("champ"));
        }
        return result;
    }
}
