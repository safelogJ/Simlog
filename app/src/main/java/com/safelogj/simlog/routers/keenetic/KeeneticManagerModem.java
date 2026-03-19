package com.safelogj.simlog.routers.keenetic;

import com.safelogj.simlog.collecting.SimCardDataRouter;

public class KeeneticManagerModem extends KeeneticManager {
    @Override
    protected String getDataCommand(SimCardDataRouter router) {
        return "/rci/show/interface/UsbModem0";
    }
}
