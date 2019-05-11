package com.r307.arbitrader.service;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;

public class ErrorCollectorServiceTest {
    private ErrorCollectorService errorCollectorService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        errorCollectorService = new ErrorCollectorService();
    }

    @Test
    public void testCollect() {
        errorCollectorService.collect(new NullPointerException("Boom!"));

        String report = errorCollectorService.report();

        assertEquals("NullPointerException: Boom! x 1", report);
    }

    @Test
    public void testCollectIncrementsByOne() {
        errorCollectorService.collect(new NullPointerException("Boom!"));
        errorCollectorService.collect(new NullPointerException("Boom!"));

        String report = errorCollectorService.report();

        assertEquals("NullPointerException: Boom! x 2", report);
    }

    @Test
    public void testEmptyReport() {
        assertEquals("", errorCollectorService.report());
    }

    @Test
    public void testIsEmpty() {
        assertTrue(errorCollectorService.isEmpty());

        errorCollectorService.collect(new NullPointerException());

        assertFalse(errorCollectorService.isEmpty());
    }

    @Test
    public void testClear() {
        errorCollectorService.collect(new NullPointerException());

        assertFalse(errorCollectorService.isEmpty());

        errorCollectorService.clear();

        assertTrue(errorCollectorService.isEmpty());
    }
}
