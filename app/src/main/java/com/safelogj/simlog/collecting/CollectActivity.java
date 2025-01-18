package com.safelogj.simlog.collecting;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.safelogj.simlog.StartActivity;
import com.safelogj.simlog.helpers.AdsId;
import com.safelogj.simlog.AppController;
import com.safelogj.simlog.R;
import com.safelogj.simlog.databinding.ActivityCollectBinding;
import com.yandex.mobile.ads.nativeads.NativeAd;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class CollectActivity extends AppCompatActivity {
    private ActivityCollectBinding mBinding;
    private AppController mController;
    private List<SimCard> mCheckedSims;
    private AnimatorListenerAdapter mAnimatorListener;
    private NativeAd mNativeAd;
    private LogWriteService mLogWriteService;

    private final ServiceConnection mLogWriteServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LogWriteService.LocalBinder binder = (LogWriteService.LocalBinder) service;
            mLogWriteService = binder.getLogWriteService();
            mCheckedSims = mLogWriteService.getCheckedSims();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            //
        }
    };

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        if (mCheckedSims == null || mCheckedSims.isEmpty()) {
            stopService(new Intent(this, LogWriteService.class));
            startStartActivity();
        } else {
            Toast.makeText(CollectActivity.this, getString(R.string.stop_collect), Toast.LENGTH_LONG).show();
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityCollectBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        mController = (AppController) getApplication();
        mBinding.collectBatteryButton.setOnClickListener(view -> fixBattery());
        mBinding.collectNotificButton.setOnClickListener(view -> askNotification());

        mBinding.collectTextView1.setText(R.string.collecting_time);
        mBinding.collectTextView2.setText(R.string.collecting_write_lines);
        mBinding.collectTextView3.setText(R.string.collecting_errors);

        mBinding.lottieView.setOnClickListener(view -> {
            stopService(new Intent(this, LogWriteService.class));
            startStartActivity();
        });

        mAnimatorListener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationRepeat(Animator animation) {
                writeInfo();
                checkButtonsColor();
            }
        };
        mBinding.lottieView.addAnimatorListener(mAnimatorListener);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, LogWriteService.class);
        bindService(intent, mLogWriteServiceConnection, Context.BIND_AUTO_CREATE);
        checkButtonsColor();

        mNativeAd = mController.peekNativeAd(AdsId.COLLECT_ACT_1.getId());
        if (mController.isAllowAds() && mNativeAd != null)
            mBinding.collectNativeBanner.setAd(mNativeAd);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (mController.isAllowAds())
            mNativeAd = mController.pollNativeAd(AdsId.COLLECT_ACT_1.getId());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("time", mBinding.collectTextView1.getText().toString());
        outState.putString("lines", mBinding.collectTextView2.getText().toString());
        outState.putString("errors", mBinding.collectTextView3.getText().toString());
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(mLogWriteServiceConnection);
        if (mController.isAllowAds()) mController.loadNativeAd();
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mBinding.collectTextView1.setText(savedInstanceState.getString("time"));
        mBinding.collectTextView2.setText(savedInstanceState.getString("lines"));
        mBinding.collectTextView3.setText(savedInstanceState.getString("errors"));
    }


    private void setInfoText(int lines, int errors) {
        String time = getString(R.string.collecting_time) + calculateWorkTime();
        String writeLines = getString(R.string.collecting_write_lines) + " " + lines;
        String err = getString(R.string.collecting_errors) + " " + errors;
        mBinding.collectTextView1.setText(time);
        mBinding.collectTextView2.setText(writeLines);
        mBinding.collectTextView3.setText(err);
    }

    private String calculateWorkTime() {
        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(mLogWriteService.getStartCollection(), now).abs();

        short days = (short) duration.toDays();
        duration = duration.minusDays(days);

        byte hours = (byte) duration.toHours();
        duration = duration.minusHours(hours);

        byte minutes = (byte) duration.toMinutes();
        duration = duration.minusMinutes(minutes);

        byte seconds = (byte) duration.getSeconds();

        StringBuilder result = new StringBuilder(" ");

        if (days > 0)
            result.append(days).append(ContextCompat.getString(this, R.string.collecting_day)).append(" ");
        if (hours > 0)
            result.append(hours).append(ContextCompat.getString(this, R.string.collecting_hour)).append(" ");
        if (minutes > 0)
            result.append(minutes).append(ContextCompat.getString(this, R.string.collecting_min)).append(" ");
        if (seconds > 0)
            result.append(seconds).append(ContextCompat.getString(this, R.string.collecting_sec));

        return result.toString();
    }

    private void writeInfo() {
        int lines = 0;
        int errors = 0;
        if (mCheckedSims != null && !mCheckedSims.isEmpty()) {
            for (SimCard simCard : mCheckedSims) {
                lines += simCard.getWriteLines();
                errors += simCard.getWriteErrors();
            }
            setInfoText(lines, errors);
        } else {
            setErrorText();
            mBinding.lottieView.cancelAnimation();
        }

    }

    private void setErrorText() {
        String writeLines =" " + getString(R.string.service_stopped);
        mBinding.collectTextView1.setText("");
        mBinding.collectTextView2.setText(writeLines);
        mBinding.collectTextView3.setText("");
    }

    private void fixBattery() {
        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        startActivity(intent);
    }

    private void askNotification() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }

    private void checkButtonsColor() {
        PowerManager mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        NotificationManagerCompat mNotificationManagerCompat = NotificationManagerCompat.from(this);
        ColorStateList mBtnBackColorGreen = getResources().getColorStateList(R.color.green_600, null);
        ColorStateList mBtnBackColorBlack = getResources().getColorStateList(R.color.black3, null);
        ColorStateList mBtnRipleColorGreen = getResources().getColorStateList(R.color.green_100, null);
        ColorStateList mBtnRipleColorBlack = getResources().getColorStateList(R.color.spinner_font, null);

        if (mPowerManager != null && mPowerManager.isIgnoringBatteryOptimizations(getPackageName())) {
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

    private void startStartActivity() {
        Intent intent = new Intent(this, StartActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
