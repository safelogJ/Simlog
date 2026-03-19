package com.safelogj.simlog.routers.cudy;

import com.safelogj.simlog.AppController;

public class CudyAuthTokens {

    private String csrf = AppController.EMPTY_STRING;
    private String token = AppController.EMPTY_STRING;
    private String salt = AppController.EMPTY_STRING;

    public String getCsrf() {
        return csrf;
    }

    public void setCsrf(String csrf) {
        this.csrf = csrf;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public boolean isValid() {
        return !csrf.isEmpty() && !token.isEmpty() && !salt.isEmpty();
    }

    @Override
    public String toString() {
        return "CSRF: " + csrf + "\nToken: " + token + "\nSalt: " + salt;
    }
}
