package com.r307.arbitrader;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

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
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
