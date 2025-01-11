package com.safelogj.simlog;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.text.HtmlCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.safelogj.simlog.databinding.ActivityStartBinding;
import com.safelogj.simlog.helpers.AdsIdActivity;

public class StartActivity extends AppCompatActivity {

    private static final String TEXT_VIEW_0_KEY = "text_view0_key";
    private ActivityStartBinding mBinding;
    private int mTextViewNumber = 1;
    private AppController mController;
    private final ActivityResultCallback<Boolean> requestDisplayFiles = isGranted -> {
        if (Boolean.TRUE == isGranted) {
            startActivity(new Intent(this, ChooseSimActivity.class));
        } else {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show();
        }
    };
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), requestDisplayFiles);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityStartBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        mController = (AppController) getApplication();


        mBinding.startOkButton.setOnClickListener(view -> {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.READ_PHONE_STATE);
                return;
            }
            startActivity(new Intent(this, ChooseSimActivity.class));
        });

        setTextView0ByNumber(mTextViewNumber);
        mBinding.startTextView0.setMovementMethod(LinkMovementMethod.getInstance());


        if (mController.isAllowAds()) {
            mBinding.switchAds.setChecked(mController.isAllowAds());
            mBinding.switchAds.setTextColor(getResources().getColor(R.color.blue_500, getTheme()));
        }

        mBinding.switchAds.setOnClickListener(view -> {
            if (mBinding.switchAds.isChecked()) {
                mController.setAllowAds(true);
                mController.writeSetting();
                mBinding.switchAds.setTextColor(getResources().getColor(R.color.blue_500, getTheme()));
                startActivity(new Intent(this, AdsIdActivity.class));

            } else  {
                mBinding.switchAds.setTextColor(getResources().getColor(R.color.black2, getTheme()));
                mController.setAllowAdId(false);
                mController.setAllowAds(false);
                mController.writeSetting();
            }
        });

    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(TEXT_VIEW_0_KEY, mTextViewNumber);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
       setTextView0ByNumber(savedInstanceState.getInt(TEXT_VIEW_0_KEY));
    }

    public void setTextView0ByClick(View view) {
        if (view != null) {
            if (view == mBinding.startPermitButton) setTextView0ByNumber(1);
            if (view == mBinding.startNoticeButton) setTextView0ByNumber(2);
            if (view == mBinding.startPrivacyButton) setTextView0ByNumber(3);
        }
    }
    private void setTextView0ByNumber(int number) {
        mTextViewNumber = number;
        switch (number) {
            case 2:  mBinding.startTextView0.setText(HtmlCompat.fromHtml(getString(R.string.notice), HtmlCompat.FROM_HTML_MODE_LEGACY)); break;
            case 3:  mBinding.startTextView0.setText(HtmlCompat.fromHtml(getString(R.string.privacy), HtmlCompat.FROM_HTML_MODE_LEGACY)); break;
            default:  mBinding.startTextView0.setText(HtmlCompat.fromHtml(getString(R.string.permit), HtmlCompat.FROM_HTML_MODE_LEGACY));
        }
    }
}
