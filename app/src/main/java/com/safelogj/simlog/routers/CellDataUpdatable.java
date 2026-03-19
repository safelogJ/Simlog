package com.safelogj.simlog.routers;

import com.safelogj.simlog.collecting.SimCardDataRouter;

public interface CellDataUpdatable {

   /**
    *@exception  InterruptedException кидает нить экзекутора если остановлен сервис при ожидании ответа от роутера.
    */
   boolean setNewDataToSimCard(SimCardDataRouter router) throws InterruptedException;
}
