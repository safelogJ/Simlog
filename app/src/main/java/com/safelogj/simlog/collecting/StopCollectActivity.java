package com.safelogj.simlog.collecting;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.airbnb.lottie.LottieDrawable;
import com.safelogj.simlog.AppController;
import com.safelogj.simlog.R;
import com.safelogj.simlog.StartActivity;
import com.safelogj.simlog.databinding.ActivityCollectBinding;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class StopCollectActivity extends AppCompatActivity {

    private static final String SPACE = " ";
    private static final String EMPTY_STRING = "";
    private static final String LINES = "lines";
    private static final float LOTTIE_TIK_PROGRESS = 0.04f;
    private ActivityCollectBinding mBinding;
    private AppController mController;

    private final ActivityResultCallback<ActivityResult> callbackForGeneralPermitURI = result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Uri uri = result.getData().getData();
            if (uri != null) {
                final int takeFlags = (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                try {
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                    Log.d(AppController.LOG_TAG, "Разрешение на URI сохранено: " + uri);
                } catch (SecurityException e) {
                    Log.d(AppController.LOG_TAG, "Ошибка получения разрешений на URI: " + e.getMessage(), e);
                }
                try {
                    ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
                    Bitmap bitmap = ImageDecoder.decodeBitmap(source);
                    mController.setUserCollectImage(bitmap);
                    setUserImage();
                } catch (IOException e) {
                    Log.d(AppController.LOG_TAG, "Ошибка чтения картинки: " + e.getMessage(), e);
                }
            }
        }
    };

    private final ActivityResultLauncher<Intent> requestGeneralPermitURI =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), callbackForGeneralPermitURI);
    private final ActivityResultCallback<Boolean> callbackAskReadImagePermit = result -> {
        if (Boolean.TRUE == result) {
            requestGeneralPermitURI.launch(getIntentActionOpenImage());
        }
    };
    private final ActivityResultLauncher<String> requestAskReadImagePermit =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), callbackAskReadImagePermit);

    ScheduledExecutorService drawStatExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> mDrawStatsTask;
    private float lottieProgress;
    private boolean isLottieTikCancel;
    private PowerManager mPowerManager;
    private NotificationManagerCompat mNotificationManagerCompat;
    private ColorStateList mBtnBackColorGreen;
    private ColorStateList mBtnBackColorBlack;
    private ColorStateList mBtnRipleColorGreen;
    private ColorStateList mBtnRipleColorBlack;

    public void collectBtnListener(View view) {
        startActivity(new Intent(this, StartActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        mBinding = ActivityCollectBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (v, insets) -> {
            Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets gestureInsets = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures());
            int leftPadding = Math.max(gestureInsets.left, systemInsets.left);
            int rightPadding = Math.max(gestureInsets.right, systemInsets.right);
            int bottomPadding = Math.max(gestureInsets.bottom, systemInsets.bottom);
            int leftPaddingLand = Math.max(leftPadding, systemInsets.top);
            int rightPaddingLand = Math.max(rightPadding, systemInsets.top);

            if (v.getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                v.setPadding(leftPaddingLand, systemInsets.top, rightPaddingLand, bottomPadding);
            } else {
                v.setPadding(leftPadding, systemInsets.top, rightPadding, bottomPadding);
            }
            return WindowInsetsCompat.CONSUMED;
        });
        setLightStatusBar();
        mController = (AppController) getApplication();
        mBinding.collectBatteryButton.setOnClickListener(view -> fixBattery());
        mBinding.collectNotificButton.setOnClickListener(view -> askNotification());
        mBinding.collectOverlayButton.setOnLongClickListener(v -> {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestAskReadImagePermit.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                return true;
            }
            requestGeneralPermitURI.launch(getIntentActionOpenImage());
            return true;
        });

        mPowerManager = mController.getPowerManager();
        mNotificationManagerCompat = mController.getNotificationManagerCompat();
        mBtnBackColorGreen = mController.getBtnBackColorGreen();
        mBtnBackColorBlack = mController.getBtnBackColorBlack();
        mBtnRipleColorGreen = mController.getBtnRipleColorGreen();
        mBtnRipleColorBlack = mController.getBtnRipleColorBlack();

    }

    @Override
    protected void onStart() {
        super.onStart();
        checkButtonsColor();
        mBinding.lottieView.playAnimation();
        setUserImage();
        startDrawStats();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(LINES, mBinding.collectTextView2.getText().toString());
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopDrawStats();
        mBinding.lottieView.cancelAnimation();
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mBinding.collectTextView2.setText(savedInstanceState.getString(LINES));
    }


    private void writeInfo() {
            if (lottieProgress == -1f) {
                float startLottieProgress = mBinding.lottieView.getProgress();
                lottieProgress = startLottieProgress > 0.9 ? startLottieProgress - LOTTIE_TIK_PROGRESS : startLottieProgress;
            }

            if (!isLottieTikCancel) {
                mBinding.lottieView.cancelAnimation();
                isLottieTikCancel = true;
                mBinding.collectPowerButton.getDrawable().setTint(getColor(R.color.main_background));
            } else {
                mBinding.collectPowerButton.getDrawable().setTint(getColor(R.color.error_color));
                mBinding.lottieView.setRepeatMode(LottieDrawable.REVERSE);
                mBinding.lottieView.setMinAndMaxProgress(lottieProgress, lottieProgress + LOTTIE_TIK_PROGRESS);
                mBinding.lottieView.setSpeed(0.42f);
                mBinding.lottieView.playAnimation();
                isLottieTikCancel = false;
            }

        checkButtonsColor();
    }

    private void setErrorText() {
        String writeLines = SPACE + getString(R.string.service_stopped);
        mBinding.collectTextView2.setText(writeLines);
    }

    private void fixBattery() {
        if (isBatterySettingsAvailable()) {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(intent);
        }
    }

    private void askNotification() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }

    private void checkButtonsColor() {
        if ((mPowerManager != null && mPowerManager.isIgnoringBatteryOptimizations(getPackageName()))
                || !isBatterySettingsAvailable()) {
            mBinding.collectBatteryButton.setBackgroundTintList(mBtnBackColorGreen);
            mBinding.collectBatteryButton.setRippleColor(mBtnRipleColorGreen);
        } else {
            mBinding.collectBatteryButton.setBackgroundTintList(mBtnBackColorBlack);
            mBinding.collectBatteryButton.setRippleColor(mBtnRipleColorBlack);
        }
        if (mNotificationManagerCompat.areNotificationsEnabled()) {
            mBinding.collectNotificButton.setBackgroundTintList(mBtnBackColorGreen);
            mBinding.collectNotificButton.setRippleColor(mBtnRipleColorGreen);
        } else {
            mBinding.collectNotificButton.setBackgroundTintList(mBtnBackColorBlack);
            mBinding.collectNotificButton.setRippleColor(mBtnRipleColorBlack);
        }
    }

    private void stopDrawStats() {
        if (mDrawStatsTask != null && !mDrawStatsTask.isCancelled()) {
            mDrawStatsTask.cancel(true);
        }
    }

    private void startDrawStats() {
        lottieProgress = -1f;
        mDrawStatsTask = drawStatExecutor.scheduleWithFixedDelay(() -> runOnUiThread((this::writeInfo)), 0, 1, TimeUnit.SECONDS);
        setErrorText();
    }

    private void setLightStatusBar() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
        // if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.main_background));
        // }
    }

    private boolean isBatterySettingsAvailable() {
        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        List<ResolveInfo> resolveInfos = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfos.isEmpty()) {
            return false;
        }
        for (ResolveInfo info : resolveInfos) {
            String packageName = info.activityInfo != null ? info.activityInfo.packageName : EMPTY_STRING;
            String className = info.activityInfo != null ? info.activityInfo.name : EMPTY_STRING;
            String combined = (packageName + className).toLowerCase(Locale.ROOT);
            if (!combined.contains("stub")) {
                return true;
            }
        }
        return false;
    }

    private void setUserImage() {
        Bitmap userImage = mController.getUserCollectImage();
        if (userImage != null) {
            mBinding.collectImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            mBinding.collectImageView.setImageBitmap(userImage);
        } else {
            mBinding.collectImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mBinding.collectImageView.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.no_image));
        }
    }

    private Intent getIntentActionOpenImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*"); // теперь только картинки
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        return intent;
    }
}