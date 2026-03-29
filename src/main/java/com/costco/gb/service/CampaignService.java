package com.costco.gb.service;

import com.costco.gb.dto.request.CreateCampaignRequest;
import com.costco.gb.dto.request.ReviseCampaignRequest;
import com.costco.gb.dto.response.CampaignSummaryResponse;
import com.costco.gb.entity.*;
import com.costco.gb.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value; // 貼上這行
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import com.costco.gb.dto.request.JoinCampaignRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final CategoryRepository categoryRepository;
    private final ParticipantRepository participantRepository;
    private final String UPLOAD_DIR = "uploads/campaigns/";
    private final CreditScoreLogRepository creditScoreLogRepository; // 👈 加入這行
    private final NotificationService notificationService; // 👈 加入這行

    // 🌟 注入 YML 裡的 base-url 參數
    @Value("${app.base-url}")
    private String baseUrl;

    // 把參數補上 categoryId 和 keyword
    public Page<CampaignSummaryResponse> getActiveCampaigns(Integer storeId, Integer categoryId, String keyword, int page, int size) {

        // 1. 設定分頁與排序 (依建立時間倒序，越新的在越上面)
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // 2. 呼叫強大的動態查詢 (取代原本冗長的 if-else)
        Page<Campaign> campaignPage = campaignRepository.findCampaignsWithFilters(
                storeId, categoryId, keyword, pageable);

        // 3. 將 Entity 轉換為 DTO 回傳
        return campaignPage.map(this::mapToSummaryResponse);
    }



    @Transactional
    public void createCampaign(Long hostId, CreateCampaignRequest request) {

        // 1. 驗證團主身分與開團權限
        User host = userRepository.findById(hostId)
                .orElseThrow(() -> new RuntimeException("找不到使用者"));

        if (!host.getHasCostcoMembership()) {
            throw new RuntimeException("拒絕存取：必須擁有好市多會員卡才能發起合購！");
        }

        // 2. 取得並驗證關聯實體 (門市與分類)
        Store store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new RuntimeException("找不到指定的門市"));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("找不到指定的分類"));

        // 3. 🖼️ 處理圖片上傳 (生成 UUID 檔名並存入本地)
        List<String> savedFileNames = new ArrayList<>();
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs(); // 如果資料夾不存在就自動建立
        }

        try {
            List<MultipartFile> images = request.getImages();
            if (images != null && !images.isEmpty()) {
                if (images.size() > 3) {
                    throw new RuntimeException("最多只能上傳 3 張圖片喔！");
                }

                for (MultipartFile file : images) {
                    if (!file.isEmpty()) {
                        // 取得原本的副檔名 (例如 .jpg, .png)
                        String originalFilename = file.getOriginalFilename();
                        String extension = (originalFilename != null && originalFilename.contains("."))
                                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                                : ".jpg"; // 預設副檔名

                        // 生成不會重複的 UUID 檔名
                        String newFileName = UUID.randomUUID().toString() + extension;

                        // 將檔案實體存入伺服器硬碟
                        File destinationFile = new File(uploadDir, newFileName);
//                        file.transferTo(destinationFile);
                        // 🌟 核心魔法：使用 Thumbnailator 在存檔時直接壓縮為 1080p
                        // (取代原本的 file.transferTo(destinationFile);)
                        Thumbnails.of(file.getInputStream())
                                .size(1080, 1080)        // 強制限制最大長寬為 1080，會自動等比例縮放
                               // .outputQuality(0.85)     // 設定畫質為 85%，肉眼看不出差異，但檔案會變超小
                                .toFile(destinationFile);

                        savedFileNames.add(newFileName);
                    }
                }
            }

            // 將 List 轉換成以逗號分隔的字串，準備存入資料庫
            String imageUrlsString = String.join(",", savedFileNames);

            // 4. 建立 Campaign 實體
            Campaign campaign = Campaign.builder()
                    .host(host)
                    .store(store)
                    .category(category)
                    .scenarioType(request.getScenarioType())
                    .itemName(request.getItemName())
                    .imageUrls(imageUrlsString) // 💡 存入 UUID 圖片字串
                    .pricePerUnit(request.getPricePerUnit())
                    .totalQuantity(request.getTotalQuantity())
                    .availableQuantity(request.getTotalQuantity()) // 初始剩餘數量 = 總數量
                    .meetupLocation(request.getMeetupLocation())
                    .meetupTime(request.getMeetupTime())
                    .expireTime(request.getExpireTime())
                    .status("OPEN") // 狀態預設為招募中
                    .build();

            // 5. 寫入資料庫
            campaignRepository.save(campaign);
            // TODO: V2 進階版，我們要在這裡把 availableQuantity 寫入 Redis，準備迎接高併發搶單！
            // (未來實作提示：redisTemplate.opsForValue().set("campaign:stock:" + campaign.getId(), String.valueOf(campaign.getAvailableQuantity())); )
            log.info("User {} 成功發起了合購單: {}", hostId, campaign.getItemName());

        } catch (Exception e) {
            // 🚨 6. 失敗退場機制：如果資料庫寫入失敗，把剛剛存進硬碟的圖片通通刪掉！
            for (String fileName : savedFileNames) {
                File fileToDelete = new File(UPLOAD_DIR + fileName);
                if (fileToDelete.exists()) {
                    fileToDelete.delete();
                }
            }
            log.error("建單發生錯誤，已清理殘留的圖片檔案: {}", e.getMessage());
            throw new RuntimeException("發起合購單失敗：" + e.getMessage());
        }
    }

    // 🌟 查詢我發起的合購單
    @Transactional(readOnly = true)
    public Page<CampaignSummaryResponse> getMyHostedCampaigns(Long userId, Pageable pageable) {
        Page<Campaign> campaigns = campaignRepository.findByHostIdOrderByCreatedAtDesc(userId, pageable);

        // 呼叫我們之前寫好、帶有完整圖片網址的 mapToSummaryResponse
        return campaigns.map(this::mapToSummaryResponse);
    }

    // 🌟 查詢我參與的合購單
    @Transactional(readOnly = true)
    public Page<CampaignSummaryResponse> getMyJoinedCampaigns(Long userId, Pageable pageable) {
        Page<Campaign> campaigns = campaignRepository.findJoinedCampaignsByUserId(userId, pageable);

        // 一樣無痛轉成前端需要的 DTO 格式
        return campaigns.map(this::mapToSummaryResponse);
    }
    // 輔助方法：Entity 轉 DTO
    private CampaignSummaryResponse mapToSummaryResponse(Campaign campaign) {

        // 🌟 1. 處理圖片字串，轉換成前端可直接讀取的完整網址 List
        List<String> imageUrlList = new java.util.ArrayList<>();
        if (campaign.getImageUrls() != null && !campaign.getImageUrls().isEmpty()) {
            String[] images = campaign.getImageUrls().split(",");
            for (String img : images) {
                // 🌟 直接將 baseUrl 與圖片路徑組裝成完整網址！
                // (例如: "http://localhost:8080/images/uuid.jpg")
                imageUrlList.add(baseUrl + "/images/" + img);
            }
        }

        // 🌟 2. 組裝回傳物件
        return CampaignSummaryResponse.builder()
                .id(campaign.getId())
                .itemName(campaign.getItemName())
                .imageUrls(imageUrlList) // 👈 改成塞入我們剛組裝好的 List
                .scenarioType(campaign.getScenarioType())
                .pricePerUnit(campaign.getPricePerUnit())
                .totalQuantity(campaign.getTotalQuantity())
                .availableQuantity(campaign.getAvailableQuantity())
                .meetupLocation(campaign.getMeetupLocation())
                .meetupTime(campaign.getMeetupTime())
                .expireTime(campaign.getExpireTime())
                .status(campaign.getStatus())
                .storeName(campaign.getStore().getName())
                .categoryName(campaign.getCategory().getName())
                .host(CampaignSummaryResponse.HostSummary.builder()
                        .id(campaign.getHost().getId())
                        .displayName(campaign.getHost().getDisplayName())
                        .profileImageUrl(campaign.getHost().getProfileImageUrl())
                        .creditScore(campaign.getHost().getCreditScore())
                        .build())
                .build();
    }
    @Transactional
    public void joinCampaign(Long userId, Long campaignId, JoinCampaignRequest request) {

        // 1. 基本防呆檢查 (保持不變)
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));

        if (!"OPEN".equals(campaign.getStatus())) {
            throw new RuntimeException("手腳太慢啦！此合購單已滿單或關閉。");
        }

        if (campaign.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("來不及啦！這筆合購單已經過了截止時間囉。");
        }

        if (campaign.getHost().getId().equals(userId)) {
            throw new RuntimeException("您是團主，不能參加自己發起的合購單喔！");
        }

        if (request.getQuantity() <= 0) {
            throw new RuntimeException("認購數量必須大於 0");
        }

        // 2. 🛡️ 核心防禦：原子性扣庫存 (直接打資料庫)
        int updatedRows = campaignRepository.decrementQuantity(campaignId, request.getQuantity());
        if (updatedRows == 0) {
            throw new RuntimeException("剩餘數量不足。請確認認購數量或是是否已滿團。");
        }

        // 🌟 關鍵修復：手動同步 Java 記憶體中的 Entity 狀態！
        // 這樣 Hibernate 待會如果要做 save 或髒檢查時，拿到的就會是「已經扣掉」的正確數字。
        campaign.setAvailableQuantity(campaign.getAvailableQuantity() - request.getQuantity());

        // 3. ✨ 檢查是否已經參加過？(保持不變)
        Optional<Participant> optionalParticipant = participantRepository.findByCampaignIdAndUserId(campaignId, userId);

        if (optionalParticipant.isPresent()) {
            Participant existingParticipant = optionalParticipant.get();
            existingParticipant.setQuantity(existingParticipant.getQuantity() + request.getQuantity());
            participantRepository.save(existingParticipant);
            log.info("User {} 在合購單 {} 追加了數量: {}，目前總共認購: {}",
                    userId, campaignId, request.getQuantity(), existingParticipant.getQuantity());
        } else {
            User user = userRepository.getReferenceById(userId);
            Participant newParticipant = Participant.builder()
                    .campaign(campaign)
                    .user(user)
                    .quantity(request.getQuantity())
                    .status("JOINED")
                    .build();
            participantRepository.save(newParticipant);
            log.info("User {} 首次認購了合購單 {}，數量: {}", userId, campaignId, request.getQuantity());
        }

        // 4. 檢查是否滿單 (寫法變得更簡單了！)
        // 因為我們前面已經同步了實體的數字，這裡直接檢查 getAvailableQuantity() 是否為 0 即可
        if (campaign.getAvailableQuantity() == 0) {
            campaign.setStatus("FULL");
            log.info("合購單 {} 已達滿單狀態！", campaignId);
            // 🌟 觸發滿單通知推播！
            notificationService.notifyCampaignFull(campaign);
            // 💡 其實在 @Transactional 的方法內，只要你 set 了值，
            // 交易結束時 Hibernate 會自動幫你 save，所以下面這行甚至可以省略！
            // campaignRepository.save(campaign);
        }
    }

    @Transactional
    public void reviseCampaign(Long userId, Long campaignId, ReviseCampaignRequest request) {

        // 1. 防呆檢查
        if (request.getQuantity() <= 0) {
            throw new RuntimeException("減少的數量必須大於 0");
        }

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));

        // 只有在 OPEN (招募中) 或 FULL (已滿單) 的狀態下可以反悔減少數量。
        // 如果已經面交或結案，就不能改了！
        if (!"OPEN".equals(campaign.getStatus()) && !"FULL".equals(campaign.getStatus())) {
            throw new RuntimeException("此合購單已進入處理階段或已結束，無法修改數量！");
        }

        // 2. 找出這個人的參團紀錄
        Participant participant = participantRepository.findByCampaignIdAndUserId(campaignId, userId)
                .orElseThrow(() -> new RuntimeException("您尚未加入此合購單！"));

        // 3. 🛡️ 核心規則：減少後的數量不能低於 1
        int newQuantity = participant.getQuantity() - request.getQuantity();
        if (newQuantity < 1) {
            throw new RuntimeException("減少後的數量不能低於 1！如果您想完全退出，請使用取消參團功能。");
        }

        // 4. 更新該團員的認購數量
        participant.setQuantity(newQuantity);
        participantRepository.save(participant);

        // 5. 歸還庫存 (打向資料庫的原子操作)
        campaignRepository.incrementQuantity(campaignId, request.getQuantity());

        // 🌟 手動同步記憶體狀態 (避免 Hibernate 髒檢查把舊數字寫回去)
        int currentAvailable = campaign.getAvailableQuantity() + request.getQuantity();
        campaign.setAvailableQuantity(currentAvailable);

        // 6. ✨ 狀態回朔魔法：如果原本滿單了，現在釋出名額，要自動變回招募中！
        if ("FULL".equals(campaign.getStatus()) && currentAvailable > 0) {
            campaign.setStatus("OPEN");
            log.info("合購單 {} 因團員減少認購數量，釋出名額，從 FULL 恢復為 OPEN 狀態！", campaignId);
        }

        log.info("User {} 在合購單 {} 減少了數量: {}，目前總共認購: {}",
                userId, campaignId, request.getQuantity(), newQuantity);
    }
    @Transactional
    public void withdrawCampaign(Long userId, Long campaignId) {

        // 1. 檢查該會員是否真的有加入這團
        Participant participant = participantRepository.findByCampaignIdAndUserId(campaignId, userId)
                .orElseThrow(() -> new RuntimeException("您並未參加此合購單！"));

        // 如果他早就退出過了，防呆擋下
        if (!"JOINED".equals(participant.getStatus())) {
            throw new RuntimeException("您已經退出過了，無法重複操作！");
        }

        // 2. 檢查合購單狀態 (如果已經完成交易，或是流局取消，就不能退出了)
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));

        if (!"OPEN".equals(campaign.getStatus()) && !"FULL".equals(campaign.getStatus())) {
            throw new RuntimeException("此合購單已進入交易階段或已結束，無法退出！請直接聯繫團主。");
        }
        if (LocalDateTime.now().isAfter(campaign.getExpireTime())) {
            throw new RuntimeException("合購單已過截止時間，無法單方面退出！若有緊急狀況請私訊團主。");
        }

        // 3. 變更參與明細狀態為「已取消」
        participant.setStatus("CANCELLED");
        participantRepository.save(participant);

        // 4. 🛡️ 原子性加回庫存，並將狀態強制切回 OPEN
        int updatedRows = campaignRepository.incrementQuantityAndReopen(campaignId, participant.getQuantity());
        if (updatedRows == 0) {
            throw new RuntimeException("退出失敗，合購單狀態可能發生異常！");
        }

        // 5. ⚠️ 信用記點：增加該使用者的「反悔次數」
        User user = userRepository.findById(userId).orElseThrow();
        user.setParticipantCancelCount(user.getParticipantCancelCount() + 1);
        // (V2 進階：如果反悔次數過高，可以在這裡順便扣他的 creditScore 信用分數)
        userRepository.save(user);

        // 6. 紀錄 MDC TraceId 追蹤日誌
        log.info("User {} 成功退出了合購單 {}，釋放數量: {}，累積反悔次數: {}",
                userId, campaignId, participant.getQuantity(), user.getParticipantCancelCount());

        // TODO: 未來可在這裡觸發推播 (Notification)，通知團主有人跳車了
    }
    // ==========================================
    // 🚚 團主專用：宣告已面交 (轉為 DELIVERED)
    // ==========================================
    @Transactional
    public void deliverCampaign(Long userId, Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));

        // 1. 權限檢查：只有團主可以按
        if (!campaign.getHost().getId().equals(userId)) {
            throw new RuntimeException("權限不足：只有團主可以宣告已面交！");
        }

        // 2. 狀態檢查：必須是 OPEN 或 FULL 才能轉為面交
        if (!"OPEN".equals(campaign.getStatus()) && !"FULL".equals(campaign.getStatus())) {
            throw new RuntimeException("此合購單狀態不正確，無法更改為「已面交」！");
        }

        // 3. 防呆：如果根本沒半個人上車，不能按面交 (請他直接取消)
        long joinedCount = participantRepository.countByCampaignIdAndStatus(campaignId, "JOINED");
        if (joinedCount == 0) {
            throw new RuntimeException("目前沒有任何團員參與，無法進行面交，請直接取消合購單。");
        }

        // 4. 更改狀態
        campaign.setStatus("DELIVERED");
        campaignRepository.save(campaign);
        log.info("團主 {} 已將合購單 {} 狀態更改為 DELIVERED (待團員確認收貨)", userId, campaignId);

        // TODO: 未來可在此推播通知所有團員「團主已發貨/面交，請記得點擊確認收貨」
    }

    // ==========================================
    // ✅ 團員專用：確認收貨 (並檢查是否整單 COMPLETED)
    // ==========================================
    @Transactional
    public void confirmReceipt(Long userId, Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));

        // 1. 狀態檢查：團主必須先按了「已面交」
        if (!"DELIVERED".equals(campaign.getStatus())) {
            throw new RuntimeException("團主尚未宣告已面交，暫時無法確認收貨！");
        }

        // 2. 取得團員明細
        Participant participant = participantRepository.findByCampaignIdAndUserId(campaignId, userId)
                .orElseThrow(() -> new RuntimeException("您並未參加此合購單！"));

        if (!"JOINED".equals(participant.getStatus())) {
            throw new RuntimeException("您的訂單狀態不正確或已取消，無法確認收貨！");
        }

        // 3. 將該團員的明細狀態改為「已確認 (CONFIRMED)」
        participant.setStatus("CONFIRMED");
        participantRepository.save(participant);
        log.info("團員 {} 已確認收到合購單 {} 的商品", userId, campaignId);

        // 4. 🌟 終極判斷：是不是所有人都拿到了？
        // 我們去算還有沒有人的狀態停留在 'JOINED' (代表還沒按確認)
        long remainingJoined = participantRepository.countByCampaignIdAndStatus(campaignId, "JOINED");

        if (remainingJoined == 0) {
            // 所有人都確認了，這張單功德圓滿！
            campaign.setStatus("COMPLETED");
            campaignRepository.save(campaign);
            log.info("🎉 合購單 {} 的所有團員皆已確認收貨，訂單正式 COMPLETED！", campaignId);

            // TODO: 未來可在此時發送推播通知，提醒雙方可以開始互給「評價 (Review)」了！
        }
    }
    // ==========================================
    // 👻 系統專用：處理團主人間蒸發的幽靈單
    // ==========================================
    @Transactional
    public void processGhostedCampaigns() {
        LocalDateTime timeoutLimit = LocalDateTime.now().minusHours(24);
        List<Campaign> ghostedCampaigns = campaignRepository.findGhostedCampaigns(timeoutLimit);

        if (ghostedCampaigns.isEmpty()) {
            return;
        }

        for (Campaign campaign : ghostedCampaigns) {
            // 1. 合購單標記為團主放鳥
            campaign.setStatus("HOST_NO_SHOW");

            // 2. 嚴懲團主！重扣 10 分信用分
            User host = campaign.getHost();
            int newScore = Math.max(0, host.getCreditScore() - 10);
            host.setCreditScore(newScore);
            userRepository.save(host);
            // 3. 寫入信用存摺：幽靈放鳥
            CreditScoreLog csLog = CreditScoreLog.builder()
                    .user(host)
                    .scoreChange(-10)
                    .reason("面交時間超過24小時未處理，系統判定放鳥")
                    .campaignId(campaign.getId())
                    .build();
            creditScoreLogRepository.save(csLog);
            // 4. 🕊️ 真正釋放團員的地方！直接呼叫 Repository 的批次更新方法
            int releasedCount = participantRepository.cancelAllByCampaignId(campaign.getId());

            log.warn("🚨 抓到幽靈團主！合購單 {} 強制結案。團主信用扣 10 分。共釋放了 {} 名無辜團員！",
                    campaign.getId(), releasedCount);
        }

        campaignRepository.saveAll(ghostedCampaigns);
    }
    // ==========================================
    // 💣 團主專用：主動取消合購單 (包含階梯式懲罰機制)
    // ==========================================
    @Transactional
    public void cancelCampaignByHost(Long userId, Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));

        // 1. 身分核對：只有團主自己可以按
        if (!campaign.getHost().getId().equals(userId)) {
            throw new RuntimeException("權限不足：只有團主可以取消此合購單！");
        }

        // 2. 狀態核對：已經面交或結案的不能取消
        if (!"OPEN".equals(campaign.getStatus()) && !"FULL".equals(campaign.getStatus())) {
            throw new RuntimeException("合購單已進入交易階段或已結束，無法取消！");
        }

        // 3. 結算懲罰：清點是不是已經有無辜團員上車了？
        long joinedCount = participantRepository.countByCampaignIdAndStatus(campaignId, "JOINED");

        if (joinedCount > 0) {
            User host = campaign.getHost();
            int penalty = 0;
            String penaltyReason = "";

            // 💡 核心亮點：根據狀態決定懲罰力道
            if ("FULL".equals(campaign.getStatus())) {
                penalty = 5;
                penaltyReason = "已成團 (FULL)";
            } else if ("OPEN".equals(campaign.getStatus())) {
                penalty = 2;
                penaltyReason = "未成團 (OPEN)";
            }

            // 執行扣分 (最低扣到 0 分)
            int newScore = Math.max(0, host.getCreditScore() - penalty);
            host.setCreditScore(newScore);
            userRepository.save(host);
            // 🌟 寫入信用存摺：主動取消
            CreditScoreLog cslog = CreditScoreLog.builder()
                    .user(host)
                    .scoreChange(-penalty)
                    .reason("團主主動取消" + penaltyReason + "的合購單")
                    .campaignId(campaignId)
                    .build();
            creditScoreLogRepository.save(cslog);

            // 釋放所有被放鳥的團員 (呼叫 Repository 的批次更新)
            int releasedCount = participantRepository.cancelAllByHost(campaignId);

            log.info("團主 {} 主動取消了【{}】的合購單 {}，共有 {} 人受影響。扣除 {} 分，剩餘信用分 {}",
                    userId, penaltyReason, campaignId, releasedCount, penalty, newScore);
        } else {
            // 沒人上車，和平落幕，完全不扣分
            log.info("團主 {} 取消了無人參與的合購單 {}", userId, campaignId);
        }

        // 4. 最終將這張單設為取消狀態
        campaign.setStatus("CANCELLED");
        campaignRepository.save(campaign);
    }

}