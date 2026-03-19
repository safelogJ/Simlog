package com.safelogj.simlog.routers.keenetic;

import com.safelogj.simlog.collecting.SimCardDataRouter;

public class KeeneticManagerLte extends KeeneticManager {

    @Override
    protected String getDataCommand(SimCardDataRouter router) {
        return "/rci/show/interface/UsbLte0";
    }
}
