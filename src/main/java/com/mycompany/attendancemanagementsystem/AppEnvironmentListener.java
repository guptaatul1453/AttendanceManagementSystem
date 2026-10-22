package com.mycompany.attendancemanagementsystem;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class AppEnvironmentListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent event) {
        Database.configure(event.getServletContext());
    }
}