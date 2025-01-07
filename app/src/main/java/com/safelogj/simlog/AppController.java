package com.safelogj.simlog;

import android.app.Application;
import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.safelogj.simlog.collecting.LogWriter;
import com.safelogj.simlog.collecting.SimCard;
import com.safelogj.simlog.collecting.SimListener;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AppController extends Application {

    private ExecutorService mExecutor;
    private Future<?> mTask;
    private volatile List<SimCard> mCheckedSims;
    private LocalDateTime mStartCollection;
    private File mExternalFileDir;
    private TelephonyManager mTelephonyManager;
    private final Map<String, Path> mFilesPaths = new HashMap<>();
    private final Map<TelephonyManager, SimListener> mMapManagers = new HashMap<>();


    public List<SimCard> getCheckedSims() {
        return mCheckedSims;
    }

    public void setCheckedSims(List<SimCard> checkedSims) {
        mCheckedSims = checkedSims;
    }

    public Map<String, Path> getFilesPaths() {
        updateFilesPaths();
        return mFilesPaths;
    }

    public LocalDateTime getStartCollection() {
        return mStartCollection;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mExternalFileDir = getExternalFilesDir(null);
        mExecutor = Executors.newSingleThreadExecutor();
    }

    public void startCollecting() {
        SimCard.setExternalFileDir(mExternalFileDir);
        for (SimCard simCard : mCheckedSims) {
            TelephonyManager simManager = mTelephonyManager.createForSubscriptionId(simCard.getSubscriptionId());
            SimListener simListener = new SimListener(simCard);
            simManager.listen(simListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
            mMapManagers.put(simManager, simListener);
        }
        mStartCollection = LocalDateTime.now();
        startExecutor();
    }

    private void startExecutor() {
        mTask = mExecutor.submit(new LogWriter(mCheckedSims));
    }

    public void stopCollecting() {
        for (Map.Entry<TelephonyManager, SimListener> entry : mMapManagers.entrySet()) {
            TelephonyManager telephonyManager = entry.getKey();
            SimListener listenerWriter = entry.getValue();
            telephonyManager.listen(listenerWriter, PhoneStateListener.LISTEN_NONE);
        }
        mMapManagers.clear();
        stopExecutor();
    }

    private void stopExecutor() {
        mTask.cancel(true);
    }

    public boolean updateFilesPaths() {
        mFilesPaths.clear();
        if (mExternalFileDir != null && mExternalFileDir.isDirectory()) {
            File[] files = mExternalFileDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        mFilesPaths.put(file.getName(), file.toPath());
                    }
                }
            }
        }
        return mFilesPaths.isEmpty();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (!mExecutor.isShutdown()) mExecutor.shutdown();
    }
}
