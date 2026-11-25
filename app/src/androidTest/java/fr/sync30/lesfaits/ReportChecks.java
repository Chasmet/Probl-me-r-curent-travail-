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
