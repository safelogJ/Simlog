package com.safelogj.simlog.collecting;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

import androidx.core.content.ContextCompat;

public class SimListener extends PhoneStateListener {
    private final SimCard mSimCard;
    private final TelephonyManager mSimManager;
    private final Context mContext;


    public SimListener(SimCard simCard, TelephonyManager simManager, Context context) {
        this.mSimCard = simCard;
        this.mSimManager = simManager;
        this.mContext = context;
    }

    @Override
    public void onSignalStrengthsChanged(SignalStrength signalStrength) {
        super.onSignalStrengthsChanged(signalStrength);
        int signal = signalStrength.getLevel();
        String type = getNetworkTypeName(updateNetworkType());
        mSimCard.setSignalStrength(signal);
        mSimCard.setNetworkType(type);

    }

    @Override
    public void onDataConnectionStateChanged(int state, int networkType) {
        super.onDataConnectionStateChanged(state, networkType);
        String type = getNetworkTypeName(networkType);
        mSimCard.setNetworkType(type);
    }

    private String getNetworkTypeName(int networkType) {
        return switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_NR -> "5G";
            case TelephonyManager.NETWORK_TYPE_EHRPD,
                 TelephonyManager.NETWORK_TYPE_LTE -> "4G";
            case TelephonyManager.NETWORK_TYPE_HSDPA,
                 TelephonyManager.NETWORK_TYPE_HSUPA,
                 TelephonyManager.NETWORK_TYPE_HSPA,
                 TelephonyManager.NETWORK_TYPE_EVDO_B,
                 TelephonyManager.NETWORK_TYPE_EVDO_0,
                 TelephonyManager.NETWORK_TYPE_EVDO_A,
                 TelephonyManager.NETWORK_TYPE_UMTS -> "3G";
            case TelephonyManager.NETWORK_TYPE_EDGE,
                 TelephonyManager.NETWORK_TYPE_GPRS,
                 TelephonyManager.NETWORK_TYPE_CDMA,
                 TelephonyManager.NETWORK_TYPE_1xRTT,
                 TelephonyManager.NETWORK_TYPE_IDEN -> "2G";
            default -> "xG";
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
