package com.safelogj.simlog.collecting;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.safelogj.simlog.AppController;
import com.safelogj.simlog.R;

import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogWriteService extends Service {
    private static final String WAKELOGTAG = "Simlog::WakelockTag";
    private static final String LISTENER_THREAD = "HandlerThread";
    private static final ThreadFactory lowPriorityFactory = runnable -> {
        Thread t = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            runnable.run();
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            t.setName("RoutersExecutor-" + t.threadId());
        } else {
            t.setName("RoutersExecutor-" + t.getId());
        }
        return t;
    };
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                getWakeLock();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                releaseWakeLock();
            }
        }
    };
    private PowerManager powerManager;
    private PowerManager.WakeLock mWakeLock;
    private List<SimCardData> mCheckedSims;
    private TelephonyManager mTelephonyManager;
    private final Map<SimCardData, Map.Entry<TelephonyManager, SimListener>> mSimListeners = new HashMap<>();
    private final IBinder mBinder = new LocalBinder();
    private LocalDateTime mStartCollection;
    private Thread mThread;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private ThreadPoolExecutor routersExecutor;
    private int corePoolSize;
    private int maximumPoolSize;
    private int executorPoolQueueSize;
    private final AtomicBoolean isAlreadyStopped = new AtomicBoolean(false);


    public List<SimCardData> getCheckedSims() {
        return mCheckedSims;
    }

    public LocalDateTime getStartCollection() {
        return mStartCollection;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        showNotification();
        initResources();
        registerScreenListener();
        startThread();
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        isAlreadyStopped.set(true);
        unregisterScreenListener();
        stopThreads();
        if (mHandler != null) {
            mHandler.post(this::stopFinally);
        } else {
            stopFinally(); // на всякий случай
        }
        super.onDestroy();
    }

    private synchronized void stopFinally() {
        clearSimListeners();
        Log.d(AppController.LOG_TAG, Thread.currentThread().getName() + " нить останавливает слушатели симок )");
        stopWriters();
        releaseWakeLock();
        stopForeground(STOP_FOREGROUND_REMOVE);

        mWakeLock = null;
        mCheckedSims = null;
        mStartCollection = null;
        HandlerThread handlerThread = mHandlerThread;
        mHandlerThread = null;
        mHandler = null;
        if (handlerThread != null) {
            boolean handlerThreadB = handlerThread.quitSafely();
            Log.d(AppController.LOG_TAG, Thread.currentThread().getName() + " результат завершения mHandThread.quitSafely()" + handlerThreadB);
        }
    }


    public class LocalBinder extends Binder {
        public LogWriteService getLogWriteService() {
            return LogWriteService.this;
        }
    }

    public void clearSimListeners() {
        for (Map.Entry<TelephonyManager, SimListener> entry : mSimListeners.values()) {
            if (entry != null) {
                Log.d(AppController.LOG_TAG, "Запущен в нэндлере очистка слушателя для симки " + Thread.currentThread().getName());
                TelephonyManager simManager = entry.getKey();
                SimListener simListener = entry.getValue();
                if (simManager != null && simListener != null) {
                    simManager.listen(simListener, PhoneStateListener.LISTEN_NONE);
                }
            }
        }
        mSimListeners.clear();
    }

    private void stopThreads() {
        if (mThread != null && mThread.isAlive()) {
            mThread.interrupt();
            Log.d(AppController.LOG_TAG, "Вызван интерапт у сервисной нити " + Thread.currentThread().getName());
        }
        mThread = null;

        ThreadPoolExecutor executor = routersExecutor;
        routersExecutor = null;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void startThread() {
        mThread = getThread();
        try {
            mThread.start();
            if (!powerManager.isInteractive()) {
                getWakeLock();
            }
        } catch (Exception e) {
            // В журнал
        }
    }

    private void showNotification() {
        Intent notificationIntent = new Intent(this, NotificationActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, AppController.NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        startForeground(1, notificationBuilder.build());
    }

    private Thread getThread() {
        return new Thread(() -> {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
                while (!Thread.currentThread().isInterrupted()) {
                    if (mCheckedSims == null || mCheckedSims.isEmpty()) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    writeData();
                    TimeUnit.SECONDS.sleep(50); // старое 25
                }
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private synchronized void writeData() {
        if (mCheckedSims != null && !mCheckedSims.isEmpty()) {
            for (SimCardData simCard : mCheckedSims) {
                if (simCard != null && !isAlreadyStopped.get()) {
                    simCard.writeLine();
                    if (!simCard.isRouter()) {
                        setSimListener(simCard);
                    } else {
                        updateRoutersData(simCard);
                    }
                }
            }

            if (corePoolSize == maximumPoolSize) {
                corePoolSize = Math.min(Math.max(1, (mCheckedSims.size() + 24) / 25), maximumPoolSize);
            }
            fixCorePoolSize();
        }
    }

    private void fixCorePoolSize() {
        ThreadPoolExecutor executor = routersExecutor;
        if (executor != null
                && executor.allowsCoreThreadTimeOut() // corePool нитям всё ещё разрешено умирать чтоб умирали стартовые доп нити
                && corePoolSize < maximumPoolSize // corePool был заужен после буста на старте, это второй+ тик
                && !executor.getQueue().isEmpty() // очередь не пуста при зауженном пуле, значит стартовые доп нити убиты (или будут по тайм-ауту) и задачи идут в очередь или задач слишком много и все пашут
                && executor.getActiveCount() <= corePoolSize) { // работающих нитей <= зауженного corePool при не пустой очереди, значит стартовые доп нити точно убиты, задачи идут в очередь
            executor.allowCoreThreadTimeOut(false);
        }
    }

    private void updateRoutersData(SimCardData simCard) {
        ThreadPoolExecutor executor = routersExecutor;
        if (executor != null && simCard instanceof SimCardDataRouter router) {
            if (executor.getActiveCount() == maximumPoolSize && executor.getQueue().size() == executorPoolQueueSize) {
                return;
            }

            if (executor.getCorePoolSize() == maximumPoolSize && corePoolSize < maximumPoolSize) {
                executor.setCorePoolSize(corePoolSize);
            }

            try {
                Log.i(AppController.LOG_TAG, "отправляется задача, нитей = " + executor.getActiveCount() +
                        " в очереди = " + executor.getQueue().size() + " из " + executorPoolQueueSize + " кор нити = " + corePoolSize);
                executor.execute(() -> {
                    try {
                        router.updateData();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Log.d(AppController.LOG_TAG, "InterruptedException  нити экзекутора!!!!!");
                    }
                });

            } catch (Exception e) {
                Log.d(AppController.LOG_TAG, "routersExecutor already shutdown");
            }
        }
    }

    private void stopWriters() {
        if (mCheckedSims != null && !mCheckedSims.isEmpty()) {
            for (SimCardData simCard : mCheckedSims) {
                if (simCard != null) {
                    simCard.stopWriter();
                    Log.d(AppController.LOG_TAG, " остановка файлрайтера нитью " + Thread.currentThread().getName());
                }
            }
            mCheckedSims.clear();
        }
    }

    private void setSimListener(SimCardData simCard) {
        if (mHandler != null) {
            mHandler.post(() -> {
                Map.Entry<TelephonyManager, SimListener> entry = mSimListeners.get(simCard);
                if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                    Log.d(AppController.LOG_TAG, "Запущен в нэндлере регистрация слушателя для симок " + simCard.getOperator() + " " + Thread.currentThread().getName());
                    TelephonyManager simManager = mTelephonyManager.createForSubscriptionId(simCard.getSubscriptionId());
                    SimListener simListener = new SimListener(simCard, simManager, this);
                    entry = new AbstractMap.SimpleEntry<>(simManager, simListener);
                    int flags = PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                            | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        flags |= PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED;
                    }

                    simManager.listen(simListener, flags);
                    mSimListeners.put(simCard, entry);
                }
            });
        }
    }

    private void initResources() {
        if (powerManager == null || mWakeLock == null) {
            powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null && mWakeLock == null) {
                mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOGTAG);
            }
        }

        if (mTelephonyManager == null) {
            mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        }

        if (mHandlerThread == null || !mHandlerThread.isAlive()) {
            mHandlerThread = new HandlerThread(LISTENER_THREAD);
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());
        }

        if (mCheckedSims == null) {
            mCheckedSims = ((AppController) getApplication()).getCheckedSims();
            corePoolSize = maximumPoolSize = Math.max(1, Math.min(mCheckedSims.size(), 32));
            executorPoolQueueSize = Math.max(1, mCheckedSims.size() * 2);
        }

        if (routersExecutor == null) {
            routersExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, SimCardDataRouter.FULL_TIMEOUT, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(executorPoolQueueSize), lowPriorityFactory);
            routersExecutor.allowCoreThreadTimeOut(true);
        }

        if (mStartCollection == null) {
            mStartCollection = LocalDateTime.now();
        }
    }

    private void getWakeLock() {
        try {
            if (mWakeLock != null && !mWakeLock.isHeld()) {
                mWakeLock.acquire();
                Log.w(AppController.LOG_TAG, "mWakeLock.acquire() " + Thread.currentThread().getName());
            }
        } catch (Exception e) {
            Log.w(AppController.LOG_TAG, "mWakeLock.acquire() = Ошибка " + Thread.currentThread().getName());
        }

    }

    private void releaseWakeLock() {
        try {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
                Log.w(AppController.LOG_TAG, "mWakeLock.release() " + Thread.currentThread().getName());
            }
        } catch (Exception e) {
            Log.w(AppController.LOG_TAG, "mWakeLock.release() = Ошибка " + Thread.currentThread().getName());
        }
    }

    private void registerScreenListener() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        try {
            registerReceiver(screenReceiver, filter);
        } catch (Exception e) {
            Log.w(AppController.LOG_TAG, "registerScreenListener = Ошибка " + Thread.currentThread().getName());
            getWakeLock();
        }
    }

    private void unregisterScreenListener() {
        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception e) {
            Log.w(AppController.LOG_TAG, "unregisterScreenListener = Ошибка " + Thread.currentThread().getName());
        }
    }
}