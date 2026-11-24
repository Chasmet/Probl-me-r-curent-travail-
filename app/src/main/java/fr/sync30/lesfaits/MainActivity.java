package fr.sync30.lesfaits;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.ClipData;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BLUE=0xFF0A57C8, NAVY=0xFF092B62, GREEN=0xFF169D54;
    private static final int BG=0xFFF4F7FC, INK=0xFF173154, MUTED=0xFF63758D;
    private static final int PICK_PHOTO=101, CAMERA=102, DICTATE=103, SAVE_PDF=104, PICK_EVIDENCE=105;
    private static final String[] SPACES={"TRACE — Personnel / Terrain","RAPPORT — Affaires personnelles"};
    private static final String[] CATEGORIES={"Matériel défectueux","Sécurité / Risque","Conditions de travail","Organisation / Service","Accident / Quasi-accident","Situation personnelle","Démarche administrative","Autre"};
    private static final String[] SEVERITIES={"Normale","Élevée","Critique"};
    private static final String[] STATUSES={"Signalé","En cours","Résolu","Classé"};
    private final SimpleDateFormat datetime=new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.FRANCE);
    private Store store;
    private SecurePrefs prefs;
    private LinearLayout root, body;
    private final List<String> newPhotos=new ArrayList<>();
    private TextView photoCounter;
    private EditText dictationTarget;
    private List<Store.Incident> pendingPdf;
    private long formTime=System.currentTimeMillis();
    private long pendingEvidenceIncident=-1L;
    private static final ExecutorService fileIo=Executors.newSingleThreadExecutor();
    private long lastForegroundPoll=0;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);store=new Store(this);prefs=new SecurePrefs(this);
        getWindow().setStatusBarColor(NAVY);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        home();
        acceptOAuthCallback(getIntent());
        SkillMajAuto.onAppOpen(this);
        SkillMajAuto.onNotificationTap(this,getIntent());
        acceptPairInvite(getIntent());
        if(McpWorkerClient.isEnabled(prefs))McpWorkerClient.check(getApplicationContext(),null);
    }
    /** Poll queued ChatGPT requests when the app returns to foreground, not only every 15 min. */
    @Override protected void onResume(){
        super.onResume();
        if(prefs!=null&&McpWorkerClient.isEnabled(prefs)){
            long elapsed=android.os.SystemClock.elapsedRealtime();
            if(elapsed-lastForegroundPoll>20000){
                lastForegroundPoll=elapsed;
                McpWorkerClient.check(getApplicationContext(),null);
            }
        }
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        acceptOAuthCallback(intent);
        acceptPairInvite(intent);
        SkillMajAuto.onNotificationTap(this,intent);
    }
    private void acceptOAuthCallback(Intent intent){
        Uri uri=intent==null?null:intent.getData();
        if(uri==null||!"fr.sync30.lesfaits".equals(uri.getScheme())||!"oauth".equals(uri.getHost()))return;
        OAuthClient.complete(this,prefs,uri,(ok,message)->{
            new AlertDialog.Builder(this).setTitle(ok?"Connexion établie":"Connexion refusée")
                .setMessage(message).setPositiveButton("OK",null).show();
            settings();
        });
    }
    private void acceptPairInvite(Intent intent){
        Uri uri=intent==null?null:intent.getData();
        if(uri==null||!"fr.sync30.lesfaits".equals(uri.getScheme())||!"pair".equals(uri.getHost()))return;
        String invite=uri.getQueryParameter("invite");
        intent.setData(null);
        if(invite==null||!invite.matches("chkinvite_[A-Za-z0-9_-]{20,150}")){
            toast("Invitation MCP invalide ou manquante.");return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Associer LES FAITS à mon MCP")
            .setMessage("Ton serveur CHK Universal pourra consulter les signalements et créer de nouveaux dossiers à ta demande via ChatGPT. Les écritures nécessitent une approbation du serveur. Les photos ne sont pas transmises. Autoriser ?")
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Associer",(d,w)->McpWorkerClient.pair(getApplicationContext(),invite,(ok,message)->
                new AlertDialog.Builder(this).setTitle(ok?"Association réussie":"Association impossible")
                    .setMessage(message).setPositiveButton("OK",(dialog,which)->settings()).show()))
            .show();
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable shape(int fill,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));return d;
    }
    private GradientDrawable border(int fill,int radius,int outline){
        GradientDrawable d=shape(fill,radius);d.setStroke(dp(1),outline);return d;
    }
    private TextView text(String label,float sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(label);t.setTextColor(color);t.setTextSize(sp);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        t.setIncludeFontPadding(true);return t;
    }
    private void gap(LinearLayout parent,int height){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    private LinearLayout vertical(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    private LinearLayout horizontal(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;
    }
    private LinearLayout card(LinearLayout parent){
        LinearLayout c=vertical();c.setPadding(dp(16),dp(16),dp(16),dp(16));
        c.setBackground(border(Color.WHITE,18,0xFFE4EAF3));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);
        parent.addView(c,p);return c;
    }
    private void heading(LinearLayout parent,String title){
        TextView t=text(title,18,INK,true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);
        p.topMargin=dp(7);parent.addView(t,p);
    }
    private TextView button(String title,int color,Runnable task){
        TextView b=text(title,15,Color.WHITE,true);b.setGravity(Gravity.CENTER);
        b.setPadding(dp(15),dp(13),dp(15),dp(13));
        b.setBackground(shape(color,13));b.setOnClickListener(v->task.run());
        return b;
    }
    private void addButton(LinearLayout parent,String title,int color,Runnable task){
        TextView b=button(title,color,task);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(7);p.bottomMargin=dp(5);parent.addView(b,p);
    }
    private void base(String screen,String selectedTab){
        root=vertical();root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            root.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        LinearLayout top=vertical();top.setPadding(dp(20),dp(15),dp(20),dp(16));
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{NAVY,BLUE}));
        LinearLayout row=horizontal();
        LinearLayout brand=vertical();
        TextView title=text("LES FAITS",25,Color.WHITE,true);
        TextView sign=text("▰  SYNC 30",11,0xFFBEDEFF,true);
        brand.addView(title);brand.addView(sign);
        row.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView settings=text("⚙",29,Color.WHITE,false);settings.setGravity(Gravity.CENTER);
        settings.setPadding(dp(14),dp(3),dp(5),dp(3));settings.setOnClickListener(v->settings());
        row.addView(settings);top.addView(row);
        if(!screen.isEmpty()){
            TextView sub=text(screen,13,0xFFEAF4FF,false);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);top.addView(sub,p);
        }
        root.addView(top,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        body=vertical();body.setPadding(dp(16),dp(18),dp(16),dp(30));
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=horizontal();nav.setBackgroundColor(Color.WHITE);
        nav.setPadding(dp(5),dp(8),dp(5),dp(7));
        navItem(nav,"⌂","Accueil","home".equals(selectedTab),this::home);
        navItem(nav,"▤","Historique","list".equals(selectedTab),()->history());
        navItem(nav,"▧","Fichiers","vault".equals(selectedTab),this::fileManager);
        navItem(nav,"▣","Rapports","report".equals(selectedTab),this::reports);
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(65)));
    }
    private void navItem(LinearLayout nav,String icon,String title,boolean active,Runnable action){
        LinearLayout wrapper=vertical();wrapper.setGravity(Gravity.CENTER);
        wrapper.setBackground(shape(active?0xFFE9F2FF:Color.WHITE,14));
        TextView symbol=text(icon,22,active?BLUE:MUTED,true);symbol.setGravity(Gravity.CENTER);
        TextView label=text(title,11,active?BLUE:MUTED,active);label.setGravity(Gravity.CENTER);
        wrapper.addView(symbol);wrapper.addView(label);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);
        p.leftMargin=dp(3);p.rightMargin=dp(3);nav.addView(wrapper,p);
        wrapper.setOnClickListener(v->action.run());
    }
    private void actionCard(LinearLayout parent,String title,String subtitle,String icon,int accent,Runnable action){
        LinearLayout c=card(parent);LinearLayout line=horizontal();
        TextView sym=text(icon,27,accent,true);sym.setGravity(Gravity.CENTER);
        sym.setBackground(shape(accent==GREEN?0xFFE7F8ED:0xFFE8F2FF,13));
        line.addView(sym,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout desc=vertical();
        TextView t=text(title,16,INK,true);TextView st=text(subtitle,12,MUTED,false);
        desc.addView(t);desc.addView(st);
        LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(0,-2,1);d.leftMargin=dp(12);line.addView(desc,d);
        line.addView(text("›",27,MUTED,false));c.addView(line);c.setOnClickListener(v->action.run());
    }
    private void statRow(LinearLayout parent,int total,int active,int resolved,int recurring){
        LinearLayout row=horizontal();
        stat(row,String.valueOf(total),"Total",BLUE);
        stat(row,String.valueOf(active),"En cours",0xFFDD7F14);
        stat(row,String.valueOf(resolved),"Résolus",GREEN);
        stat(row,String.valueOf(recurring),"Récurrents",0xFF9A4AD8);
        parent.addView(row);
    }
    private void stat(LinearLayout row,String value,String label,int color){
        LinearLayout c=vertical();c.setGravity(Gravity.CENTER);c.setPadding(dp(3),dp(13),dp(3),dp(13));
        c.setBackground(border(Color.WHITE,12,0xFFE4EAF3));
        TextView n=text(value,21,color,true);n.setGravity(Gravity.CENTER);
        TextView t=text(label,10,MUTED,false);t.setGravity(Gravity.CENTER);
        c.addView(n);c.addView(t);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=dp(2);p.rightMargin=dp(2);row.addView(c,p);
    }
    private void home(){
        base("Tout consigner, dater, prouver.","home");
        actionCard(body,"Nouveau signalement","Décrire un fait et joindre des preuves","＋",BLUE,this::form);
        actionCard(body,"Mises à jour","Rechercher la dernière version de LES FAITS","⟳",GREEN,()->SkillMajAuto.check(this,true));
        actionCard(body,"Mes fichiers et preuves","Photos, vidéos, PDF et documents liés aux dossiers","▧",BLUE,this::fileManager);
        heading(body,"Mes espaces");
        actionCard(body,"TRACE · Personnel / Terrain","Incidents, matériel, organisation, sécurité","⌖",BLUE,()->history("TERRAIN"));
        actionCard(body,"RAPPORT · Affaires personnelles","Dossiers et événements du quotidien","▣",GREEN,()->history("PERSONNEL"));
        List<Store.Incident> all=store.all();int open=0,resolved=0,recurring=0;
        for(Store.Incident i:all){
            if("Résolu".equals(i.status))resolved++;
            if("En cours".equals(i.status)||"Signalé".equals(i.status))open++;
            if(i.recurring)recurring++;
        }
        heading(body,"Tableau de bord");
        statRow(body,all.size(),open,resolved,recurring);
        gap(body,15);heading(body,"Derniers faits");
        if(all.isEmpty()){
            LinearLayout empty=card(body);
            empty.addView(text("Aucun signalement enregistré.",14,MUTED,false));
            empty.addView(text("Appuie sur « Nouveau signalement » pour commencer.",12,MUTED,false));
        }else for(int i=0;i<Math.min(5,all.size());i++) incidentCard(body,all.get(i));
    }
    private String labelDate(long time){return datetime.format(new Date(time));}
    private void incidentCard(LinearLayout parent,Store.Incident i){
        LinearLayout c=card(parent);
        TextView title=text(i.title,16,INK,true);
        c.addView(title);
        gap(c,5);
        c.addView(text(i.category+"  •  "+labelDate(i.occurred),12,MUTED,false));
        gap(c,5);
        int color="Résolu".equals(i.status)?GREEN:("Critique".equals(i.severity)?0xFFD04D21:BLUE);
        c.addView(text(i.status+(i.recurring?"  •  Récurrent":""),12,color,true));
        int attachments=store.filesForIncident(i.id).size();
        if(attachments>0)c.addView(text(attachments+" pièce(s) jointe(s)",11,MUTED,false));
        c.setOnClickListener(v->detail(i.id));
    }
    private void history(){history("TOUT");}
    private void history(String initial){
        base("Historique des signalements","list");
        String[] filters={"Tous les faits","TRACE · Personnel / Terrain","RAPPORT · Affaires personnelles","Problèmes récurrents","Non résolus"};
        Spinner spinner=spinner(body,"Afficher",filters);
        if("TERRAIN".equals(initial))spinner.setSelection(1);
        if("PERSONNEL".equals(initial))spinner.setSelection(2);
        EditText query=edit(body,"Rechercher","Titre, description, lieu, catégorie",false);
        LinearLayout items=vertical();body.addView(items);
        Runnable refresh=()->{
            items.removeAllViews();
            String q=query.getText().toString().toLowerCase(Locale.FRANCE);
            int choice=spinner.getSelectedItemPosition(),count=0;
            for(Store.Incident i:store.all()){
                if(choice==1&&!"TERRAIN".equals(i.space))continue;
                if(choice==2&&!"PERSONNEL".equals(i.space))continue;
                if(choice==3&&!i.recurring)continue;
                if(choice==4&&("Résolu".equals(i.status)||"Classé".equals(i.status)))continue;
                String hay=(i.title+" "+i.description+" "+i.location+" "+i.category).toLowerCase(Locale.FRANCE);
                if(!hay.contains(q))continue;
                incidentCard(items,i);count++;
            }
            if(count==0)items.addView(text("Aucun fait correspondant.",14,MUTED,false));
        };
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){refresh.run();}
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        query.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){refresh.run();}
            public void afterTextChanged(Editable e){}
        });
        refresh.run();
        addButton(body,"＋ Nouveau signalement",BLUE,this::form);
    }
    private EditText edit(LinearLayout parent,String name,String hint,boolean multiline){
        TextView l=text(name,13,INK,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(8);lp.bottomMargin=dp(5);parent.addView(l,lp);
        EditText field=new EditText(this);field.setTextColor(INK);field.setTextSize(14);field.setHint(hint);
        field.setHintTextColor(0xFF8B9AAF);
        field.setBackground(border(Color.WHITE,12,0xFFD5E0ED));
        field.setPadding(dp(12),dp(10),dp(12),dp(10));
        if(multiline){field.setMinLines(4);field.setGravity(Gravity.TOP);field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);}
        else field.setSingleLine(true);
        parent.addView(field,new LinearLayout.LayoutParams(-1,-2));
        return field;
    }
    private Spinner spinner(LinearLayout parent,String title,String[] options){
        TextView label=text(title,13,INK,true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);p.bottomMargin=dp(5);
        parent.addView(label,p);
        Spinner s=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);s.setPadding(dp(10),dp(8),dp(4),dp(8));
        s.setBackground(border(Color.WHITE,12,0xFFD5E0ED));
        parent.addView(s,new LinearLayout.LayoutParams(-1,dp(54)));
        return s;
    }

    /** Bibliothèque partagée : un fichier importé peut servir à plusieurs signalements. */
    private void fileManager(){
        base("Fichiers et pièces justificatives","vault");
        List<Store.Evidence> all=store.files();
        LinearLayout overview=card(body);
        overview.addView(text("Ma bibliothèque de preuves",19,BLUE,true));
        overview.addView(text(all.size()+" fichier(s) privés · photos, vidéos, audio, PDF et documents",12,MUTED,false));
        addButton(overview,"＋ Importer des fichiers",BLUE,()->pickEvidence(-1L));
        addButton(overview,"Synchroniser les demandes ChatGPT",NAVY,()->
            McpWorkerClient.check(getApplicationContext(),(ok,message)->toast(message)));
        overview.addView(text("Jusqu'à 250 Mo par fichier. Les originaux sont copiés dans l'espace privé de LES FAITS. Aucun fichier n'est envoyé automatiquement à ChatGPT.",11,MUTED,false));
        EditText search=edit(body,"Rechercher dans mes fichiers","Nom du fichier, type…",false);
        Spinner filter=spinner(body,"Filtrer",new String[]{"Tous","Photos","Vidéos","Audio","Documents"});
        LinearLayout results=vertical();
        body.addView(results);
        Runnable refresh=()->{
            results.removeAllViews();
            String query=search.getText().toString().trim().toLowerCase(Locale.FRANCE);
            String type=(String)filter.getSelectedItem();
            int visible=0;
            for(Store.Evidence item:store.files()){
                String kind=FileVault.kind(item.mime);
                if(!"Tous".equals(type)&&
                    !("Photos".equals(type)&&"Photo".equals(kind))&&
                    !("Vidéos".equals(type)&&"Vidéo".equals(kind))&&
                    !("Audio".equals(type)&&"Audio".equals(kind))&&
                    !("Documents".equals(type)&&("PDF".equals(kind)||"Document".equals(kind))))continue;
                if(!item.name.toLowerCase(Locale.FRANCE).contains(query)&&
                    !kind.toLowerCase(Locale.FRANCE).contains(query))continue;
                visible++;
                LinearLayout card=card(results);
                LinearLayout head=horizontal();head.setGravity(Gravity.CENTER_VERTICAL);
                if(item.mime.startsWith("image/")){
                    try{
                        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
                        BitmapFactory.decodeFile(FileVault.safeFile(this,item.path).getPath(),o);
                        int max=Math.max(o.outWidth,o.outHeight);
                        o.inSampleSize=Math.max(1,(max+299)/300);o.inJustDecodeBounds=false;
                        Bitmap thumbnail=BitmapFactory.decodeFile(item.path,o);
                        if(thumbnail!=null){
                            ImageView thumb=new ImageView(this);thumb.setImageBitmap(thumbnail);
                            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            head.addView(thumb,new LinearLayout.LayoutParams(dp(66),dp(66)));
                        }
                    }catch(Exception ignored){}
                }else{
                    String symbol="Vidéo".equals(kind)?"▶":"Audio".equals(kind)?"♫":"PDF".equals(kind)?"PDF":"▧";
                    TextView icon=text(symbol,"PDF".equals(symbol)?13:23,BLUE,true);icon.setGravity(Gravity.CENTER);
                    icon.setBackground(shape(0xFFE9F2FF,13));
                    head.addView(icon,new LinearLayout.LayoutParams(dp(66),dp(66)));
                }
                LinearLayout meta=vertical();
                TextView label=text(item.name,15,INK,true);label.setMaxLines(2);
                meta.addView(label);
                meta.addView(text(kind+" · "+FileVault.sizeLabel(item.size),12,MUTED,false));
                meta.addView(text(item.linkedCount+" rapport(s) lié(s)",11,item.linkedCount>0?GREEN:MUTED,false));
                LinearLayout.LayoutParams m=new LinearLayout.LayoutParams(0,-2,1);m.leftMargin=dp(12);
                head.addView(meta,m);card.addView(head);
                addButton(card,"Ouvrir / consulter",NAVY,()->openEvidence(item));
                addButton(card,"Associer à mes rapports",BLUE,()->chooseReportsForFile(item.id));
                if(item.linkedCount==0)addButton(card,"Supprimer de la bibliothèque",0xFF9D4D53,()->
                    new AlertDialog.Builder(this).setTitle("Supprimer ce fichier ?")
                        .setMessage("Il sera définitivement supprimé de la mémoire de LES FAITS.")
                        .setNegativeButton("Annuler",null)
                        .setPositiveButton("Supprimer",(d,w)->{
                            if(store.deleteEvidence(item.id)){toast("Fichier supprimé.");fileManager();}
                            else toast("Fichier utilisé par un rapport : suppression refusée.");
                        }).show());
            }
            if(visible==0){
                LinearLayout empty=card(results);
                empty.addView(text("Aucun fichier pour cette recherche.",14,MUTED,false));
                empty.addView(text("Importe une photo, une vidéo, un PDF ou une pièce justificative.",12,MUTED,false));
            }
        };
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){refresh.run();}
            public void afterTextChanged(Editable e){}
        });
        filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){refresh.run();}
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        refresh.run();
    }
    private void pickEvidence(long reportId){
        pendingEvidenceIncident=reportId;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{
            "image/*","video/*","audio/*","application/pdf","text/plain","text/csv",
            "application/msword","application/vnd.ms-excel","application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation","application/rtf"
        });
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(intent,PICK_EVIDENCE);}
        catch(Exception e){toast("Sélecteur de fichiers indisponible.");}
    }
    private void importEvidence(Intent data){
        final long target=pendingEvidenceIncident;pendingEvidenceIncident=-1L;
        final List<Uri> choices=new ArrayList<>();
        if(data.getClipData()!=null){
            for(int k=0;k<Math.min(10,data.getClipData().getItemCount());k++)
                choices.add(data.getClipData().getItemAt(k).getUri());
        }else if(data.getData()!=null)choices.add(data.getData());
        if(choices.isEmpty()){toast("Aucun fichier sélectionné.");return;}
        toast("Import en cours ("+choices.size()+" fichier(s))…");
        fileIo.execute(()->{
            int added=0;String error=null;
            try(Store background=new Store(getApplicationContext())){
                for(Uri uri:choices){
                    try{
                        long fileId=FileVault.importUri(getApplicationContext(),background,uri);
                        if(target>0&&!background.linkEvidence(target,fileId))
                            throw new IllegalStateException("Rapport n°"+target+" introuvable");
                        added++;
                    }catch(Exception ex){error=ex.getMessage();}
                }
            }catch(Exception ex){error=ex.getMessage();}
            final int done=added;final String lastError=error;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                toast(done+" fichier(s) importé(s)"+(lastError==null?".":" · "+lastError));
                if(target>0&&store.get(target)!=null)detail(target);
                else fileManager();
            });
        });
    }
    private void openEvidence(Store.Evidence file){
        try{
            FileVault.safeFile(this,file.path);
            Uri uri=new Uri.Builder().scheme("content").authority("fr.sync30.lesfaits.evidence")
                .appendPath(String.valueOf(file.id)).build();
            Intent view=new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(uri,file.mime);
            view.setClipData(ClipData.newRawUri("LES FAITS",uri));
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(view,"Ouvrir "+FileVault.kind(file.mime)));
        }catch(Exception ex){toast("Lecteur indisponible : "+ex.getClass().getSimpleName());}
    }
    private void chooseReportsForFile(long fileId){
        Store.Evidence file=store.file(fileId);
        if(file==null){toast("Fichier introuvable.");fileManager();return;}
        List<Store.Incident> incidents=store.all();
        if(incidents.isEmpty()){toast("Crée d'abord un signalement.");return;}
        String[] labels=new String[incidents.size()];boolean[] selected=new boolean[incidents.size()];
        for(int k=0;k<incidents.size();k++){
            Store.Incident i=incidents.get(k);
            labels[k]="N°"+i.id+" · "+i.title;
            for(Store.Evidence linked:store.filesForIncident(i.id))
                if(linked.id==fileId){selected[k]=true;break;}
        }
        new AlertDialog.Builder(this).setTitle("Associer : "+file.name)
            .setMultiChoiceItems(labels,selected,(dialog,which,isChecked)->selected[which]=isChecked)
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Enregistrer",(dialog,which)->{
                for(int k=0;k<incidents.size();k++){
                    if(selected[k])store.linkEvidence(incidents.get(k).id,fileId);
                    else store.unlinkEvidence(incidents.get(k).id,fileId);
                }
                toast("Associations enregistrées.");fileManager();
            }).show();
    }
    private void chooseFilesForReport(long reportId){
        List<Store.Evidence> files=store.files();
        if(files.isEmpty()){pickEvidence(reportId);return;}
        String[] labels=new String[files.size()];boolean[] selected=new boolean[files.size()];
        for(int k=0;k<files.size();k++){
            Store.Evidence file=files.get(k);
            labels[k]=file.name+" · "+FileVault.kind(file.mime);
            for(Store.Evidence linked:store.filesForIncident(reportId))
                if(linked.id==file.id){selected[k]=true;break;}
        }
        new AlertDialog.Builder(this).setTitle("Pièces jointes du rapport n°"+reportId)
            .setMultiChoiceItems(labels,selected,(dialog,which,isChecked)->selected[which]=isChecked)
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Enregistrer",(dialog,which)->{
                for(int k=0;k<files.size();k++){
                    if(selected[k])store.linkEvidence(reportId,files.get(k).id);
                    else store.unlinkEvidence(reportId,files.get(k).id);
                }
                toast("Pièces jointes mises à jour.");detail(reportId);
            }).show();
    }
    private void form(){
        newPhotos.clear();formTime=System.currentTimeMillis();
        base("Nouveau signalement","home");
        LinearLayout c=card(body);
        c.addView(text("1 / Informations sur le fait",17,BLUE,true));
        Spinner section=spinner(c,"Espace concerné",SPACES);
        Spinner category=spinner(c,"Catégorie",CATEGORIES);
        EditText title=edit(c,"Titre du problème *","Ex. Panne de la balayeuse",false);
        EditText description=edit(c,"Description factuelle *","Que s'est-il passé ? Conséquences, actions réalisées…",true);
        dictationTarget=description;
        addButton(c,"🎙  Dicter la description",NAVY,()->{
            try{
                Intent speech=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"fr-FR");
                startActivityForResult(speech,DICTATE);
            }catch(Exception e){toast("Dictée vocale indisponible sur cet appareil.");}
        });
        EditText location=edit(c,"Lieu / secteur (facultatif)","Ex. Atelier, rue, domicile…",false);
        TextView dateLabel=text("Date et heure du fait : "+labelDate(formTime),13,INK,true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(14);c.addView(dateLabel,p);
        addButton(c,"Modifier la date / heure",NAVY,()->{
            Calendar cal=Calendar.getInstance();cal.setTimeInMillis(formTime);
            new DatePickerDialog(this,(d,year,month,day)->{
                cal.set(Calendar.YEAR,year);cal.set(Calendar.MONTH,month);cal.set(Calendar.DAY_OF_MONTH,day);
                new TimePickerDialog(this,(v,h,m)->{
                    cal.set(Calendar.HOUR_OF_DAY,h);cal.set(Calendar.MINUTE,m);cal.set(Calendar.SECOND,0);
                    formTime=cal.getTimeInMillis();dateLabel.setText("Date et heure du fait : "+labelDate(formTime));
                },cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show();
            },cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        Spinner urgency=spinner(c,"Niveau d'urgence",SEVERITIES);
        CheckBox recurring=new CheckBox(this);recurring.setText("C'est un problème récurrent");recurring.setTextColor(INK);
        c.addView(recurring);
        gap(c,10);c.addView(text("2 / Photos et pièces justificatives",17,BLUE,true));
        photoCounter=text("Aucune photo ajoutée",12,MUTED,false);c.addView(photoCounter);
        addButton(c,"＋ Ajouter des photos",BLUE,()->{
            Intent pick=new Intent(Intent.ACTION_GET_CONTENT);pick.setType("image/*");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
            try{startActivityForResult(Intent.createChooser(pick,"Choisir des photos"),PICK_PHOTO);}
            catch(Exception e){toast("Galerie indisponible.");}
        });
        addButton(c,"📷 Prendre une photo",NAVY,()->{
            try{startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE),CAMERA);}
            catch(Exception e){toast("Appareil photo indisponible.");}
        });
        c.addView(text("Les images restent dans la mémoire privée de l'application. La photo prise directement est une vignette.",11,MUTED,false));
        gap(c,15);
        addButton(c,"✓ Enregistrer le signalement",GREEN,()->{
            String t=title.getText().toString().trim(),desc=description.getText().toString().trim();
            if(t.isEmpty()||desc.isEmpty()){toast("Renseigne un titre et une description.");return;}
            Store.Incident i=new Store.Incident();
            i.space=section.getSelectedItemPosition()==0?"TERRAIN":"PERSONNEL";
            i.category=(String)category.getSelectedItem();i.title=t;i.description=desc;
            i.location=location.getText().toString().trim();i.occurred=formTime;
            i.severity=(String)urgency.getSelectedItem();i.recurring=recurring.isChecked();
            try{
                long id=store.insert(i);
                for(String path:newPhotos)store.addPhoto(id,path);
                newPhotos.clear();toast("Signalement enregistré hors connexion.");detail(id);
            }catch(Exception e){toast("Impossible d'enregistrer : "+e.getMessage());}
        });
    }
    private void detail(long id){
        Store.Incident item=store.get(id);
        if(item==null){toast("Signalement introuvable.");history();return;}
        base("Détail du signalement n° "+id,"list");
        LinearLayout c=card(body);
        c.addView(text(item.title,22,INK,true));gap(c,7);
        c.addView(text("Date du fait : "+labelDate(item.occurred),13,MUTED,false));
        c.addView(text("Espace : "+("TERRAIN".equals(item.space)?SPACES[0]:SPACES[1]),13,MUTED,false));
        c.addView(text("Catégorie : "+item.category,13,MUTED,false));
        c.addView(text("Urgence : "+item.severity+"  •  Récurrent : "+(item.recurring?"Oui":"Non"),13,MUTED,false));
        if(!item.location.isEmpty())c.addView(text("Lieu : "+item.location,13,MUTED,false));
        gap(c,12);c.addView(text("Constat",16,BLUE,true));gap(c,5);
        c.addView(text(item.description,14,INK,false));
        List<String> photos=store.photos(id);
        if(!photos.isEmpty()){
            gap(c,15);c.addView(text("Photos / preuves ("+photos.size()+")",16,BLUE,true));
            for(String path:photos){
                try {
                    BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=4;
                    Bitmap img=BitmapFactory.decodeFile(path,opts);
                    if(img==null)continue;
                    ImageView view=new ImageView(this);view.setImageBitmap(img);view.setAdjustViewBounds(true);
                    view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setMaxHeight(dp(250));
                    LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=dp(8);c.addView(view,pp);
                }catch(Exception ignored){}
            }
        }

        LinearLayout filesCard=card(body);
        List<Store.Evidence> attached=store.filesForIncident(id);
        filesCard.addView(text("Pièces jointes · "+attached.size(),17,BLUE,true));
        filesCard.addView(text("Photos, vidéos, enregistrements, PDF et documents associés à ce dossier.",12,MUTED,false));
        addButton(filesCard,"＋ Importer une pièce pour ce rapport",BLUE,()->pickEvidence(id));
        addButton(filesCard,"Associer des fichiers déjà importés",NAVY,()->chooseFilesForReport(id));
        for(Store.Evidence file:attached){
            LinearLayout row=card(body);
            row.addView(text(file.name,15,INK,true));
            row.addView(text(FileVault.kind(file.mime)+" · "+FileVault.sizeLabel(file.size),12,MUTED,false));
            addButton(row,"Ouvrir cette pièce",BLUE,()->openEvidence(file));
            addButton(row,"Détacher de ce rapport",NAVY,()->
                new AlertDialog.Builder(this).setTitle("Retirer le lien ?")
                    .setMessage("Le fichier restera dans la bibliothèque et les autres rapports.")
                    .setNegativeButton("Annuler",null)
                    .setPositiveButton("Détacher",(d,w)->{
                        store.unlinkEvidence(id,file.id);detail(id);
                    }).show());
        }
        LinearLayout actions=card(body);
        actions.addView(text("Suivi du problème",17,BLUE,true));
        Spinner status=spinner(actions,"État du dossier",STATUSES);
        for(int j=0;j<STATUSES.length;j++)if(STATUSES[j].equals(item.status))status.setSelection(j);
        addButton(actions,"Mettre à jour l'état",BLUE,()->{
            store.status(id,(String)status.getSelectedItem());toast("État mis à jour.");detail(id);
        });
        EditText note=edit(actions,"Ajouter une observation","Réponse obtenue, action réalisée, suite à donner…",true);
        addButton(actions,"Enregistrer l'observation",NAVY,()->{
            String s=note.getText().toString().trim();
            if(s.isEmpty()){toast("Saisis une observation.");return;}
            store.addNote(id,s);toast("Observation enregistrée.");detail(id);
        });
        List<Store.Note> notes=store.notes(id);
        if(!notes.isEmpty()){
            heading(body,"Historique des observations");
            for(Store.Note n:notes){
                LinearLayout nc=card(body);
                nc.addView(text(labelDate(n.at),11,MUTED,true));
                nc.addView(text(n.text,14,INK,false));
            }
        }
        addButton(body,"Exporter ce dossier en PDF",GREEN,()->exportPdf(Collections.singletonList(item)));
        addButton(body,"Supprimer ce dossier",0xFFB13E42,()->new AlertDialog.Builder(this)
            .setTitle("Supprimer définitivement ?")
            .setMessage("Ce dossier, ses anciennes photos et ses observations seront effacés. Les fichiers de la bibliothèque seront conservés pour les autres rapports.")
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Supprimer",(d,w)->{store.delete(id);history();}).show());
    }
    private void reports(){
        base("Rapports / Affaires personnelles","report");
        actionCard(body,"RAPPORT · Affaires personnelles","Consulter les faits de la vie personnelle","▣",GREEN,()->history("PERSONNEL"));
        List<Store.Incident> all=store.all();
        int terrain=0,personal=0,recurring=0;
        for(Store.Incident i:all){
            if("TERRAIN".equals(i.space))terrain++;else personal++;
            if(i.recurring)recurring++;
        }
        heading(body,"Synthèse des dossiers");
        LinearLayout summary=card(body);
        summary.addView(text("Total : "+all.size(),17,INK,true));
        summary.addView(text("TRACE · Personnel / Terrain : "+terrain,14,INK,false));
        summary.addView(text("RAPPORT · Affaires personnelles : "+personal,14,INK,false));
        summary.addView(text("Problèmes récurrents : "+recurring,14,INK,false));
        summary.addView(text("Fichiers dans ma bibliothèque : "+store.files().size(),14,INK,false));
        gap(body,10);
        actionCard(body,"Exporter tous les dossiers en PDF","Rapport daté et inventaire des pièces (vidéos non intégrées au PDF)","▤",BLUE,()->exportPdf(store.all()));
        body.addView(text("Les rapports PDF sont enregistrés à l'emplacement que tu choisis sur ton téléphone. Aucun envoi automatique.",12,MUTED,false));
    }
    private void exportPdf(List<Store.Incident> list){
        if(list.isEmpty()){toast("Aucun signalement à exporter.");return;}
        pendingPdf=new ArrayList<>(list);
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/pdf");intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,"Les_Faits_"+new SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).format(new Date())+".pdf");
        try{startActivityForResult(intent,SAVE_PDF);}
        catch(Exception e){toast("Aucune application disponible pour enregistrer un PDF.");}
    }
    private void settings(){
        base("Connexion et confidentialité","home");
        LinearLayout c=card(body);
        c.addView(text("Mon serveur MCP existant",18,BLUE,true));gap(c,8);
        c.addView(text(McpWorkerClient.MCP_URL,12,INK,false));
        gap(c,10);
        boolean connected=McpWorkerClient.connected(prefs);
        boolean active=McpWorkerClient.isEnabled(prefs);
        c.addView(text(!connected?"Non associé":active?"Connecté au serveur · partage activé":"Associé · partage désactivé",
            14,connected&&active?GREEN:MUTED,true));
        c.addView(text("LES FAITS reprend le système d'association déjà utilisé par les applications CHK. Aucune connexion OAuth ni jeton permanent à recopier. Une invitation temporaire autorise l'appareil une seule fois.",12,MUTED,false));
        gap(c,8);
        if(!connected){
            EditText invite=edit(c,"Invitation MCP temporaire","Coller l'invitation fournie par ChatGPT",false);
            addButton(c,"Associer LES FAITS au serveur",BLUE,()->{
                String code=invite.getText().toString().trim();
                new AlertDialog.Builder(this)
                    .setTitle("Autoriser la lecture et la création de dossiers ?")
                    .setMessage("Ce serveur pourra consulter et ajouter des dossiers depuis ChatGPT avec approbation. Les photos restent sur le téléphone. Le partage peut être désactivé.")
                    .setNegativeButton("Annuler",null)
                    .setPositiveButton("Autoriser",(d,w)->McpWorkerClient.pair(getApplicationContext(),code,(ok,message)->
                        new AlertDialog.Builder(this).setTitle(ok?"Association réussie":"Association impossible")
                            .setMessage(message).setPositiveButton("OK",(dialog,which)->settings()).show()))
                    .show();
            });
        }else{
            CheckBox share=new CheckBox(this);
            share.setText("Autoriser les demandes ChatGPT (lecture et création après association)");
            share.setTextColor(INK);share.setChecked(active);
            share.setOnCheckedChangeListener((v,checked)->{
                McpWorkerClient.setEnabled(this,prefs,checked);
                toast(checked?"Connexion MCP autorisée":"Connexion MCP désactivée");
            });
            c.addView(share);
            if(!"true".equals(prefs.value("worker_write_enabled"))||!"true".equals(prefs.value("worker_files_enabled"))){
                c.addView(text("Pour que ChatGPT puisse associer les fichiers aux rapports, crée une invitation MCP et réassocie LES FAITS. Les dossiers et fichiers existants sont conservés.",12,MUTED,false));
                EditText renewal=edit(c,"Nouvelle invitation MCP","Coller le code de réassociation",false);
                addButton(c,"Activer la gestion des fichiers ChatGPT",BLUE,()->new AlertDialog.Builder(this)
                    .setTitle("Autoriser les fichiers et rapports depuis ChatGPT ?")
                    .setMessage("La réassociation ajoute la consultation des fichiers et leur liaison aux rapports depuis ChatGPT. Les écritures restent soumises aux approbations du serveur CHK Universal.")
                    .setNegativeButton("Annuler",null)
                    .setPositiveButton("Réassocier",(d,w)->McpWorkerClient.pair(getApplicationContext(),renewal.getText().toString().trim(),(ok,message)->
                        new AlertDialog.Builder(this).setTitle(ok?"Association réussie":"Association impossible")
                            .setMessage(message).setPositiveButton("OK",(dialog,which)->settings()).show()))
                    .show());
            }
            addButton(c,"Vérifier ma connexion MCP",BLUE,()->
                McpWorkerClient.check(getApplicationContext(),(ok,message)->
                    new AlertDialog.Builder(this).setTitle(ok?"MCP disponible":"MCP indisponible")
                        .setMessage(message).setPositiveButton("OK",null).show()));
            addButton(c,"Dissocier cet appareil",NAVY,()->new AlertDialog.Builder(this)
                .setTitle("Dissocier LES FAITS ?")
                .setMessage("Le serveur ne pourra plus interroger l'application depuis cet appareil. Aucun signalement local ne sera effacé.")
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Dissocier",(d,w)->{McpWorkerClient.disconnect(this,prefs);settings();}).show());
        }
        LinearLayout info=card(body);
        info.addView(text("Consultation et création depuis ChatGPT",17,BLUE,true));gap(info,7);
        info.addView(text("Lorsque le partage est actif, LES FAITS peut lister les faits, consulter ou créer un signalement et associer une pièce déjà importée à un rapport après réassociation et autorisation. Seules les métadonnées des fichiers sont transmises, jamais les vidéos ou photos elles-mêmes. La vérification a lieu à l’ouverture, puis périodiquement selon Android.",13,INK,false));
        gap(info,8);
        info.addView(text("Les médias restent sur le téléphone. ChatGPT peut consulter les noms et identifiants des fichiers puis demander de les associer aux rapports ; les octets des vidéos et photos ne sont pas transmis.",12,MUTED,false));
        LinearLayout updateCard=card(body);
        updateCard.addView(text("SkillMajAuto · Mises à jour",18,BLUE,true));
        gap(updateCard,6);
        updateCard.addView(text("SkillMajAuto contrôle automatiquement les GitHub Releases à l'ouverture et environ toutes les 12 heures en arrière-plan. Une notification annonce une nouvelle version. L'APK est téléchargée, vérifiée et installée depuis LES FAITS avec ta confirmation Android.",12,INK,false));
        CheckBox automatic=new CheckBox(this);automatic.setText("Vérifier automatiquement les mises à jour");
        automatic.setTextColor(INK);automatic.setChecked(SkillMajAuto.autoEnabled(this));
        automatic.setOnCheckedChangeListener((b,checked)->SkillMajAuto.setAuto(this,checked));
        updateCard.addView(automatic);
        if(android.os.Build.VERSION.SDK_INT>=33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            addButton(updateCard,"Activer les notifications de mises à jour",NAVY,()->
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},5401));
        addButton(updateCard,"SkillMajAuto · Vérifier les mises à jour",BLUE,()->SkillMajAuto.check(this,true));
        updateCard.addView(text("Une APK officielle et sa signature sont vérifiées avant de proposer l'installation, avec ton accord Android.",11,MUTED,false));
        LinearLayout version=card(body);
        version.addView(text("LES FAITS  •  SYNC 30  •  v"+SkillMajAuto.versionName(this),12,MUTED,true));
        version.addView(text("Application gratuite. Sauvegarde tes rapports PDF avant toute désinstallation.",12,MUTED,false));
    }
    private String copyPhoto(Uri uri)throws Exception {
        File dir=new File(getFilesDir(),"photos");
        if(!dir.exists()&&!dir.mkdirs())throw new Exception("Stockage des photos indisponible.");
        File dst=new File(dir,UUID.randomUUID()+".img");
        try(InputStream in=getContentResolver().openInputStream(uri);
            OutputStream out=new FileOutputStream(dst)){
            if(in==null)throw new Exception("Image illisible.");
            byte[] buffer=new byte[8192];int n;long bytes=0;
            while((n=in.read(buffer))>=0){
                bytes+=n;if(bytes>15_000_000)throw new Exception("Photo trop volumineuse (15 Mo maximum).");
                out.write(buffer,0,n);
            }
        }catch(Exception e){dst.delete();throw e;}
        return dst.getAbsolutePath();
    }
    private String saveCameraBitmap(Bitmap bmp)throws Exception {
        File dir=new File(getFilesDir(),"photos");
        if(!dir.exists()&&!dir.mkdirs())throw new Exception("Stockage indisponible.");
        File dst=new File(dir,UUID.randomUUID()+".jpg");
        try(OutputStream out=new FileOutputStream(dst)){
            if(!bmp.compress(Bitmap.CompressFormat.JPEG,85,out))throw new Exception("Photo non enregistrée.");
        }catch(Exception e){dst.delete();throw e;}
        return dst.getAbsolutePath();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null)return;
        try {
            if(request==PICK_EVIDENCE){
                importEvidence(data);
            }else if(request==PICK_PHOTO){
                if(data.getClipData()!=null){
                    for(int i=0;i<data.getClipData().getItemCount();i++){
                        newPhotos.add(copyPhoto(data.getClipData().getItemAt(i).getUri()));
                    }
                }else if(data.getData()!=null)newPhotos.add(copyPhoto(data.getData()));
                if(photoCounter!=null)photoCounter.setText(newPhotos.size()+" photo(s) sélectionnée(s)");
            }else if(request==CAMERA){
                Bundle extras=data.getExtras();
                Bitmap photo=extras==null?null:(Bitmap)extras.get("data");
                if(photo!=null)newPhotos.add(saveCameraBitmap(photo));
                if(photoCounter!=null)photoCounter.setText(newPhotos.size()+" photo(s) sélectionnée(s)");
            }else if(request==DICTATE){
                ArrayList<String> results=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if(results!=null&&!results.isEmpty()&&dictationTarget!=null){
                    String old=dictationTarget.getText().toString().trim();
                    dictationTarget.setText(old.isEmpty()?results.get(0):old+" "+results.get(0));
                }
            }else if(request==SAVE_PDF){
                Uri uri=data.getData();
                if(uri!=null&&pendingPdf!=null)try(OutputStream out=getContentResolver().openOutputStream(uri)){
                    if(out==null)throw new Exception("Document non accessible.");
                    PdfExporter.write(out,pendingPdf,store);
                    toast("Rapport PDF enregistré.");
                }
                pendingPdf=null;
            }
        }catch(Exception e){toast("Opération impossible : "+e.getMessage());}
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
