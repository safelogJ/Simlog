package com.safelogj.simlog.routers.keenetic;

import com.safelogj.simlog.collecting.SimCardDataRouter;

public class KeeneticManagerQmi extends KeeneticManager {
    @Override
    protected String getDataCommand(SimCardDataRouter router) {
        return "/rci/show/interface/UsbQmi0";
    }
}
