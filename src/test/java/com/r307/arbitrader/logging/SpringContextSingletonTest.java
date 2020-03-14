package com.r307.arbitrader.logging;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;

import static org.junit.Assert.assertEquals;

public class SpringContextSingletonTest {
    @Mock
    private ApplicationContext applicationContext;

    @SuppressWarnings("FieldCanBeLocal")
    private SpringContextSingleton singleton;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        singleton = new SpringContextSingleton();
        singleton.setup();
        singleton.setApplicationContext(applicationContext);
    }

    @Test
    public void testGetApplicationContext() {
        SpringContextSingleton result = SpringContextSingleton.getInstance();

        assertEquals(applicationContext, result.getApplicationContext());
    }
}
