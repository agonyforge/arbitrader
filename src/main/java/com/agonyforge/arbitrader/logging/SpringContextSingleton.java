package com.agonyforge.arbitrader.logging;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Holds the Spring ApplicationContext so that non-Spring beans can access it.
 */
@Component
public class SpringContextSingleton implements ApplicationContextAware {
    private static SpringContextSingleton instance = null;

    public static SpringContextSingleton getInstance() {
        return instance;
    }

    private ApplicationContext applicationContext;

    @PostConstruct
    public void setup() {
        instance = this;
    }

    @Override
    public void setApplicationContext(@SuppressWarnings("NullableProblems") ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
