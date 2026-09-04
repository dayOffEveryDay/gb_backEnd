package com.costco.gb.service;

import com.costco.gb.dto.request.ChatMessageRequest;
import com.costco.gb.entity.Campaign;
import com.costco.gb.entity.ChatMessage;
import com.costco.gb.entity.User;
import com.costco.gb.repository.CampaignRepository;
import com.costco.gb.repository.ChatMessageRepository;
import com.costco.gb.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CampaignChatAccessService chatAccessService;

    @InjectMocks
    private ChatService chatService;

    @Test
    @DisplayName("每次發送訊息都必須重新檢查聊天室寫入權限")
    void saveMessage_AccessRevoked_DoesNotPersistMessage() {
        Campaign campaign = campaign();
        User sender = User.builder().id(2L).displayName("團員").build();
        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent("測試訊息");
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));
        when(userRepository.findById(2L)).thenReturn(Optional.of(sender));
        doThrow(new RuntimeException("您不是此合購單的有效成員，無法發言！"))
                .when(chatAccessService).requireWritable(campaign, 2L);

        assertThrows(RuntimeException.class, () -> chatService.saveMessage(2L, 10L, request));

        verify(chatMessageRepository, never()).save(org.mockito.ArgumentMatchers.any(ChatMessage.class));
    }

    @Test
    @DisplayName("讀取歷史訊息前必須檢查聊天室讀取權限")
    void getChatHistory_ReadAllowed_LoadsMessages() {
        Campaign campaign = campaign();
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));
        when(chatMessageRepository.findByCampaignIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());

        chatService.getChatHistory(2L, 10L);

        verify(chatAccessService).requireReadable(campaign, 2L);
        verify(chatMessageRepository).findByCampaignIdOrderByCreatedAtAsc(10L);
    }

    private Campaign campaign() {
        return Campaign.builder()
                .id(10L)
                .host(User.builder().id(1L).build())
                .status("OPEN")
                .build();
    }
}
