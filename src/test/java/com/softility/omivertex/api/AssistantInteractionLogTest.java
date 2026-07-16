package com.softility.omivertex.api;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.softility.omivertex.service.AssistantInteractionLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantInteractionLogTest {

    private final AssistantInteractionLog interactionLog = new AssistantInteractionLog();
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(AssistantInteractionLog.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void record_writesOneParseableLine() {
        interactionLog.record("viewer@softility.com", AssistantInteractionLog.Outcome.ANSWERED,
                List.of("search_associates", "get_associate_detail"), 2413, "who is free for Acme?");

        assertThat(appender.list).hasSize(1);
        String line = appender.list.get(0).getFormattedMessage();
        assertThat(line).contains("MIRAI user=viewer@softility.com");
        assertThat(line).contains("outcome=ANSWERED");
        assertThat(line).contains("tools=[search_associates, get_associate_detail]");
        assertThat(line).contains("latencyMs=2413");
        assertThat(line).contains("question=\"who is free for Acme?\"");
    }

    @Test
    void record_escapesQuotesInTheQuestion_andSurvivesNull() {
        interactionLog.record("system", AssistantInteractionLog.Outcome.ERROR, List.of(), 5,
                "find \"Priya\"");
        interactionLog.record("system", AssistantInteractionLog.Outcome.ERROR, List.of(), 5, null);

        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("question=\"find \\\"Priya\\\"\"");
        assertThat(appender.list.get(1).getFormattedMessage()).contains("question=\"\"");
    }
}
