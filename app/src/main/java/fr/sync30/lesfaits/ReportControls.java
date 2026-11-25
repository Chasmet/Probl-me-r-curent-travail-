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
            result.put("largeur",Math.max(1,Math.round(width*scale)));
            result.put("hauteur",Math.max(1,Math.round(height*scale)));
            result.put("champs",new org.json.JSONArray(fields.keySet()));
        } else {
            if (args==null || !session.equals(args.optString("session"))) throw new IllegalArgumentException("Session expirée. Utilise lesfaits_vision.");
            if ("lesfaits_souris".equals(action) && args.has("defiler")) {
                String direction=args.getString("defiler");
                if (!direction.equals("haut") && !direction.equals("bas")) throw new IllegalArgumentException("Sens attendu : haut ou bas");
                View parent=fields.get("titre");
                while (!(parent instanceof android.widget.ScrollView) && parent.getParent() instanceof View)
                    parent=(View)parent.getParent();
                if (!(parent instanceof android.widget.ScrollView)) throw new IllegalStateException("Défilement indisponible");
                android.widget.ScrollView scroll=(android.widget.ScrollView)parent;
                scroll.scrollBy(0,(direction.equals("haut")?-1:1)*scroll.getHeight()*2/3);
                return result.put("defilement",direction);
            }
            String name=args.optString("champ");
            if ("lesfaits_souris".equals(action) && (args.has("x") || args.has("y"))) {
                float scale=Math.min(1f,1000f/Math.max(editor.getWidth(),editor.getHeight()));
                int[] origin=new int[2];editor.getLocationOnScreen(origin);
                int x=origin[0]+Math.round((float)args.getDouble("x")/scale);
                int y=origin[1]+Math.round((float)args.getDouble("y")/scale);
                for (java.util.Map.Entry<String,EditText> entry:fields.entrySet()){
                    EditText candidate=entry.getValue();
                    int[] location=new int[2];candidate.getLocationOnScreen(location);
                    android.graphics.Rect visible=new android.graphics.Rect();
                    if(candidate.getLocalVisibleRect(visible) && visible.contains(x-location[0],y-location[1])){
                        candidate.requestFocus();
                        int pos=candidate.getOffsetForPosition(x-location[0],y-location[1]);
                        if(pos>=0)candidate.setSelection(pos);
                        return result.put("champ",entry.getKey()).put("curseur",pos);
                    }
                }
                throw new IllegalArgumentException("Aucun champ visible à ces coordonnées. Reprends une capture.");
            }
            EditText field=fields.get(name);
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
