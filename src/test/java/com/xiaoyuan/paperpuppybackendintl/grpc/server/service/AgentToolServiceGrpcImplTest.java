package com.xiaoyuan.paperpuppybackendintl.grpc.server.service;

import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.GetDocumentRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.GetDocumentResponse;
import com.xiaoyuan.paperpuppybackendintl.grpc.server.service.handler.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolServiceGrpcImplTest {

    @Test
    void getDocumentPreservesGrpcStatusFromHandler() {
        AgentToolFileHandler fileHandler = mock(AgentToolFileHandler.class);
        AgentToolDocumentHandler documentHandler = mock(AgentToolDocumentHandler.class);
        AgentToolCreateDocumentHandler createDocumentHandler = mock(AgentToolCreateDocumentHandler.class);
        AgentToolBookHandler toolBookHandler = mock(AgentToolBookHandler.class);
        AgentToolUserPreferenceHandler userPreferenceHandler = mock(AgentToolUserPreferenceHandler.class);
        AgentToolJsonStructureHandler jsonStructureHandler = mock(AgentToolJsonStructureHandler.class);
        AgentToolAgentRunHandler agentRunHandler = mock(AgentToolAgentRunHandler.class);
        AgentToolServiceGrpcImpl service = new AgentToolServiceGrpcImpl(
                fileHandler,
                documentHandler,
                createDocumentHandler,
                toolBookHandler,
                userPreferenceHandler,
                jsonStructureHandler,
                agentRunHandler
        );

        StatusRuntimeException failure = Status.NOT_FOUND.withDescription("missing doc").asRuntimeException();
        when(documentHandler.getDocument(any(GetDocumentRequest.class))).thenThrow(failure);

        CapturingObserver<GetDocumentResponse> observer = new CapturingObserver<>();
        service.getDocument(GetDocumentRequest.newBuilder()
                .setUserId(1L)
                .setProjectId(2L)
                .setDocumentId(3L)
                .build(), observer);

        assertNotNull(observer.error);
        assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(observer.error).getCode());
        assertEquals("missing doc", Status.fromThrowable(observer.error).getDescription());
    }

    private static final class CapturingObserver<T> implements StreamObserver<T> {
        private Throwable error;

        @Override
        public void onNext(T value) {
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
        }
    }
}
