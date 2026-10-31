package com.xiaoyuan.paperpuppybackendintl.controller.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.ModelCredentialStatus;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.ModelCredentialUpsertRequest;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelCredentialService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModelCredentialControllerTest {

    @Test
    void authenticatedEndpointsDelegateOnlyCurrentUserIdAndReturnNoSecret() throws Exception {
        ModelCredentialService service = mock(ModelCredentialService.class);
        ModelCredentialController controller = new ModelCredentialController(service);
        assertTrue(ModelCredentialController.class.isAnnotationPresent(AuthCheck.class));
        var status = new ModelCredentialStatus("deepseek", true, "****1234");
        when(service.listStatus(7L)).thenReturn(List.of(status));
        when(service.upsert(7L, "deepseek", "secret-1234")).thenReturn(status);
        when(service.delete(7L, "deepseek")).thenReturn(true);

        assertEquals(List.of(status), controller.list(7L).getData());
        var response = controller.upsert("deepseek", new ModelCredentialUpsertRequest("secret-1234"), 7L);
        assertEquals(status, response.getData());
        assertFalse(new ObjectMapper().writeValueAsString(response).contains("secret-1234"));
        assertEquals(true, controller.delete("deepseek", 7L).getData());
        verify(service).listStatus(7L);
        verify(service).upsert(7L, "deepseek", "secret-1234");
        verify(service).delete(7L, "deepseek");
    }
}
