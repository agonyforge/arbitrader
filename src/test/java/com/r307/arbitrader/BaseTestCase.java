package com.r307.arbitrader;

import org.junit.After;
import org.junit.Before;
import org.mockito.MockitoAnnotations;

/**
 * Mockito deprecated MockitoAnnotations.initMocks() and this is one of the recommended patterns to
 * replace it. It's nice because it encapsulates opening and closing the mocks but I'm not thrilled
 * about having to have a superclass for everything. We'll see how it goes.
 *
 * https://www.javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/MockitoAnnotations.html
 */
public class BaseTestCase {
    private AutoCloseable closeable;

    @Before
    public void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @After
    public void releaseMocks() throws Exception {
        closeable.close();
    }
}
