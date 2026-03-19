package com.safelogj.simlog.collecting;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatCheckBox;

import com.safelogj.simlog.AppController;
import com.safelogj.simlog.R;

public class SimCardCheckBox extends AppCompatCheckBox {
    private int slot;
    private String operator = AppController.EMPTY_STRING;
    private int subscriptionId;
    private String checkBoxText = AppController.EMPTY_STRING;
    private String address = AppController.EMPTY_STRING;
    private String login = AppController.EMPTY_STRING;
    private String pass = AppController.EMPTY_STRING;
    private String customCmd = AppController.EMPTY_STRING;
    private boolean isRouter;

    public SimCardCheckBox(@NonNull Context context, int slot, String operator, int subscriptionId) {
        super(context);
        this.slot = slot;
        this.operator = operator;
        this.subscriptionId = subscriptionId;
        checkBoxText = context.getText(R.string.sim_slot).toString() + slot + " " + operator;
    }

    public SimCardCheckBox(@NonNull Context context, String address, String login, String pass, String customCmd) {
        super(context);
        this.address = address;
        this.login = login;
        this.pass = pass;
        this.customCmd = customCmd;
        checkBoxText = address;
        isRouter = true;
    }


    public SimCardCheckBox(@NonNull Context context) {
        super(context);
    }

    public SimCardCheckBox(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SimCardCheckBox(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public int getSlot() {
        return slot;
    }

    public String getOperator() {
        return operator;
    }

    public int getSubscriptionId() {
        return subscriptionId;
    }

    public String getCheckBoxText() {
        return checkBoxText;
    }

    public boolean isRouter() {
        return isRouter;
    }

    public String getAddress() {
        return address;
    }

    public String getLogin() {
        return login;
    }

    public String getPass() {
        return pass;
    }

    public String getCustomCmd() {
        return customCmd;
    }
}
