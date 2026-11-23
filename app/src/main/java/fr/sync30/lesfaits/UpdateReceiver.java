package fr.sync30.lesfaits;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

/** Receives only local PackageInstaller callbacks and opens Android's consent screen. */
public final class UpdateReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(!"fr.sync30.lesfaits.UPDATE_STATUS".equals(intent.getAction()))return;
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            Intent confirm;
            if(Build.VERSION.SDK_INT>=33)
                confirm=intent.getParcelableExtra(Intent.EXTRA_INTENT,Intent.class);
            else confirm=(Intent)intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if(confirm!=null){
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try{context.startActivity(confirm);}
                catch(Exception e){Toast.makeText(context,"Ouvre Android pour confirmer l'installation.",Toast.LENGTH_LONG).show();}
            }
        }else if(status!=PackageInstaller.STATUS_SUCCESS){
            String reason=intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(context,"Mise à jour refusée ou impossible"+(reason==null?"":": "+reason),Toast.LENGTH_LONG).show();
        }
    }
}
