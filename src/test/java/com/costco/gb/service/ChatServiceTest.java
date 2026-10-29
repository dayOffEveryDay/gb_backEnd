package com.costco.gb.service;

import com.costco.gb.dto.request.ChatMessageRequest;
import com.costco.gb.entity.Campaign;
import com.costco.gb.entity.ChatMessage;
import com.costco.gb.entity.User;
import com.costco.gb.repository.CampaignRepository;
import com.costco.gb.repository.ChatMessageRepository;
import com.costco.gb.repository.UserRepository;
import com.costco.gb.repository.ChatReadStateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
    @Mock
    private ChatReadStateRepository chatReadStateRepository;

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

    @Test
    void markRead_UsesReadPermissionAndExactDisplayedCursor() {
        when(chatMessageRepository.existsByIdAndCampaignId(25L, 10L)).thenReturn(true);
        when(chatMessageRepository.countUnread(10L, 2L)).thenReturn(1L);

        assertEquals(1L, chatService.markRead(2L, 10L, 25L));

        verify(chatAccessService).requireReadable(10L, 2L);
        verify(chatAccessService, never()).requireWritable(any(Campaign.class), anyLong());
        verify(chatReadStateRepository).advance(10L, 2L, 25L);
    }

    @Test
    void markRead_RevokedMemberCannotUpdateProgress() {
        doThrow(new RuntimeException("denied")).when(chatAccessService).requireReadable(10L, 2L);
        assertThrows(RuntimeException.class, () -> chatService.markRead(2L, 10L, 25L));
        verify(chatReadStateRepository, never()).advance(anyLong(), anyLong(), anyLong());
    }

    @Test
    void markRead_RejectsMissingOrForeignMessage() {
        assertThrows(IllegalArgumentException.class, () -> chatService.markRead(2L, 10L, 99L));
        assertThrows(IllegalArgumentException.class, () -> chatService.markRead(2L, 10L, null));
        assertThrows(IllegalArgumentException.class, () -> chatService.markRead(2L, 10L, 0L));
        verify(chatReadStateRepository, never()).advance(anyLong(), anyLong(), anyLong());
    }

    @Test
    void getUnreadCount_ExcludesInaccessibleRooms() {
        Campaign campaign = campaign();
        when(chatAccessService.getAccessLevel(campaign, 2L))
                .thenReturn(CampaignChatAccessService.AccessLevel.NONE);
        assertEquals(0L, chatService.getUnreadCount(2L, campaign));
        verify(chatMessageRepository, never()).countUnread(anyLong(), anyLong());
    }

    @Test
    void getUnreadCount_IncludesReadOnlyMembersAndHost() {
        Campaign campaign = campaign();
        when(chatAccessService.getAccessLevel(campaign, 2L))
                .thenReturn(CampaignChatAccessService.AccessLevel.READ_ONLY);
        when(chatAccessService.getAccessLevel(campaign, 1L))
                .thenReturn(CampaignChatAccessService.AccessLevel.READ_WRITE);
        when(chatMessageRepository.countUnread(10L, 2L)).thenReturn(3L);
        when(chatMessageRepository.countUnread(10L, 1L)).thenReturn(2L);
        assertEquals(3L, chatService.getUnreadCount(2L, campaign));
        assertEquals(2L, chatService.getUnreadCount(1L, campaign));
    }

    @Test
    void getChatHistory_ReturnsPersistentMessageId() {
        Campaign campaign = campaign();
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));
        when(chatMessageRepository.findByCampaignIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(
                ChatMessage.builder().id(25L).campaign(campaign)
                        .sender(User.builder().id(1L).build()).content("hello").build()));
        assertEquals(25L, chatService.getChatHistory(2L, 10L).get(0).getMessageId());
    }
}
