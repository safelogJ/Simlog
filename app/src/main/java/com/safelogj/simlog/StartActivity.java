package com.safelogj.simlog;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.safelogj.simlog.databinding.ActivityStartBinding;
import com.safelogj.simlog.helpers.PrivacyActivity;


public class StartActivity extends AppCompatActivity {

    private static final String TEXT_VIEW_0_KEY = "text_view0_key";
    private static final int PERMIT_TEXT = 1;
    private static final int NOTICE_TEXT = 2;
    private static final String PACKAGE = "package";
    private ActivityStartBinding mBinding;
    private int mTextViewNumber = PERMIT_TEXT;
    private int mPermissionCounter;
    private AppController mController;
    private final ActivityResultCallback<Boolean> requestDisplayFiles = isGranted -> {
        if (Boolean.TRUE == isGranted) {
            startActivity(new Intent(this, ChooseSimActivity.class));
        } else {
            permissionCount();
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
        initStartOkBtn();
        setTextView0ByNumber(mTextViewNumber);
        mBinding.startYoutubeButton.setOnClickListener(view -> openYoutubeLink());
        mBinding.startTextView0.setMovementMethod(LinkMovementMethod.getInstance());

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
            if (view == mBinding.startPermitButton) setTextView0ByNumber(PERMIT_TEXT);
            if (view == mBinding.startNoticeButton) setTextView0ByNumber(NOTICE_TEXT);

            if (view == mBinding.startPrivacyButton)
                startActivity(new Intent(this, PrivacyActivity.class));
        }
    }

    private void setTextView0ByNumber(int number) {
        mTextViewNumber = number;
        if (number == NOTICE_TEXT) {
            mBinding.startTextView0.setText(HtmlCompat.fromHtml(getString(R.string.notice), HtmlCompat.FROM_HTML_MODE_LEGACY));
        } else {
            mBinding.startTextView0.setText(HtmlCompat.fromHtml(getString(R.string.permit), HtmlCompat.FROM_HTML_MODE_LEGACY));
        }
    }

    private void permissionCount() {
        mPermissionCounter++;
        if (mPermissionCounter > 2) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts(PACKAGE, getPackageName(), null));
            startActivity(intent);
            mPermissionCounter = 0;
        } else {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show();
        }
    }

    private void initStartOkBtn() {
        mBinding.startOkButton.setOnClickListener(view -> {
            if (mController.getPrivacyId() < PrivacyActivity.CURRENT_PRIVACY_ID) {
                startActivity(new Intent(this, PrivacyActivity.class));
            } else {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionLauncher.launch(android.Manifest.permission.READ_PHONE_STATE);
                    return;
                }
                startActivity(new Intent(this, ChooseSimActivity.class));
            }
        });
    }

    private void setLightStatusBar() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
      //  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.main_background));
        //}
    }

    private void openYoutubeLink() {
        try {
            Uri webpage = Uri.parse("https://www.youtube.com/watch?v=eVcEH2mbUHk&list=PL5Ch75WcmOXSz-TVd8ihTaQO3eE4FP1MI&index=1");
            Intent intent = new Intent(Intent.ACTION_VIEW, webpage);
            startActivity(intent);
        } catch (Exception e) {
            //
        }
    }
}
