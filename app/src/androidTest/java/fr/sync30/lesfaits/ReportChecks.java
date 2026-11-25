package fr.sync30.lesfaits;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.zip.*;

public final class ReportChecks extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            checkReports(getTargetContext());
            checkControls();
            result.putString("checks","reports-documents-ok");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable failure) {
            result.putString("failure",android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED,result);
        }
    }

    private static void require(boolean value,String message){
        if(!value) throw new AssertionError(message);
    }

    private static android.view.View find(android.view.View view,String text) {
        if(view instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView)view).getText())) return view;
        if(view instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                android.view.View child=find(group.getChildAt(i),text);
                if(child!=null)return child;
            }
        }
        return null;
    }

    private void checkControls()throws Exception {
        android.content.Intent launch=new android.content.Intent(getTargetContext(),MainActivity.class);
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity=startActivitySync(launch);
        waitForIdleSync();
        final android.widget.EditText[] fields=new android.widget.EditText[3];
        runOnMainSync(()->{
            android.view.View root=activity.findViewById(android.R.id.content);
            android.view.View start=find(root,"Nouveau signalement");
            while(start!=null && !start.isClickable())start=(android.view.View)start.getParent();
            require(start!=null,"Nouveau signalement accessible");start.performClick();
            findTextFields(root,fields,new int[]{0});
            require(fields[2]!=null,"Champs de saisie");
            new ReportControls(root,0,fields[0],fields[1],fields[2]).enable(true);
        });
        waitForIdleSync();
        org.json.JSONObject view=ReportControls.run("lesfaits_vision",null);
        require(view.getString("image_jpeg_base64").length()>100,"Capture de l’éditeur");
        org.json.JSONObject args=new org.json.JSONObject().put("session",view.getString("session")).put("champ","titre").put("texte","Essai clavier");
        require(ReportControls.run("lesfaits_souris",args).getString("champ").equals("titre"),"Clic sur champ");
        ReportControls.run("lesfaits_clavier",args);
        runOnMainSync(()->{
            require(fields[0].getText().toString().equals("Essai clavier"),"Texte saisi");
            ReportControls.leave();activity.finish();
        });
        try{
            ReportControls.run("lesfaits_vision",null);
            throw new AssertionError("Capture possible après fermeture");
        }catch(IllegalArgumentException expected){}
    }

    private static void findTextFields(android.view.View view,android.widget.EditText[] out,int[] count){
        if(view instanceof android.widget.EditText && count[0]<out.length) out[count[0]++]=(android.widget.EditText)view;
        if(view instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)findTextFields(group.getChildAt(i),out,count);
        }
    }

    private static void checkReports(Context context)throws Exception {
        try(Store store=new Store(context)){
            Store.Incident initial=new Store.Incident();
            initial.title="Test de suivi"; initial.description="Constat initial";
            long id=store.insert(initial);
            store.addNote(id,"Observation");
            store.status(id,"En cours");
            File doc=File.createTempFile("check",".bin",FileVault.directory(context));
            try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(doc))){
                zip.putNextEntry(new ZipEntry("word/document.xml"));
                zip.write("<document><p><t>Observation de terrain</t></p></document>".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            long fileId=store.addEvidence("note.docx","application/octet-stream",doc.getCanonicalPath(),doc.length());
            require(store.linkEvidence(id,fileId),"Association");
            try{
                require(EvidenceReader.read(context,store.file(fileId),1).getString("texte").equals("Observation de terrain"),"Texte DOCX");
                Store.Incident original=store.get(id), changed=store.get(id);
                changed.title="Titre corrigé";
                require(store.saveReport(changed,original,Collections.emptyList())==id,"Identité du dossier");
                require(store.get(id).created==original.created && store.get(id).status.equals("En cours"),"Date et statut");
                require(store.notes(id).size()==1 && store.filesForIncident(id).size()==1,"Historique et pièces");
                store.replaceField(id,"title","Titre corrigé","Titre MCP","task_test_123");
                store.replaceField(id,"title","Titre corrigé","Titre MCP","task_test_123");
                require(store.get(id).title.equals("Titre MCP"),"Correction MCP rejouée");
                try {
                    store.saveReport(changed,original,Collections.emptyList());
                    throw new AssertionError("Modification concurrente écrasée");
                } catch(IllegalStateException expected){}
                require(FileVault.allowedMime(null,"preuve.zzz").equals("application/octet-stream"),"Format inconnu");
            }finally{
                store.delete(id);
                store.deleteEvidence(fileId);
            }
        }
    }
}
