package com.safelogj.simlog.collecting;

import com.safelogj.simlog.AppController;

public class SavedRouter {

    private String address = AppController.EMPTY_STRING;
    private String login = AppController.EMPTY_STRING;
    private String pass = AppController.EMPTY_STRING;
    private String customCommand = AppController.EMPTY_STRING;


    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public String getCustomCommand() {
        return customCommand;
    }

    public void setCustomCommand(String customCommand) {
        this.customCommand = customCommand;
    }

    public boolean isValidRouter() {
        return isLanAddress(address) && !login.isEmpty() && !pass.isEmpty();
    }

    public static boolean isRemoved(SavedRouter router) {
        return !router.getAddress().isEmpty() && router.getLogin().equals("remove");
    }

    public static boolean isLanAddress(String host) {
        try {
            String[] ip = host.split("\\.");
            if (ip.length != 4) return false;

            int a = Integer.parseInt(ip[0]);
            int b = Integer.parseInt(ip[1]);
            int c = Integer.parseInt(ip[2]);
            int d = Integer.parseInt(ip[3]);

            if (isInvalidV4(a) || isInvalidV4(b) || isInvalidV4(c) || isInvalidV4(d))
                return false;

            return (a == 192 && b == 168) || (a == 172 && b >= 16 && b <= 31)  || a == 10;

        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isInvalidV4(int digit) {
        return digit > 255 || digit < 0;
    }
}
