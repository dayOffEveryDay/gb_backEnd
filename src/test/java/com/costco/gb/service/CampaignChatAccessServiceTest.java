package com.costco.gb.service;

import com.costco.gb.entity.Campaign;
import com.costco.gb.entity.Participant;
import com.costco.gb.entity.User;
import com.costco.gb.repository.CampaignRepository;
import com.costco.gb.repository.ParticipantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.costco.gb.service.CampaignChatAccessService.AccessLevel.NONE;
import static com.costco.gb.service.CampaignChatAccessService.AccessLevel.READ_ONLY;
import static com.costco.gb.service.CampaignChatAccessService.AccessLevel.READ_WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignChatAccessServiceTest {

    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private ParticipantRepository participantRepository;

    @InjectMocks
    private CampaignChatAccessService chatAccessService;

    @Test
    @DisplayName("團主建立團購後即可讀寫聊天室")
    void getAccessLevel_OpenCampaignHost_ReturnsReadWrite() {
        Campaign campaign = campaign("OPEN", null);

        assertEquals(READ_WRITE, chatAccessService.getAccessLevel(campaign, 1L));
    }

    @Test
    @DisplayName("有效團員參團後即可讀寫聊天室")
    void getAccessLevel_JoinedParticipant_ReturnsReadWrite() {
        Campaign campaign = campaign("OPEN", null);
        participant(campaign, "JOINED");

        assertEquals(READ_WRITE, chatAccessService.getAccessLevel(campaign, 2L));
    }

    @Test
    @DisplayName("主動退出或被踢除後不得存取聊天室")
    void getAccessLevel_WithdrawnOrKickedParticipant_ReturnsNone() {
        Campaign campaign = campaign("OPEN", null);
        participant(campaign, "CANCELLED");
        assertEquals(NONE, chatAccessService.getAccessLevel(campaign, 2L));

        participant(campaign, "KICKED");
        assertEquals(NONE, chatAccessService.getAccessLevel(campaign, 2L));
    }

    @Test
    @DisplayName("整團取消後原有效團員保留唯讀紀錄")
    void getAccessLevel_CancelledByHostParticipant_ReturnsReadOnly() {
        Campaign campaign = campaign("CANCELLED", null);
        participant(campaign, "CANCELLED_BY_HOST");

        assertEquals(READ_ONLY, chatAccessService.getAccessLevel(campaign, 2L));
    }

    @Test
    @DisplayName("完成三天內可讀寫，超過三天改為唯讀")
    void getAccessLevel_CompletedCampaign_UsesThreeDayGracePeriod() {
        Campaign withinGracePeriod = campaign("COMPLETED", LocalDateTime.now().minusDays(2));
        participant(withinGracePeriod, "CONFIRMED");
        assertEquals(READ_WRITE, chatAccessService.getAccessLevel(withinGracePeriod, 2L));

        Campaign archived = campaign("COMPLETED", LocalDateTime.now().minusDays(4));
        participant(archived, "CONFIRMED");
        assertEquals(READ_ONLY, chatAccessService.getAccessLevel(archived, 2L));
    }

    private Campaign campaign(String status, LocalDateTime completedAt) {
        return Campaign.builder()
                .id(10L)
                .host(User.builder().id(1L).build())
                .status(status)
                .completedAt(completedAt)
                .build();
    }

    private void participant(Campaign campaign, String status) {
        Participant participant = Participant.builder()
                .campaign(campaign)
                .user(User.builder().id(2L).build())
                .quantity(1)
                .status(status)
                .build();
        when(participantRepository.findByCampaignIdAndUserId(campaign.getId(), 2L))
                .thenReturn(Optional.of(participant));
    }
}
