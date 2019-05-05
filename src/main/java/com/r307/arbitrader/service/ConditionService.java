package com.r307.arbitrader.service;

import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class ConditionService {
    static final String FORCE_CLOSE = "force-close";
    static final String EXIT_WHEN_IDLE = "exit-when-idle";

    private File forceCloseFile = new File(FORCE_CLOSE);
    private File exitWhenIdleFile = new File(EXIT_WHEN_IDLE);

    public boolean isForceCloseCondition() {
        return forceCloseFile.exists();
    }

    public void clearForceCloseCondition() {
        FileUtils.deleteQuietly(forceCloseFile);
    }

    public boolean isExitWhenIdleCondition() {
        return exitWhenIdleFile.exists();
    }

    public void clearExitWhenIdleCondition() {
        FileUtils.deleteQuietly(exitWhenIdleFile);
    }
}
