package com.safelogj.simlog;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.safelogj.simlog.collecting.SimCard;
import com.safelogj.simlog.helpers.AdsId;
import com.yandex.mobile.ads.common.AdRequestError;
import com.yandex.mobile.ads.common.ImpressionData;
import com.yandex.mobile.ads.common.MobileAds;
import com.yandex.mobile.ads.nativeads.NativeAd;
import com.yandex.mobile.ads.nativeads.NativeAdEventListener;
import com.yandex.mobile.ads.nativeads.NativeAdLoadListener;
import com.yandex.mobile.ads.nativeads.NativeAdLoader;
import com.yandex.mobile.ads.nativeads.NativeAdRequestConfiguration;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class AppController extends Application {
    public static final String NOTIFICATION_CHANNEL_ID = "Notification_CHANNEL_ID";
    private List<SimCard> mCheckedSims;
    private File mExternalFileDir;
    private final Map<String, Path> mFilesPaths = new HashMap<>();
    private boolean mAllowAdId;
    private boolean mAllowAds;
    private final HashMap<String, NativeAdLoader> mNativeLoaders = new HashMap<>();
    private final HashMap<String, LinkedList<NativeAd>> mNativeAdsQueues = new HashMap<>();
    private int mAdQueueSize = 2;

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

    public boolean isAllowAdId() {
        return mAllowAdId;
    }

    public void setAllowAdId(boolean allowAdId) {
        this.mAllowAdId = allowAdId;
    }

    public boolean isAllowAds() {
        return mAllowAds;
    }

    public void setAllowAds(boolean allowAds) {
        this.mAllowAds = allowAds;
    }

    public NativeAd peekNativeAd(String adUnitId) {
        LinkedList<NativeAd> listAd = mNativeAdsQueues.get(adUnitId);
        return listAd == null ? null : listAd.peek();
    }

    public NativeAd pollNativeAd(String adUnitId) {
        LinkedList<NativeAd> listAd = mNativeAdsQueues.get(adUnitId);
        return listAd == null ? null : listAd.poll();
    }

    public void loadNativeAd() {
        MobileAds.setUserConsent(isAllowAdId());
        for (AdsId id : AdsId.values()) {
            String adsId = id.getId();
            LinkedList<NativeAd> queue = mNativeAdsQueues.get(adsId);
            NativeAdLoader loader = mNativeLoaders.get(adsId);
            if (queue != null && loader != null) {
                int queueSize = queue.size();
                for (int i = 0; i < mAdQueueSize - queueSize; i++) {
                    loader.loadAd(new NativeAdRequestConfiguration.Builder(adsId).build());
                }
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mExternalFileDir = getExternalFilesDir(null);
        SimCard.setExternalFileDir(mExternalFileDir);
        createNotificationChannel();
        readSettings();

        MobileAds.setAgeRestrictedUser(true);
        MobileAds.setUserConsent(isAllowAdId());
        MobileAds.initialize(this, () -> {
        });
        initNativeLoaders();
        if (isAllowAds()) loadNativeAd();
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

    public void writeSetting() {
        JSONObject settingsJson = new JSONObject();
        try {
            settingsJson.put("mAllowAdId", mAllowAdId);
            settingsJson.put("mAllowAds", mAllowAds);

            File settingsDir = new File(mExternalFileDir, "settings");
            if (!settingsDir.exists()) {
                settingsDir.mkdirs();
            }
            File settingsFile = new File(settingsDir, "ads.txt");
            try (FileOutputStream fos = new FileOutputStream(settingsFile)) {
                fos.write(settingsJson.toString().getBytes());
                fos.flush();
            }

        } catch (JSONException | IOException e) {
            Log.e("MyApplication", "Ошибка при сохранении настроек: " + e.getMessage());
        }

    }

    private void readSettings() {
        File settingsDir = new File(mExternalFileDir, "settings");
        File settingsFile = new File(settingsDir, "ads.txt");

        if (settingsFile.exists() && settingsFile.isFile()) {
            try (FileInputStream fis = new FileInputStream(settingsFile)) {
                byte[] data = new byte[(int) settingsFile.length()];
                fis.read(data);
                String jsonStr = new String(data);
                JSONObject settingsJson = new JSONObject(jsonStr);
                mAllowAdId = settingsJson.optBoolean("mAllowAdId", false);
                mAllowAds = settingsJson.optBoolean("mAllowAds", false);
            } catch (IOException | JSONException e) {
                Log.e("MyApplication", "Ошибка при загрузке настроек: " + e.getMessage());
            }
        }
    }

    private static class NativeAdEventLogger implements NativeAdEventListener {
        @Override
        public void onAdClicked() {
            // Called when a click is recorded for an ad.
        }

        @Override
        public void onLeftApplication() {
            // Called when user is about to leave application (e.g., to go to the browser), as a result of clicking on the ad.
        }

        @Override
        public void onReturnedToApplication() {
            // Called when user returned to application after click.
        }

        @Override
        public void onImpression(ImpressionData impressionData) {
            // Called when an impression is recorded for an ad.
        }
    }

    private void initNativeLoaders() {
        for (AdsId id : AdsId.values()) {
            final NativeAdLoader newNativeAdLoader = new NativeAdLoader(this);
            newNativeAdLoader.setNativeAdLoadListener(new NativeAdLoadListener() {
                @Override
                public void onAdLoaded(@NonNull final NativeAd nativeAd) {
                    nativeAd.setNativeAdEventListener(new NativeAdEventLogger());
                    LinkedList<NativeAd> listAd = mNativeAdsQueues.get(id.getId());
                    if (listAd != null) {
                        listAd.add(nativeAd);
                        mAdQueueSize = 2;
                    }

                }

                @Override
                public void onAdFailedToLoad(@NonNull final AdRequestError error) {
                    mAdQueueSize = 3;
                }
            });
            mNativeLoaders.put(id.getId(), newNativeAdLoader);
            mNativeAdsQueues.put(id.getId(), new LinkedList<>());
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

}
