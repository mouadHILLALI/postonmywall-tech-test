package com.postonmywall.scheduler;

import com.postonmywall.common.Frequency;
import com.postonmywall.publish.PublishService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PublishScheduler {

    private static final Logger log = LoggerFactory.getLogger(PublishScheduler.class);

    private final ScheduledPublishService scheduledPublishService;
    private final PublishService publishService;

    public PublishScheduler(ScheduledPublishService scheduledPublishService,
                            PublishService publishService) {
        this.scheduledPublishService = scheduledPublishService;
        this.publishService = publishService;
    }

    @Scheduled(cron = "${postonmywall.scheduler.daily-cron}")
    public void runDailyJobs() {
        log.info("[SCHEDULER] Running DAILY publish jobs");
        List<ScheduledPublish> jobs = scheduledPublishService.getActiveJobsByFrequency(Frequency.DAILY);
        log.info("[SCHEDULER] Found {} active daily jobs", jobs.size());
        publishService.publishScheduled(jobs);
    }

    @Scheduled(cron = "${postonmywall.scheduler.weekly-cron}")
    public void runWeeklyJobs() {
        log.info("[SCHEDULER] Running WEEKLY publish jobs");
        List<ScheduledPublish> jobs = scheduledPublishService.getActiveJobsByFrequency(Frequency.WEEKLY);
        log.info("[SCHEDULER] Found {} active weekly jobs", jobs.size());
        publishService.publishScheduled(jobs);
    }
}
