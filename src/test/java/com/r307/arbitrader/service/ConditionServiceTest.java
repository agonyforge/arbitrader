package com.r307.arbitrader.service;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConditionServiceTest {
    private ConditionService conditionService;

    @Before
    public void setUp() {
        conditionService = new ConditionService();
    }

    @Test
    public void testClearForceCloseConditionIdempotence() {
        // it should not break if the condition is already clear
        conditionService.clearForceCloseCondition();
    }

    @Test
    public void testClearForceCloseCondition() throws IOException {
        File forceClose = new File("force-close");

        assertTrue(forceClose.createNewFile());
        assertTrue(forceClose.exists());

        conditionService.clearForceCloseCondition();

        assertFalse(forceClose.exists());
    }

    @Test
    public void testCheckForceCloseCondition() throws IOException {
        File forceClose = new File("force-close");

        assertFalse(forceClose.exists());
        assertFalse(conditionService.isForceCloseCondition());

        assertTrue(forceClose.createNewFile());

        assertTrue(forceClose.exists());
        assertTrue(conditionService.isForceCloseCondition());

        FileUtils.deleteQuietly(forceClose);
    }
}
