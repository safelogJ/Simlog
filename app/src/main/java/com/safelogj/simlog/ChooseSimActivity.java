package com.safelogj.simlog;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.CompoundButtonCompat;

import com.safelogj.simlog.collecting.CollectActivity;
import com.safelogj.simlog.collecting.LogWriteService;
import com.safelogj.simlog.collecting.SavedRouter;
import com.safelogj.simlog.collecting.SimCardCheckBox;
import com.safelogj.simlog.collecting.SimCardData;
import com.safelogj.simlog.collecting.SimCardDataRouter;
import com.safelogj.simlog.databinding.ActivityChooseSimBinding;
import com.safelogj.simlog.displaying.DisplayActivity;
import com.safelogj.simlog.helpers.LinearLayoutBuilder;
import com.safelogj.simlog.helpers.PassFieldListener;

import java.util.ArrayList;
import java.util.List;

public class ChooseSimActivity extends AppCompatActivity {
    private static final String INVALID_CARRIER_NAME = "[^a-zA-Z0-9 _-]";
    private static final String SOME_CARRIER_NAME = "Some";
    private static final String EMPTY_STRING = "";
    private ActivityChooseSimBinding mBinding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<SimCardCheckBox> mDisplayedSimCardCheckBox = new ArrayList<>();
    private final List<SimCardData> mCheckedSimCardData = new ArrayList<>();
    private int mPermissionCounter;
    private SubscriptionManager mSubscriptionManager;
    private LinearLayout mLinearLayoutForSimCard;
    private ColorStateList mCheckBoxTintList;
    private AppController mController;
    private SavedRouter currentRouter;
    private final ActivityResultCallback<Boolean> requestDisplaySimCard = isGranted -> {
        if (Boolean.TRUE == isGranted) {
            displaySimCards();
            displayCheckStatus();
        } else {
            permissionCount();
        }
    };
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), requestDisplaySimCard);

    private final ActivityResultCallback<Boolean> requestNotifications = isGranted -> {
        if (Boolean.TRUE == isGranted) {
            startService();
        } else {
            permissionCount();
        }
    };
    private final ActivityResultLauncher<String> requestNotificationsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), requestNotifications);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityChooseSimBinding.inflate(getLayoutInflater());
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
        currentRouter = mController.getCurrentRouter();
        mLinearLayoutForSimCard = mBinding.linearSimCard;
        mSubscriptionManager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        mCheckBoxTintList = ContextCompat.getColorStateList(this, R.color.checkbox_backgrount_selector);
        setAddBtnListener();
        setLeftBtnListener();
        setRightBtnListener();
        mBinding.editPass.setOnTouchListener(new PassFieldListener());
    }

    @Override
    protected void onStart() {
        super.onStart();
        drawCurrentRouter();
        displaySimCards();
        displayCheckStatus();
    }


//    private void makeR() {
//        for (int i = 1; i < 255 ; i++) {
//            String addr = "192.168.99." + i;
//            mCheckedSimCardData.add(new SimCardDataRouter(addr, "login", "pass", ""));
//        }
//
//        for (int i = 1; i < 255 ; i++) {
//            String addr = "192.168.100." + i;
//            mCheckedSimCardData.add(new SimCardDataRouter(addr, "login", "pass", ""));
//        }
//
//        for (int i = 1; i < 255 ; i++) {
//            String addr = "192.168.101." + i;
//            mCheckedSimCardData.add(new SimCardDataRouter(addr, "login", "pass", ""));
//        }
//
//    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        for (SimCardCheckBox simCardCheckBox : mDisplayedSimCardCheckBox) {
            outState.putBoolean(simCardCheckBox.getCheckBoxText(), simCardCheckBox.isChecked());
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        for (SimCardCheckBox simCardCheckBox : mDisplayedSimCardCheckBox) {
            simCardCheckBox.setChecked(savedInstanceState.getBoolean(simCardCheckBox.getCheckBoxText()));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        fillSimCardsDataList();
        fillRouterFromFields(currentRouter);
        mController.writeRoutersListEncrypted();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void setAddBtnListener() {
        mBinding.addRouterButton.setOnClickListener(v -> {
            SavedRouter router = fillRouterFromFields(new SavedRouter());
            if (SavedRouter.isRemoved(router)) {
                mController.removeRouterFromMap(router);
            } else {
                if (!router.isValidRouter()) {
                    drawRedField();
                    return;
                }
                mController.addRouterToMap(router);
            }
            fillSimCardsDataList();
            displaySimCards();
            displayCheckStatus();
        });
    }

    private void setLeftBtnListener() {
        mBinding.leftButton.setOnClickListener(view -> {
            fillSimCardsDataList();
            if (mCheckedSimCardData.isEmpty()) {
                Toast.makeText(this, getString(R.string.need_choose_sim), Toast.LENGTH_SHORT).show();
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestNotificationsLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                } else {
                    startService();
                }
            }
        });
    }

    private void setRightBtnListener() {
        mBinding.rightButton.setOnClickListener(view -> {
            if (mController.updateFilesPaths()) {
                Toast.makeText(this, getString(R.string.no_data_files), Toast.LENGTH_SHORT).show();
            } else {
                startActivity(new Intent(this, DisplayActivity.class));
            }
        });
    }

    private void displayCheckStatus() {
        for (SimCardCheckBox displayed : mDisplayedSimCardCheckBox) {
            for (SimCardData checked : mCheckedSimCardData) {
                if (displayed.getCheckBoxText().equals(checked.getCheckBoxText())) {
                    displayed.setChecked(true);
                    break;
                }
            }
        }
    }

    private void drawCurrentRouter() {
        mBinding.editAddress.setText(currentRouter.getAddress());
        mBinding.editLogin.setText(currentRouter.getLogin());
        mBinding.editPass.setText(currentRouter.getPass());
        mBinding.editCustomCmd.setText(currentRouter.getCustomCommand());
    }

    private void displaySimCards() {
        mLinearLayoutForSimCard.removeAllViews();
        mDisplayedSimCardCheckBox.clear();

        for (SavedRouter router : mController.getSavedRoutersMap().values()) {
            SimCardCheckBox simCardCheckBox = new SimCardCheckBox(this, router.getAddress(), router.getLogin(), router.getPass(), router.getCustomCommand());
            CompoundButtonCompat.setButtonTintList(simCardCheckBox, mCheckBoxTintList);
            mDisplayedSimCardCheckBox.add(simCardCheckBox);
            mLinearLayoutForSimCard.addView(LinearLayoutBuilder.createConstraintLayoutForSimCard(this, simCardCheckBox, this::onCheckBoxClicked));
        }


        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(android.Manifest.permission.READ_PHONE_STATE);
            return;
        }

        try {
            for (SubscriptionInfo info : mSubscriptionManager.getActiveSubscriptionInfoList()) {
                SimCardCheckBox simCardCheckBox = new SimCardCheckBox(this, info.getSimSlotIndex(),
                        filterCarrierName(info.getCarrierName()), info.getSubscriptionId());

                CompoundButtonCompat.setButtonTintList(simCardCheckBox, mCheckBoxTintList);
                mDisplayedSimCardCheckBox.add(simCardCheckBox);
                mLinearLayoutForSimCard.addView(LinearLayoutBuilder.createConstraintLayoutForSimCard(this, simCardCheckBox, this::onCheckBoxClicked));
            }

        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.subscription_error), Toast.LENGTH_LONG).show();
        }

        if (mDisplayedSimCardCheckBox.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_sim), Toast.LENGTH_LONG).show();
        }
    }

    private void onCheckBoxClicked(SimCardCheckBox simCardCheckBox) {
        mBinding.editAddress.setText(simCardCheckBox.getAddress());
        mBinding.editLogin.setText(simCardCheckBox.getLogin());
        mBinding.editPass.setText(simCardCheckBox.getPass());
        mBinding.editCustomCmd.setText(simCardCheckBox.getCustomCmd());
    }

    private String filterCarrierName(CharSequence charSequence) {
        if (charSequence == null) {
            return SOME_CARRIER_NAME;
        }
        return charSequence.toString().replaceAll(INVALID_CARRIER_NAME, EMPTY_STRING);
    }

    private void fillSimCardsDataList() {
        if (!mCheckedSimCardData.isEmpty()) {
            mCheckedSimCardData.clear();
        }
        for (SimCardCheckBox simCardCheckBox : mDisplayedSimCardCheckBox) {
            if (simCardCheckBox.isChecked()) {
                if (simCardCheckBox.isRouter()) {
                    mCheckedSimCardData.add(new SimCardDataRouter(simCardCheckBox.getAddress(), simCardCheckBox.getLogin(),
                            simCardCheckBox.getPass(), simCardCheckBox.getCustomCmd()));
                } else {
                    mCheckedSimCardData.add(new SimCardData(simCardCheckBox.getSlot(), simCardCheckBox.getOperator(),
                            simCardCheckBox.getSubscriptionId(), simCardCheckBox.getCheckBoxText()));
                }
            }
        }

    }

    private void startService() {
        mController.setCheckedSims(new ArrayList<>(mCheckedSimCardData));
        startService(new Intent(this, LogWriteService.class));
        Intent intent = new Intent(this, CollectActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void permissionCount() {
        mPermissionCounter++;
        if (mPermissionCounter > 2) {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
            mPermissionCounter = 0;
        } else {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show();
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

    private void drawRedField() {
        drawEditTextAddressRedBorder(mBinding.editAddress);
        drawEditTextRedBorder(mBinding.editLogin);
        drawEditTextRedBorder(mBinding.editPass);
    }

    private void drawEditTextRedBorder(EditText editText) {
        if (editText.getText().toString().trim().isEmpty()) {
            editText.setBackground(AppCompatResources.getDrawable(this, R.drawable.router_field_bg_red));
            handler.postDelayed(() ->
                            editText.setBackground(AppCompatResources.getDrawable(this, R.drawable.router_field_bg)),
                    1000
            );
        }
    }

    private void drawEditTextAddressRedBorder(EditText editText) {
        String address = editText.getText().toString().trim();
        if (address.isEmpty() || !SavedRouter.isLanAddress(address)) {
            editText.setBackground(AppCompatResources.getDrawable(this, R.drawable.router_field_bg_red));
            handler.postDelayed(() ->
                            editText.setBackground(AppCompatResources.getDrawable(this, R.drawable.router_field_bg)),
                    1000
            );
        }
    }

    private SavedRouter fillRouterFromFields(SavedRouter router) {
        router.setAddress(mBinding.editAddress.getText().toString().trim());
        router.setLogin(mBinding.editLogin.getText().toString().trim());
        router.setPass(mBinding.editPass.getText().toString().trim());
        router.setCustomCommand(mBinding.editCustomCmd.getText().toString().trim());
        return router;
    }
}