package com.r307.arbitrader.service;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static com.r307.arbitrader.service.ErrorCollectorService.HEADER;
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

        List<String> report = errorCollectorService.report();

        assertEquals(2, report.size());
        assertEquals(HEADER, report.get(0));
        assertEquals("NullPointerException: Boom! x 1", report.get(1));
    }

    @Test
    public void testCollectIncrementsByOne() {
        errorCollectorService.collect(new NullPointerException("Boom!"));
        errorCollectorService.collect(new NullPointerException("Boom!"));

        List<String> report = errorCollectorService.report();

        assertEquals(2, report.size());
        assertEquals(HEADER, report.get(0));
        assertEquals("NullPointerException: Boom! x 2", report.get(1));
    }

    @Test
    public void testEmptyReport() {
        List<String> report = errorCollectorService.report();

        assertEquals(1, report.size());
        assertEquals(HEADER, report.get(0));
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
