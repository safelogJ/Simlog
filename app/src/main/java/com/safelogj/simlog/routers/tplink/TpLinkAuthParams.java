package com.safelogj.simlog.routers.tplink;

import com.safelogj.simlog.AppController;

public class TpLinkAuthParams {
    public final String nn;
    public final String ee;
    public final int seq;

    private String tokenID = AppController.EMPTY_STRING;
    private String hash = AppController.EMPTY_STRING;
    private String aesKey = AppController.EMPTY_STRING;
    private String aesIv = AppController.EMPTY_STRING;

    public TpLinkAuthParams(String nn, String ee, int seq) {
        this.nn = nn;
        this.ee = ee;
        this.seq = seq;
    }

    public int getSeq() {
        return seq;
    }

    public String getNn() {
        return nn;
    }

    public String getEe() {
        return ee;
    }

    public String getTokenID() {
        return tokenID;
    }

    public void setTokenID(String tokenID) {
        this.tokenID = tokenID;
    }

    public String getHash() {
        return hash;
    }

    public String getAesKey() {
        return aesKey;
    }

    public void setAesKey(String aesKey) {
        this.aesKey = aesKey;
    }

    public String getAesIv() {
        return aesIv;
    }

    public void setAesIv(String aesIv) {
        this.aesIv = aesIv;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public boolean isValid() {
        return !nn.isEmpty() && !ee.isEmpty() && seq > 0;
    }
}
