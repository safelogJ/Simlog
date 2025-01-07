package com.safelogj.simlog;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.CompoundButtonCompat;

import com.safelogj.simlog.collecting.CollectActivity;
import com.safelogj.simlog.collecting.SimCard;
import com.safelogj.simlog.databinding.ActivityChooseSimBinding;
import com.safelogj.simlog.displaying.DisplayActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ChooseSimActivity extends AppCompatActivity {
    private static final String INVALID_CARRIER_NAME = "[^a-zA-Z0-9 _-]";
    private ActivityChooseSimBinding mBinding;
    private List<SimCard> mDisplayedSims;
    private List<SimCard> mCheckedSims;
    private SubscriptionManager mSubscriptionManager;
    private LinearLayout mLinearLayoutForSimCard;
    private ColorStateList mCheckBoxTintList;
    private AppController mController;


    private final ActivityResultCallback<Boolean> requestDisplayFiles = isGranted -> {
        if (Boolean.TRUE == isGranted) {
            displaySimCards();
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
        mBinding = ActivityChooseSimBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mController = (AppController) getApplication();
        mLinearLayoutForSimCard = mBinding.linearSimCard;
        mDisplayedSims = new ArrayList<>();
        mCheckedSims = new ArrayList<>();
        mSubscriptionManager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        mCheckBoxTintList = ContextCompat.getColorStateList(this, R.color.checkbox_backgrount_selector);

        mBinding.leftButton.setOnClickListener(view -> {
            mCheckedSims = getCheckedSims();
            if (mCheckedSims.isEmpty()) {
                Toast.makeText(this, getString(R.string.need_choose_sim), Toast.LENGTH_SHORT).show();
            } else {
                mController.setCheckedSims(mCheckedSims);
                mController.startCollecting();
                startActivity(new Intent(this, CollectActivity.class));
            }
        });
        mBinding.rightButton.setOnClickListener(view -> {
            if (mController.updateFilesPaths()) {
                Toast.makeText(this, getString(R.string.no_data_files), Toast.LENGTH_SHORT).show();
            } else {
                startActivity(new Intent(this, DisplayActivity.class));
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        displaySimCards();
        for (SimCard displayed : mDisplayedSims) {
            for (SimCard checked : mCheckedSims) {
                if (displayed.getSlot() == checked.getSlot()) {
                    displayed.setChecked(true);
                    break;
                }
            }
        }

    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        for (SimCard simCard : mDisplayedSims) {
            outState.putBoolean(simCard.getOperator(), simCard.isChecked());
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        for (SimCard simCard : mDisplayedSims) {
            simCard.setChecked(savedInstanceState.getBoolean(simCard.getOperator()));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mCheckedSims = getCheckedSims();
    }

    private void displaySimCards() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(android.Manifest.permission.READ_PHONE_STATE);
            return;
        }
        mLinearLayoutForSimCard.removeAllViews();
        mDisplayedSims.clear();

        try {
            for (SubscriptionInfo info : mSubscriptionManager.getActiveSubscriptionInfoList()) {
                SimCard simCard = new SimCard(this, info.getSimSlotIndex(),
                        filterCarrierName(info.getCarrierName()), info.getSubscriptionId());

                CompoundButtonCompat.setButtonTintList(simCard, mCheckBoxTintList);
                mDisplayedSims.add(simCard);
                mLinearLayoutForSimCard.addView(LinearLayoutBuilder.createConstraintLayoutForSimCard(this, simCard));
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.subscription_error), Toast.LENGTH_LONG).show();
        }
    }

    private String filterCarrierName(CharSequence charSequence) {
        if (charSequence == null) {
            return "Some";
        }
        return charSequence.toString().replaceAll(INVALID_CARRIER_NAME, "");
    }

    private List<SimCard> getCheckedSims() {
        return mDisplayedSims.stream().filter(SimCard::isChecked).collect(Collectors.toList());

    }
}