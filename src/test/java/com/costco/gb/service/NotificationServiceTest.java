package com.costco.gb.service;

import com.costco.gb.entity.Notification;
import com.costco.gb.entity.User;
import com.costco.gb.repository.NotificationRepository;
import com.costco.gb.repository.ParticipantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void sendNotificationIncludesPersistedNotificationIdInWebSocketPayload() {
        User user = User.builder().id(7L).build();
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(99L);
            return notification;
        });

        notificationService.sendNotification(user, "CAMPAIGN_FULL", 42L, "揪團已額滿");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("7"),
                eq("/queue/notifications"),
                payloadCaptor.capture()
        );
        assertEquals(Map.of(
                "id", 99L,
                "content", "揪團已額滿",
                "type", "CAMPAIGN_FULL",
                "referenceId", 42L
        ), payloadCaptor.getValue());
    }
}
