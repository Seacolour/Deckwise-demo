package com.xiaoyuan.paperpuppybackendintl.service.session.impl;

import com.xiaoyuan.paperpuppybackendintl.config.openai.OpenAiAutoTitleProperties;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageRole;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageStatus;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageType;
import com.xiaoyuan.paperpuppybackendintl.repository.SessionMessageRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.SessionRepository;
import com.xiaoyuan.paperpuppybackendintl.service.openai.OpenAiTitleGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoTitleServiceImplTest {

    @Test
    void updatesSessionTitleWhenCurrentTitleIsPlaceholder() {
        Fixture fixture = newFixture();

        when(fixture.sessionMessageRepository.countBySessionAndRoleTypeStatus(
                10L, SessionMessageRole.USER, SessionMessageType.TEXT, SessionMessageStatus.FINAL))
                .thenReturn(1);
        when(fixture.sessionRepository.findTitleById(10L)).thenReturn("New chat");
        when(fixture.titleGenerator.generateTitleFromUserMessage("Build a launch deck")).thenReturn("Launch Deck");
        when(fixture.sessionRepository.updateTitleIfIn(10L, fixture.sessionPlaceholders, "Launch Deck")).thenReturn(true);

        fixture.service.tryUpdateTitleAfterFirstTurn(7L, 10L, "Build a launch deck", null);

        verify(fixture.sessionRepository).updateTitleIfIn(10L, fixture.sessionPlaceholders, "Launch Deck");
    }

    @Test
    void doesNotGenerateTitleWhenCurrentTitleWasUserModified() {
        Fixture fixture = newFixture();

        when(fixture.sessionMessageRepository.countBySessionAndRoleTypeStatus(
                10L, SessionMessageRole.USER, SessionMessageType.TEXT, SessionMessageStatus.FINAL))
                .thenReturn(1);
        when(fixture.sessionRepository.findTitleById(10L)).thenReturn("Manual title");

        fixture.service.tryUpdateTitleAfterFirstTurn(7L, 10L, "Continue the work", null);

        verify(fixture.titleGenerator, never()).generateTitleFromUserMessage("Continue the work");
    }

    @Test
    void doesNotReadSessionAgainWhenSessionTitleWasChangedConcurrently() {
        Fixture fixture = newFixture();

        when(fixture.sessionMessageRepository.countBySessionAndRoleTypeStatus(
                10L, SessionMessageRole.USER, SessionMessageType.TEXT, SessionMessageStatus.FINAL))
                .thenReturn(1);
        when(fixture.sessionRepository.findTitleById(10L)).thenReturn("New chat");
        when(fixture.titleGenerator.generateTitleFromUserMessage("Manual rename race")).thenReturn("Launch Deck");
        when(fixture.sessionRepository.updateTitleIfIn(10L, fixture.sessionPlaceholders, "Launch Deck")).thenReturn(false);

        fixture.service.tryUpdateTitleAfterFirstTurn(7L, 10L, "Manual rename race", null);

        verify(fixture.sessionRepository, never()).findById(10L);
    }

    private static Fixture newFixture() {
        List<String> sessionPlaceholders = List.of("New chat");
        OpenAiAutoTitleProperties props = new OpenAiAutoTitleProperties(
                true,
                "gpt-4o-mini",
                4000,
                sessionPlaceholders,
                30);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        SessionMessageRepository sessionMessageRepository = mock(SessionMessageRepository.class);
        OpenAiTitleGenerator titleGenerator = mock(OpenAiTitleGenerator.class);
        AutoTitleServiceImpl service = new AutoTitleServiceImpl(
                props,
                sessionRepository,
                sessionMessageRepository,
                titleGenerator);
        return new Fixture(
                service,
                sessionRepository,
                sessionMessageRepository,
                titleGenerator,
                sessionPlaceholders);
    }

    private record Fixture(
            AutoTitleServiceImpl service,
            SessionRepository sessionRepository,
            SessionMessageRepository sessionMessageRepository,
            OpenAiTitleGenerator titleGenerator,
            List<String> sessionPlaceholders) {
    }
}
