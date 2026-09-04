package com.costco.gb.config;

import com.costco.gb.service.CampaignChatAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignChatOutboundInterceptorTest {

    @Mock
    private WebSocketSessionRegistry sessionRegistry;
    @Mock
    private CampaignChatAccessService campaignChatAccessService;
    @Mock
    private MessageChannel messageChannel;

    @InjectMocks
    private CampaignChatOutboundInterceptor interceptor;

    @Test
    @DisplayName("有效成員的既有訂閱可繼續接收聊天室訊息")
    void preSend_ActiveMember_AllowsDelivery() {
        Message<byte[]> message = campaignMessage("session-1", 10L);
        when(sessionRegistry.findUserId("session-1")).thenReturn(Optional.of(2L));
        when(campaignChatAccessService.getAccessLevel(10L, 2L))
                .thenReturn(CampaignChatAccessService.AccessLevel.READ_WRITE);

        assertNotNull(interceptor.preSend(message, messageChannel));
    }

    @Test
    @DisplayName("退出或被踢除後既有訂閱不得再收到聊天室訊息")
    void preSend_RevokedMember_BlocksDelivery() {
        Message<byte[]> message = campaignMessage("session-1", 10L);
        when(sessionRegistry.findUserId("session-1")).thenReturn(Optional.of(2L));
        when(campaignChatAccessService.getAccessLevel(10L, 2L))
                .thenReturn(CampaignChatAccessService.AccessLevel.NONE);

        assertNull(interceptor.preSend(message, messageChannel));
    }

    private Message<byte[]> campaignMessage(String sessionId, Long campaignId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setSessionId(sessionId);
        accessor.setDestination("/topic/campaigns/" + campaignId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
