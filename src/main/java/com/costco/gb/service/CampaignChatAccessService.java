package com.costco.gb.service;

import com.costco.gb.entity.Campaign;
import com.costco.gb.entity.Participant;
import com.costco.gb.repository.CampaignRepository;
import com.costco.gb.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignChatAccessService {

    private static final Set<String> ACTIVE_PARTICIPANT_STATUSES = Set.of(
            "JOINED", "CONFIRMED", "DISPUTED", "NO_SHOW"
    );
    private static final Set<String> CAMPAIGN_CANCELLED_PARTICIPANT_STATUSES = Set.of(
            "CANCELLED_BY_HOST", "CANCELLED_BY_SYSTEM"
    );

    private final CampaignRepository campaignRepository;
    private final ParticipantRepository participantRepository;

    public enum AccessLevel {
        READ_WRITE,
        READ_ONLY,
        NONE
    }

    @Transactional(readOnly = true)
    public AccessLevel getAccessLevel(Long campaignId, Long userId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到合購單"));
        return getAccessLevel(campaign, userId);
    }

    @Transactional(readOnly = true)
    public AccessLevel getAccessLevel(Campaign campaign, Long userId) {
        boolean isHost = campaign.getHost().getId().equals(userId);
        Optional<Participant> participant = isHost
                ? Optional.empty()
                : participantRepository.findByCampaignIdAndUserId(campaign.getId(), userId);

        // 主動退出與被踢除者不得再讀取或寫入聊天室；整團取消者僅保留歷史紀錄。
        if (!isHost) {
            String participantStatus = participant.map(Participant::getStatus).orElse(null);
            if (CAMPAIGN_CANCELLED_PARTICIPANT_STATUSES.contains(participantStatus)
                    && ("CANCELLED".equals(campaign.getStatus()) || "HOST_NO_SHOW".equals(campaign.getStatus()))) {
                return AccessLevel.READ_ONLY;
            }
            if (!ACTIVE_PARTICIPANT_STATUSES.contains(participantStatus)) {
                return AccessLevel.NONE;
            }
        }

        String campaignStatus = campaign.getStatus();
        if ("CANCELLED".equals(campaignStatus) || "HOST_NO_SHOW".equals(campaignStatus)) {
            return AccessLevel.READ_ONLY;
        }

        // 合購完成後保留三天溝通期，期限屆滿後僅允許查看歷史訊息。
        if ("COMPLETED".equals(campaignStatus)) {
            if (campaign.getCompletedAt() == null
                    || LocalDateTime.now().isAfter(campaign.getCompletedAt().plusDays(3))) {
                return AccessLevel.READ_ONLY;
            }
        }

        return AccessLevel.READ_WRITE;
    }

    @Transactional(readOnly = true)
    public void requireReadable(Long campaignId, Long userId) {
        requireReadable(campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到合購單")), userId);
    }

    public void requireReadable(Campaign campaign, Long userId) {
        if (getAccessLevel(campaign, userId) == AccessLevel.NONE) {
            log.warn("event=CAMPAIGN_CHAT_ACCESS_DENIED actorId={} campaignId={} operation=READ reasonCode=NOT_MEMBER", userId, campaign.getId());
            throw new RuntimeException("您不是此合購單的有效成員，無法查看聊天紀錄！");
        }
    }

    public void requireWritable(Campaign campaign, Long userId) {
        AccessLevel accessLevel = getAccessLevel(campaign, userId);
        if (accessLevel == AccessLevel.NONE) {
            log.warn("event=CAMPAIGN_CHAT_ACCESS_DENIED actorId={} campaignId={} operation=WRITE reasonCode=NOT_MEMBER", userId, campaign.getId());
            throw new RuntimeException("您不是此合購單的有效成員，無法發言！");
        }
        if (accessLevel == AccessLevel.READ_ONLY) {
            log.warn("event=CAMPAIGN_CHAT_ACCESS_DENIED actorId={} campaignId={} operation=WRITE reasonCode=READ_ONLY", userId, campaign.getId());
            throw new RuntimeException("此聊天室目前為唯讀狀態，無法發言！");
        }
    }

}
