package com.safelogj.simlog.collecting;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.safelogj.simlog.AdsId;
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
        mCheckedSims = mController.getCheckedSims();

        mBinding.collectButton.setOnClickListener(view -> {
            mController.stopCollecting();
            finish();

        });

        mBinding.collectTextView1.setText(R.string.collecting_time);
        mBinding.collectTextView2.setText(R.string.collecting_write_lines);
        mBinding.collectTextView3.setText(R.string.collecting_errors);

        mBinding.lottieView.addAnimatorListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationRepeat(Animator animation) {
                writeInfo();
            }
        });
        mBinding.lottieView.setOnClickListener(view -> {
            mController.stopCollecting();
            finish();
        });


    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mController.isAllowAds()) {
            NativeAd nativeAd = mController.getNativeAd(AdsId.COLLECT_ACT_1.getId());
            if (nativeAd != null) mBinding.collectNativeBanner.setAd(nativeAd);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (mController.isAllowAds()) mController.loadNativeAd(AdsId.COLLECT_ACT_1.getId());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("time", mBinding.collectTextView1.getText().toString());
        outState.putString("lines", mBinding.collectTextView2.getText().toString());
        outState.putString("errors", mBinding.collectTextView3.getText().toString());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mBinding.collectTextView1.setText(savedInstanceState.getString("time"));
        mBinding.collectTextView2.setText(savedInstanceState.getString("lines"));
        mBinding.collectTextView3.setText(savedInstanceState.getString("errors"));
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        Toast.makeText(CollectActivity.this, getString(R.string.stop_collect), Toast.LENGTH_LONG).show();
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
        Duration duration = Duration.between(mController.getStartCollection(), now).abs();

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
        }

    }
}
