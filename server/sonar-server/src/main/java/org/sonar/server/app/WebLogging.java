/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.app;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.ConsoleAppender;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.sonar.process.LogbackHelper;

import java.util.logging.LogManager;

/**
 * Configure logback for web server process. Logs must be written to console, which is
 * forwarded to file logs/sonar.log by the app master process.
 */
class WebLogging {

  private static final String LOG_FORMAT = "%d{yyyy.MM.dd HH:mm:ss} %-5level web[%logger{20}] %X %msg%n";

  private final LogbackHelper helper = new LogbackHelper();

  LoggerContext configure() {
    LoggerContext ctx = helper.getRootContext();
    ctx.reset();

    helper.enableJulChangePropagation(ctx);
    configureAppender(ctx);
    configureLevels(ctx);

    // Configure java.util.logging, used by Tomcat, in order to forward to slf4j
    LogManager.getLogManager().reset();
    SLF4JBridgeHandler.install();
    return ctx;
  }

  private void configureAppender(LoggerContext ctx) {
    ConsoleAppender consoleAppender = helper.newConsoleAppender(ctx, "CONSOLE", LOG_FORMAT);
    Logger rootLogger = helper.configureLogger(ctx, Logger.ROOT_LOGGER_NAME, Level.INFO);
    rootLogger.addAppender(consoleAppender);
  }

  private void configureLevels(LoggerContext ctx) {
    // override level of some loggers
    helper.configureLogger(ctx, "rails", Level.WARN);
    helper.configureLogger(ctx, "org.hibernate.cache.ReadWriteCache", Level.ERROR);
    helper.configureLogger(ctx, "org.hibernate", Level.WARN);
    helper.configureLogger(ctx, "org.hibernate.SQL", Level.WARN);
    helper.configureLogger(ctx, "org.apache.ibatis", Level.WARN);
    helper.configureLogger(ctx, "java.sql", Level.WARN);
    helper.configureLogger(ctx, "java.sql.ResultSet", Level.WARN);
    helper.configureLogger(ctx, "org.sonar.MEASURE_FILTER", Level.WARN);
    helper.configureLogger(ctx, "org.elasticsearch", Level.INFO);
    helper.configureLogger(ctx, "org.elasticsearch.node", Level.INFO);
    helper.configureLogger(ctx, "org.elasticsearch.http", Level.INFO);
    helper.configureLogger(ctx, "ch.qos.logback", Level.WARN);
  }
}