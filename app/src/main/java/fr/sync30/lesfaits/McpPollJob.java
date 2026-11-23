package fr.sync30.lesfaits;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Battery-efficient background polling, subject to Android scheduler and Doze. */
public final class McpPollJob extends JobService {
    @Override public boolean onStartJob(JobParameters params){
        McpWorkerClient.check(getApplicationContext(),(ok,message)->jobFinished(params,!ok));
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){
        return true;
    }
}
