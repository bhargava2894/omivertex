package com.softility.omivertex.config;

import com.softility.omivertex.service.AssociateService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs exit cleanup shortly after midnight so future-dated exits take effect. */
@Component
public class ExitScheduler {

    private final AssociateService associateService;

    public ExitScheduler(AssociateService associateService) {
        this.associateService = associateService;
    }

    @Scheduled(cron = "0 30 0 * * *")
    public void nightlyExitSweep() {
        associateService.processExits();
    }
}
