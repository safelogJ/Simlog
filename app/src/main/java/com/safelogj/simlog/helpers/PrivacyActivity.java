package com.safelogj.simlog.helpers;

import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.text.HtmlCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.CompoundButtonCompat;

import com.safelogj.simlog.AppController;
import com.safelogj.simlog.R;
import com.safelogj.simlog.databinding.ActivityPrivacyBinding;

public class PrivacyActivity extends AppCompatActivity {

    public static final int CURRENT_PRIVACY_ID = 3;
    private static final String CHECK_BOX_KEY = "checkBoxKey";
    private ActivityPrivacyBinding mBinding;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityPrivacyBinding.inflate(getLayoutInflater());
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
        AppController appController = (AppController) getApplication();
        ColorStateList checkBoxTintList = ContextCompat.getColorStateList(this, R.color.checkbox_backgrount_selector);
        CompoundButtonCompat.setButtonTintList(mBinding.privacyCheckBox, checkBoxTintList);
        boolean privacyNew = appController.getPrivacyId() < CURRENT_PRIVACY_ID;
        mBinding.PrivacyAllowButton.setOnClickListener(view -> {
            if (!privacyNew) {
                finish();
            } else if (mBinding.privacyCheckBox.isChecked()) {
                appController.setPrivacyId(CURRENT_PRIVACY_ID);
                appController.writeSetting();
                finish();
            } else {
                CompoundButtonCompat.setButtonTintList(mBinding.privacyCheckBox, ContextCompat.getColorStateList(this, R.color.checkbox_backgrount_selector_red));
            }
        });

        if (!privacyNew) {
            setColorBtn(true);
            mBinding.privacyCheckBox.setVisibility(View.GONE);
            mBinding.privacyCheckBox.setChecked(true);
        } else {
            setColorBtn(false);
        }
        mBinding.privacyCheckBox.setOnClickListener(view -> setColorBtn(mBinding.privacyCheckBox.isChecked()));
        mBinding.PrivacyTextView0.setMovementMethod(LinkMovementMethod.getInstance());
        String text = appController.getPrivacyText();
        mBinding.PrivacyTextView0.setText(HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(CHECK_BOX_KEY, mBinding.privacyCheckBox.isChecked());

    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        boolean checked = savedInstanceState.getBoolean(CHECK_BOX_KEY);
        mBinding.privacyCheckBox.setChecked(checked);
        setColorBtn(checked);
    }

    private void setColorBtn(boolean checkBox) {
        ColorStateList mBtnBackColorGreen = getResources().getColorStateList(R.color.green_600, null);
        ColorStateList mBtnBackColorBlack = getResources().getColorStateList(R.color.black3, null);
        ColorStateList mBtnRipleColorGreen = getResources().getColorStateList(R.color.green_100, null);
        ColorStateList mBtnRipleColorBlack = getResources().getColorStateList(R.color.spinner_font, null);
        if (checkBox) {
            mBinding.PrivacyAllowButton.setBackgroundTintList(mBtnBackColorGreen);
            mBinding.PrivacyAllowButton.setRippleColor(mBtnRipleColorGreen);
        } else {
            mBinding.PrivacyAllowButton.setBackgroundTintList(mBtnBackColorBlack);
            mBinding.PrivacyAllowButton.setRippleColor(mBtnRipleColorBlack);
        }
    }

    private void setLightStatusBar() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
      //  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.main_background));
       // }
    }

}