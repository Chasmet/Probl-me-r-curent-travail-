package fr.sync30.lesfaits;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Daily Android-managed official update check. */
public final class UpdateCheckJob extends JobService {
    @Override public boolean onStartJob(JobParameters params){
        UpdateManager.checkBackground(getApplicationContext(),()->jobFinished(params,false));
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){return false;}
}
