package com.safelogj.simlog.collecting;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
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
import com.safelogj.simlog.StartActivity;
import com.safelogj.simlog.AppController;
import com.safelogj.simlog.R;
import com.safelogj.simlog.databinding.ActivityCollectBinding;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CollectActivity extends AppCompatActivity {
    private static final String SPACE = " ";
    private static final String EMPTY_STRING = "";
    private static final String TIME = "time";
    private static final String LINES = "lines";
    private static final String ERRORS = "errors";
    private static final String ROUTERS_ERRORS = "routersErrors";
    private static final float LOTTIE_TIK_PROGRESS = 0.04f;
    private ActivityCollectBinding mBinding;
    private AppController mController;
    private List<SimCardData> mCheckedSims;
    private LogWriteService mLogWriteService;
    private LocalDateTime startCollecting;
    private final ServiceConnection mLogWriteServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LogWriteService.LocalBinder binder = (LogWriteService.LocalBinder) service;
            mLogWriteService = binder.getLogWriteService();
            mCheckedSims = mLogWriteService.getCheckedSims();
            startCollecting = mLogWriteService.getStartCollection();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            //
        }
    };
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
    private int drawTaskCounter;
    private float lottieProgress;
    private boolean isLottieTikCancel;
    private PowerManager mPowerManager;
    private NotificationManagerCompat mNotificationManagerCompat;
    private ColorStateList mBtnBackColorGreen;
    private ColorStateList mBtnBackColorBlack;
    private ColorStateList mBtnRipleColorGreen;
    private ColorStateList mBtnRipleColorBlack;

    public void collectBtnListener(View view) {
        stopService(new Intent(this, LogWriteService.class));
        stopCollectActivity();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!isTaskRoot() && getIntent().hasCategory(Intent.CATEGORY_LAUNCHER) && Intent.ACTION_MAIN.equals(getIntent().getAction())) {
            Log.d(AppController.LOG_TAG, "обнаружен дубликат при запуске с иконки.");
            finish();
            return;
        }

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
        Intent intent = new Intent(this, LogWriteService.class);
        bindService(intent, mLogWriteServiceConnection, Context.BIND_AUTO_CREATE);
        checkButtonsColor();
        mBinding.lottieView.playAnimation();
        setUserImage();
        startDrawStats();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(TIME, mBinding.collectTextView1.getText().toString());
        outState.putString(LINES, mBinding.collectTextView2.getText().toString());
        outState.putString(ERRORS, mBinding.collectTextView3.getText().toString());
        outState.putString(ROUTERS_ERRORS, mBinding.collectTextView4.getText().toString());
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(mLogWriteServiceConnection);
        stopDrawStats();
        mBinding.lottieView.cancelAnimation();
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mBinding.collectTextView1.setText(savedInstanceState.getString(TIME));
        mBinding.collectTextView2.setText(savedInstanceState.getString(LINES));
        mBinding.collectTextView3.setText(savedInstanceState.getString(ERRORS));
        mBinding.collectTextView4.setText(savedInstanceState.getString(ROUTERS_ERRORS));
    }


    private void setInfoText() {
        String time = getString(R.string.collecting_time) + calculateWorkTime();
        mBinding.collectTextView1.setText(time);
        if (drawTaskCounter > 0) {
            drawTaskCounter--;
        } else {
            drawTaskCounter = 30;
            int lines = 0;
            int errors = 0;
            String routersErr = AppController.EMPTY_STRING;
            for (SimCardData simCard : mCheckedSims) {
                lines += simCard.getWriteLines();
                errors += simCard.getWriteErrors();
                routersErr = gerRouterErr(simCard, routersErr);

            }
            String writeLines = getString(R.string.collecting_write_lines) + SPACE + lines;
            String err = getString(R.string.collecting_errors) + SPACE + errors + SPACE;
            mBinding.collectTextView2.setText(writeLines);
            mBinding.collectTextView3.setText(err);
            mBinding.collectTextView4.setText(routersErr);
        }
    }

    private String gerRouterErr(SimCardData simCard, String err) {
        if (simCard instanceof SimCardDataRouter router && router.getRouterManager() == null && router.getWriteErrors() > 0) {
            err += SPACE + router.getAddress() + SPACE;
        }
        return err;
    }


    private String calculateWorkTime() {
        if (startCollecting == null) return EMPTY_STRING;

       Duration duration = Duration.between(startCollecting, LocalDateTime.now()).abs();

        short days = (short) duration.toDays();
        duration = duration.minusDays(days);

        byte hours = (byte) duration.toHours();
        duration = duration.minusHours(hours);

        byte minutes = (byte) duration.toMinutes();
        duration = duration.minusMinutes(minutes);

        byte seconds = (byte) duration.getSeconds();

        StringBuilder result = new StringBuilder(SPACE);

        if (days > 0)
            result.append(days).append(ContextCompat.getString(this, R.string.collecting_day)).append(SPACE);
        if (hours > 0)
            result.append(hours).append(ContextCompat.getString(this, R.string.collecting_hour)).append(SPACE);
        if (minutes > 0)
            result.append(minutes).append(ContextCompat.getString(this, R.string.collecting_min)).append(SPACE);
        if (seconds > 0)
            result.append(seconds).append(ContextCompat.getString(this, R.string.collecting_sec));

        return result.toString();
    }

    private void writeInfo() {
        if (mCheckedSims != null && !mCheckedSims.isEmpty()) {
            setInfoText();
        } else {
            if (lottieProgress == -1f) {
                setErrorText();
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
        }
        checkButtonsColor();
        setUserImage();
    }

    private void setErrorText() {
        String writeLines = SPACE + getString(R.string.service_stopped);
        mBinding.collectTextView1.setText(EMPTY_STRING);
        mBinding.collectTextView2.setText(writeLines);
        mBinding.collectTextView3.setText(EMPTY_STRING);
        mBinding.collectTextView4.setText(EMPTY_STRING);
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

    private void stopCollectActivity() {
        startActivity(new Intent(this, StopCollectActivity.class));
        startActivity(new Intent(this, StartActivity.class));
        finish();
    }

    private void stopDrawStats() {
        if (mDrawStatsTask != null && !mDrawStatsTask.isCancelled()) {
            mDrawStatsTask.cancel(true);
        }
    }

    private void startDrawStats() {
        drawTaskCounter = 0;
        lottieProgress = -1f;
        mDrawStatsTask = drawStatExecutor.scheduleWithFixedDelay(() -> runOnUiThread((this::writeInfo)), 2, 1, TimeUnit.SECONDS);
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
