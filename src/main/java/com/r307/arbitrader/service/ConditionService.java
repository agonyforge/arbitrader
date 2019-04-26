package com.r307.arbitrader.service;

import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class ConditionService {
    private File forceCloseFile = new File("force-close");

    public boolean isForceCloseCondition() {
        return forceCloseFile.exists();
    }

    public void clearForceCloseCondition() {
        FileUtils.deleteQuietly(forceCloseFile);
    }
}
