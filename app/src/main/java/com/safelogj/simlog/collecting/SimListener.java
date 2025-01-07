package com.safelogj.simlog.collecting;

import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

public class SimListener extends PhoneStateListener {
    private final SimCard mSimCard;


    public SimListener(SimCard simCard) {
        this.mSimCard = simCard;
    }

    @Override
    public void  onSignalStrengthsChanged(SignalStrength signalStrength) {
        super.onSignalStrengthsChanged(signalStrength);
        int signal = signalStrength.getLevel();
        if (mSimCard.getSignalStrength() != signal) {
            mSimCard.setSignalStrength(signal);
        }
    }

    @Override
    public void onDataConnectionStateChanged(int state, int networkType) {
        super.onDataConnectionStateChanged(state, networkType);
        String type = getNetworkTypeName(networkType);
        if (!mSimCard.getNetworkType().equals(type)) {
            mSimCard.setNetworkType(type);
        }
    }

    private String getNetworkTypeName(int networkType) {
        return switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_NR -> "5G";
            case TelephonyManager.NETWORK_TYPE_LTE -> "4G";
            case TelephonyManager.NETWORK_TYPE_HSDPA,
                 TelephonyManager.NETWORK_TYPE_HSUPA,
                 TelephonyManager.NETWORK_TYPE_HSPA,
                 TelephonyManager.NETWORK_TYPE_EVDO_B,
                 TelephonyManager.NETWORK_TYPE_EHRPD,
                 TelephonyManager.NETWORK_TYPE_UMTS -> "3G";
            case TelephonyManager.NETWORK_TYPE_EDGE,
                 TelephonyManager.NETWORK_TYPE_GPRS,
                 TelephonyManager.NETWORK_TYPE_CDMA,
                 TelephonyManager.NETWORK_TYPE_1xRTT,
                 TelephonyManager.NETWORK_TYPE_IDEN -> "2G";

            default -> "unknown";
        };
    }


}
