package com.safelogj.simlog.collecting;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public class SimListener extends PhoneStateListener {
    public static final String TYPE_2G = "2G";
    public static final String TYPE_3G = "3G";
    public static final String TYPE_4G = "4G";
    public static final String TYPE_5G = "5G";
    public static final String TYPE_XG = "xG";
    private final SimCardData mSimCard;
    private final TelephonyManager mSimManager;
    private final Context mContext;
    private boolean isDisplayed5G;


    public SimListener(SimCardData simCard, TelephonyManager simManager, Context context) {
        this.mSimCard = simCard;
        this.mSimManager = simManager;
        this.mContext = context;
    }

    @Override
    public void onSignalStrengthsChanged(SignalStrength signalStrength) {
        super.onSignalStrengthsChanged(signalStrength);
        mSimCard.setSignalStrength(signalStrength.getLevel());
        mSimCard.setNetworkType(getNetworkTypeName(updateNetworkType()));
    }

    @Override
    public void onDataConnectionStateChanged(int state, int networkType) {
        super.onDataConnectionStateChanged(state, networkType);
        mSimCard.setNetworkType(getNetworkTypeName(networkType));
    }


    @Override
    public void onDisplayInfoChanged(@NonNull TelephonyDisplayInfo telephonyDisplayInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R
                    && ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) { // Android 11: нужен READ_PHONE_STATE
                return; // нет разрешения, выходим
            }

            int displayedNetworkType = telephonyDisplayInfo.getOverrideNetworkType();
            if (displayedNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA
                    || displayedNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE
                    || displayedNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED) {
                isDisplayed5G = true;
                mSimCard.setNetworkType(TYPE_5G);
            } else {
                isDisplayed5G = false;
                mSimCard.setNetworkType(getNetworkTypeName(updateNetworkType()));
            }
        }
    }


    private String getNetworkTypeName(int networkType) {
        if (isDisplayed5G && (networkType == TelephonyManager.NETWORK_TYPE_EHRPD || networkType == TelephonyManager.NETWORK_TYPE_LTE)) {
            return TYPE_5G;
        }
        return switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_NR -> TYPE_5G;
            case TelephonyManager.NETWORK_TYPE_EHRPD,
                 TelephonyManager.NETWORK_TYPE_LTE -> TYPE_4G;
            case TelephonyManager.NETWORK_TYPE_HSDPA,
                 TelephonyManager.NETWORK_TYPE_HSPAP,
                 TelephonyManager.NETWORK_TYPE_HSUPA,
                 TelephonyManager.NETWORK_TYPE_HSPA,
                 TelephonyManager.NETWORK_TYPE_EVDO_B,
                 TelephonyManager.NETWORK_TYPE_EVDO_0,
                 TelephonyManager.NETWORK_TYPE_EVDO_A,
                 TelephonyManager.NETWORK_TYPE_UMTS -> TYPE_3G;
            case TelephonyManager.NETWORK_TYPE_EDGE,
                 TelephonyManager.NETWORK_TYPE_GPRS,
                 TelephonyManager.NETWORK_TYPE_CDMA,
                 TelephonyManager.NETWORK_TYPE_1xRTT,
                 TelephonyManager.NETWORK_TYPE_IDEN -> TYPE_2G;
            default -> TYPE_XG;
        };
    }

    private int updateNetworkType() {
        int networkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                && mSimManager.getDataState() == TelephonyManager.DATA_CONNECTED) {
            networkType = mSimManager.getDataNetworkType();
        }
        return networkType;
    }

}
