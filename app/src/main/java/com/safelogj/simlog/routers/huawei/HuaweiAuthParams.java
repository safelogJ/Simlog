package com.safelogj.simlog.routers.huawei;

import androidx.annotation.NonNull;

public class HuaweiAuthParams {

    private  String sessionId;
    private  String token;
    private String requestVerificationToken;
    private  String salt;
    private  String firstnonce;
    private  String servernonce;
    private  String iterations;

    public HuaweiAuthParams(String sessionId, String token) {
        this.sessionId = sessionId;
        this.token = token;
    }

    public HuaweiAuthParams() {
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getToken() {
        return token;
    }

    public String getSalt() {
        return salt;
    }

    public int getIterations() {
        try {
            return Integer.parseInt(iterations);
        } catch (Exception e) {
            return 1000;
        }
    }

    public void setIterations(String iterations) {
        this.iterations = iterations;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getServernonce() {
        return servernonce;
    }

    public void setServernonce(String servernonce) {
        this.servernonce = servernonce;
    }

    public String getRequestVerificationToken() {
        return requestVerificationToken;
    }

    public void setRequestVerificationToken(String requestVerificationToken) {
        this.requestVerificationToken = requestVerificationToken;
    }

    public String getFirstnonce() {
        return firstnonce;
    }

    public void setFirstnonce(String firstnonce) {
        this.firstnonce = firstnonce;
    }

    @NonNull
    @Override
    public String toString() {
        return "sessionId = " + sessionId + " token = " + token;
    }
}
