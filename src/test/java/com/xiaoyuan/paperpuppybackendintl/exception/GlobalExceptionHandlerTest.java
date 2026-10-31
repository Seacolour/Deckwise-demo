package com.xiaoyuan.paperpuppybackendintl.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private Logger accessLogger;
    private Level originalAccessLevel;
    private ListAppender<ILoggingEvent> accessEvents;

    @BeforeEach
    void captureAccessEvents() {
        accessLogger = (Logger) LoggerFactory.getLogger("com.xiaoyuan.paperpuppybackendintl.accesslog");
        originalAccessLevel = accessLogger.getLevel();
        accessLogger.setLevel(Level.INFO);
        accessEvents = new ListAppender<>();
        accessEvents.start();
        accessLogger.addAppender(accessEvents);
    }

    @AfterEach
    void stopCapturingAccessEvents() {
        accessLogger.detachAppender(accessEvents);
        accessLogger.setLevel(originalAccessLevel);
        accessEvents.stop();
    }

    @Test
    void mediaTypeNegotiationScansAreSilentInProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        GlobalExceptionHandler handler = new GlobalExceptionHandler(environment);

        var response = handler.handleMediaTypeNotAcceptable(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)));

        assertEquals(406, response.getStatusCode().value());
        assertNull(response.getBody());
        assertTrue(accessEvents.list.isEmpty());
    }

    @Test
    void clientBadRequestsRemainObservableOutsideProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        GlobalExceptionHandler handler = new GlobalExceptionHandler(environment);

        var response = handler.handleMediaTypeNotAcceptable(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)));

        assertEquals(406, response.getStatusCode().value());
        assertEquals(1, accessEvents.list.size());
        assertEquals("api_error", accessEvents.list.getFirst().getFormattedMessage());
    }

    @Test
    void incompatibleAcceptHeaderReturnsEmpty406WithoutSecondaryNegotiationFailure() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        GlobalExceptionHandler handler = new GlobalExceptionHandler(environment);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new StreamingController())
                .setControllerAdvice(handler)
                .build();

        mockMvc.perform(post("/stream").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().string(""));

        assertTrue(accessEvents.list.isEmpty());
    }

    @Controller
    private static class StreamingController {

        @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        @ResponseBody
        String stream() {
            return "unused";
        }
    }
}
