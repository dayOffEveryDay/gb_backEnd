package com.costco.gb.service;

import com.costco.gb.dto.request.CreateCampaignRequest;
import com.costco.gb.dto.request.ReviseCampaignRequest;
import com.costco.gb.dto.request.ReviseHostQuantityRequest;
import com.costco.gb.dto.response.CampaignSummaryResponse;
import com.costco.gb.dto.response.HostDashboardResponse;
import com.costco.gb.dto.response.MyParticipationResponse;
import com.costco.gb.entity.*;
import com.costco.gb.mapper.CampaignMapper;
import com.costco.gb.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    private final CreditScoreLogRepository creditScoreLogRepository;
    private final NotificationService notificationService;
    private final CampaignMapper campaignMapper;
    private final CreditScoreService creditScoreService;
    private final CampaignChatAccessService campaignChatAccessService;


    // 圖片完整網址使用的服務基底 URL
    @Value("${app.base-url}")
    private String baseUrl;

    // currentUserId 可為空，登入時用於補充使用者的參團狀態
    public Page<CampaignSummaryResponse> getActiveCampaigns(
            Integer storeId, Integer categoryId, String keyword, Long currentUserId, int page, int size) {

        // 1. 設定分頁與排序 (依建立時間倒序，越新的在越上面)
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // 2. 依篩選條件及目前使用者查詢合購資料
        Page<Campaign> campaignPage = campaignRepository.findCampaignsWithFilters(
                storeId, categoryId, keyword, currentUserId, pageable);

        // 3. 將 Entity 轉換為 DTO 回傳
        return campaignPage.map(campaignMapper::toSummaryResponse);
    }



    @Transactional
    public void createCampaign(Long hostId, CreateCampaignRequest request) {

        // 1. 驗證團主身分
        User host = userRepository.findById(hostId)
                .orElseThrow(() -> new RuntimeException("找不到使用者"));
        // 🧠 系統自動計算團主自留數量
        int calculatedReserved = request.getProductTotalQuantity() - request.getOpenQuantity();
        // 🛡️ 防呆：開放的數量不能大於商品總數！
        if (request.getOpenQuantity() > request.getProductTotalQuantity()) {
            throw new IllegalArgumentException("開放合購的數量不能大於商品總數！");
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
                        // 保留來源檔案的副檔名，例如 .jpg 或 .png
                        String originalFilename = file.getOriginalFilename();
                        String extension = (originalFilename != null && originalFilename.contains("."))
                                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                                : ".jpg"; // 預設副檔名

                        // 生成不會重複的 UUID 檔名
                        String newFileName = UUID.randomUUID().toString() + extension;

                        // 將檔案實體存入伺服器硬碟
                        File destinationFile = new File(uploadDir, newFileName);
                        // 存檔前將圖片等比例縮放至最大 1080 x 1080
                        Thumbnails.of(file.getInputStream())
                                .size(1080, 1080)        // 強制限制最大長寬為 1080，會自動等比例縮放
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
                    .totalQuantity(request.getOpenQuantity())      // 資料庫的 total 是指「這團總共賣多少」
                    .availableQuantity(request.getOpenQuantity())  // 剛開團，剩餘 = 總共賣的
                    .hostReservedQuantity(calculatedReserved)      // 存入我們剛剛算好的自留數！
                    .meetupLocation(request.getMeetupLocation())
                    .meetupTime(request.getMeetupTime())
                    .expireTime(request.getExpireTime())
                    .status("OPEN") // 狀態預設為招募中
                    .build();

            // 5. 寫入資料庫
            campaignRepository.save(campaign);
            host.setTotalHostedCount(incrementCount(host.getTotalHostedCount()));
            userRepository.save(host);
            // TODO: V2 進階版，我們要在這裡把 availableQuantity 寫入 Redis，準備迎接高併發搶單！
            // (未來實作提示：redisTemplate.opsForValue().set("campaign:stock:" + campaign.getId(), String.valueOf(campaign.getAvailableQuantity())); )
            log.info("User {} 成功發起了合購單: {}", hostId, campaign.getItemName());

        } catch (Exception e) {
            // 建單失敗時清除本次已寫入的圖片，避免留下無對應資料的檔案
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

    @Transactional
    public void updateCampaignImageOrder(Long campaignId, Long userId, List<String> newImageUrls) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));

        // 🛡️ 防護一：權限檢查 (只有主揪可以調整圖片)
        if (!campaign.getHost().getId().equals(userId)) {
            throw new RuntimeException("權限不足：只有主揪可以調整圖片順序");
        }

        // 🛡️ 防護二：數量防呆檢查
        // 因為資料庫存的是 String (例如 "url1,url2,url3")，我們要先用逗號切開來算數量
        String currentUrlsStr = campaign.getImageUrls();
        int oldSize = 0;
        if (currentUrlsStr != null && !currentUrlsStr.trim().isEmpty()) {
            oldSize = currentUrlsStr.split(",").length; // 用逗號切開算長度
        }

        int newSize = (newImageUrls != null) ? newImageUrls.size() : 0;

        if (oldSize != newSize) {
            throw new IllegalArgumentException("圖片數量不符，無法更新排序。舊數量: " + oldSize + ", 新數量: " + newSize);
        }

        // 資料庫以逗號分隔字串保存圖片名稱
        String joinedUrls = "";
        if (newImageUrls != null && !newImageUrls.isEmpty()) {
            joinedUrls = String.join(",", newImageUrls);
        }

        // 更新圖片順序並保存
        campaign.setImageUrls(joinedUrls);
        campaignRepository.save(campaign);

        log.info("合購單 {} 圖片順序已更新: {}", campaignId, joinedUrls);
    }

    // 🌟 查詢我發起的合購單
    @Transactional(readOnly = true)
    public Page<CampaignSummaryResponse> getMyHostedCampaigns(Long userId, Pageable pageable) {
        Page<Campaign> campaigns = campaignRepository.findByHostIdOrderByCreatedAtDesc(userId, pageable);

        // 將 Entity 轉換成包含完整圖片網址的回傳 DTO
        return campaigns.map(campaignMapper::toSummaryResponse);
    }

    // 🌟 查詢我參與的合購單
    @Transactional(readOnly = true)
    public Page<CampaignSummaryResponse> getMyJoinedCampaigns(Long userId, Pageable pageable) {
        Page<Campaign> campaigns = campaignRepository.findJoinedCampaignsByUserId(userId, pageable);

        // 將 Entity 轉換成前端需要的 DTO
        return campaigns.map(campaign -> campaignMapper.toSummaryResponse(campaign, userId));
    }
    // 輔助方法：Entity 轉 DTO
//    private CampaignSummaryResponse mapToSummaryResponse(Campaign campaign) {
//
//        // 🌟 1. 處理圖片字串，轉換成前端可直接讀取的完整網址 List
//        List<String> imageUrlList = new java.util.ArrayList<>();
//        if (campaign.getImageUrls() != null && !campaign.getImageUrls().isEmpty()) {
//            String[] images = campaign.getImageUrls().split(",");
//            for (String img : images) {
//                // 🌟 直接將 baseUrl 與圖片路徑組裝成完整網址！
//                // (例如: "http://localhost:8080/images/uuid.jpg")
//                imageUrlList.add(baseUrl + "/images/" + img);
//            }
//        }
//
//        // 🌟 2. 組裝回傳物件
//        return CampaignSummaryResponse.builder()
//                .id(campaign.getId())
//                .itemName(campaign.getItemName())
//                .imageUrls(imageUrlList)
//                .scenarioType(campaign.getScenarioType())
//                .pricePerUnit(campaign.getPricePerUnit())
//                .totalQuantity(campaign.getTotalQuantity())
//                .availableQuantity(campaign.getAvailableQuantity())
//                .meetupLocation(campaign.getMeetupLocation())
//                .meetupTime(campaign.getMeetupTime())
//                .expireTime(campaign.getExpireTime())
//                .status(campaign.getStatus())
//                .storeName(campaign.getStore().getName())
//                .categoryName(campaign.getCategory().getName())
//                .host(CampaignSummaryResponse.HostSummary.builder()
//                        .id(campaign.getHost().getId())
//                        .displayName(campaign.getHost().getDisplayName())
//                        .profileImageUrl(campaign.getHost().getProfileImageUrl())
//                        .creditScore(campaign.getHost().getCreditScore())
//                        .build())
//                .build();
//    }

    // 🌟 團主專用：修改合購單數量
    @Transactional
    public void reviseCampaignByHost(Long hostId, Long campaignId, ReviseHostQuantityRequest request) {

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));

        // 🛡️ 防護 1：驗證身分，只有團主本人可以改！
        if (!campaign.getHost().getId().equals(hostId)) {
            throw new RuntimeException("權限不足：您不是此合購單的團主！");
        }

        // 🛡️ 防護 2：合購單狀態必須是 OPEN 才能改
        if (!"OPEN".equals(campaign.getStatus())) {
            throw new RuntimeException("合購單已滿單或關閉，無法修改數量！");
        }

        if (request.getNewOpenQuantity() > request.getNewProductTotalQuantity()) {
            throw new IllegalArgumentException("開放合購的數量不能大於商品總數！");
        }

        // 🧠 核心數學運算：
        // 算出已經被團員買走的數量 = 舊的開放總數 - 舊的剩餘數量
        int alreadySoldQuantity = campaign.getTotalQuantity() - campaign.getAvailableQuantity();

        // 🛡️ 防護 3：如果團主想把開放數量縮小，但新的開放數量竟然比「已經賣掉的」還少，絕對要擋下！
        if (request.getNewOpenQuantity() < alreadySoldQuantity) {
            throw new RuntimeException("修改失敗！目前已認購的數量 (" + alreadySoldQuantity + ") 超過您設定的新開放數量！請先請團員退出或維持原數量。");
        }

        // ✨ 計算新的自留數與新的剩餘數
        int newCalculatedReserved = request.getNewProductTotalQuantity() - request.getNewOpenQuantity();
        int newAvailableQuantity = request.getNewOpenQuantity() - alreadySoldQuantity;

        // 更新資料庫
        campaign.setTotalQuantity(request.getNewOpenQuantity());
        campaign.setAvailableQuantity(newAvailableQuantity);
        campaign.setHostReservedQuantity(newCalculatedReserved);

        // 如果修改後剛好剩餘數量變成 0，自動觸發滿單！
        if (newAvailableQuantity == 0) {
            campaign.setStatus("FULL");
            log.info("合購單 {} 因團主修改數量，觸發滿單狀態！", campaignId);
            // 這裡可以呼叫 notificationService.notifyCampaignFull(campaign);
        }

        campaignRepository.save(campaign);
        log.info("團主 {} 成功修改合購單 {} 的數量配置。新自留: {}, 新開放: {}",
                hostId, campaignId, newCalculatedReserved, request.getNewOpenQuantity());
    }


    // 🌟 團主專屬：取得合購單完整儀表板 (包含乘客名單)
    @Transactional(readOnly = true)
    public HostDashboardResponse getCampaignDashboardForHost(Long hostId, Long campaignId) {

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));

        // 🛡️ 防護：確保來看這張單的人，真的是這張單的團主！
        if (!campaign.getHost().getId().equals(hostId)) {
            throw new RuntimeException("權限不足：您不是此合購單的團主，無法查看儀表板！");
        }

        // 1. 撈取所有曾經加入過這張單的團員 (這裡用 findAllByCampaignId，不用濾掉 CANCELLED，讓團主看得到誰跳車)
        // 載入此合購單的全部參與者資料
        List<Participant> allParticipants = participantRepository.findByCampaignId(campaignId);

        // 2. 將團員名單轉換成 DTO
        List<HostDashboardResponse.ParticipantDetail> participantDetails = allParticipants.stream()
                .map(p -> HostDashboardResponse.ParticipantDetail.builder()
                        .participantsId(p.getId())
                        .userId(p.getUser().getId())
                        .displayName(p.getUser().getDisplayName() != null ? p.getUser().getDisplayName() : "匿名會員")
                        .quantity(p.getQuantity())
                        .status(p.getStatus()) // 讓團主知道這個人是 JOINED 還是 CANCELLED
                        .build())
                .toList();

        // 3. 計算各項庫存數據
        int openQty = campaign.getTotalQuantity(); // 我們存的 total 就是開放數量
        int reservedQty = campaign.getHostReservedQuantity();
        int availableQty = campaign.getAvailableQuantity();
        int soldQty = openQty - availableQty; // 已賣出 = 開放數 - 剩餘數

        // 4. 打包回傳
        return HostDashboardResponse.builder()
                .campaignId(campaign.getId())
                .itemName(campaign.getItemName())
                .status(campaign.getStatus())
                .totalPhysicalQuantity(openQty + reservedQty) // 還原當初填的商品總數
                .openQuantity(openQty)
                .hostReservedQuantity(reservedQty)
                .alreadySoldQuantity(soldQty)
                .availableQuantity(availableQty)
                .participants(participantDetails)
                .build();
    }

    @Transactional
    public void joinCampaign(Long userId, Long campaignId, JoinCampaignRequest request) {

        // 1. 驗證合購單狀態、期限及認購資格
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

        // 原子更新資料庫後同步記憶體中的剩餘數量，避免後續判斷使用舊值
        campaign.setAvailableQuantity(campaign.getAvailableQuantity() - request.getQuantity());

        // 查詢不限制狀態，需同時處理使用者過去取消後再次加入的紀錄
        Optional<Participant> optionalParticipant = participantRepository.findByCampaignIdAndUserId(campaignId, userId);

        if (optionalParticipant.isPresent()) {
            Participant existingParticipant = optionalParticipant.get();
            String currentStatus = existingParticipant.getStatus();

            // 曾取消或被移除的參與者重新加入時，重設狀態與數量
            if ("CANCELLED".equals(currentStatus) || "KICKED".equals(currentStatus)) {
                existingParticipant.setStatus("JOINED");
                existingParticipant.setQuantity(request.getQuantity()); // 數量不累加，直接覆蓋成這次要求的新數量

                log.info("User {} 重新加入了合購單 {} (從 {} 狀態復活)，數量: {}",
                        userId, campaignId, currentStatus, request.getQuantity());
            }
            // 已在團內的參與者再次認購時累加數量
            else if ("JOINED".equals(currentStatus)) {
                existingParticipant.setQuantity(existingParticipant.getQuantity() + request.getQuantity());

                log.info("User {} 在合購單 {} 追加了數量: {}，目前總共認購: {}",
                        userId, campaignId, request.getQuantity(), existingParticipant.getQuantity());
            }

            participantRepository.save(existingParticipant);

        } else {
            // 尚無參與紀錄時建立新的參與者資料
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Participant newParticipant = Participant.builder()
                    .campaign(campaign)
                    .user(user)
                    .quantity(request.getQuantity())
                    .status("JOINED") // 預設狀態為加入
                    .build();
            participantRepository.save(newParticipant);
            user.setTotalJoinedCount(incrementCount(user.getTotalJoinedCount()));
            userRepository.save(user);

            log.info("User {} 首次認購了合購單 {}，數量: {}", userId, campaignId, request.getQuantity());
        }

        // 4. 剩餘數量歸零時將合購單標記為滿單
        if (campaign.getAvailableQuantity() == 0) {
            campaign.setStatus("FULL");
            log.info("合購單 {} 已達滿單狀態！", campaignId);
            // 滿單後通知團主與參與者
            notificationService.notifyCampaignFull(campaign);
            // 交易提交時由 Hibernate dirty checking 更新狀態
        }
    }

    // 🌟 團主專用：踢除裝死或惡意團員
    @Transactional
    public void kickParticipant(Long hostId, Long campaignId, Long targetUserId, String reason) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));

        if (!campaign.getHost().getId().equals(hostId)) {
            throw new RuntimeException("只有團主可以踢人！");
        }
        // 🛡️ 防護 1-A：精準攔截面交階段，引導團主使用正確功能
        if ("DELIVERED".equals(campaign.getStatus()) || "CONFIRMED".equals(campaign.getStatus())) {
            throw new RuntimeException("合購單已進入面交階段！若團員未現身，請改用「標記棄單 (NO_SHOW)」功能。");
        }

        // 🛡️ 防護 1-B：嚴格白名單 (只允許 OPEN 和 FULL)
        if (!"OPEN".equals(campaign.getStatus()) && !"FULL".equals(campaign.getStatus())) {
            throw new RuntimeException("合購單目前狀態為 " + campaign.getStatus() + "，已無法執行踢人操作。");
        }
        Participant participant = participantRepository.findByCampaignIdAndUserIdAndStatus(campaignId, targetUserId, "JOINED")
                .orElseThrow(() -> new RuntimeException("找不到該名參團中的團員"));
        // 🛡️ 防護 2：團員狀態檢查 (只能踢正在車上的人！)
        if (!"JOINED".equals(participant.getStatus())) {
            throw new RuntimeException("該團員目前狀態為 " + participant.getStatus() + "，無法進行踢除。");
        }
        // 1. 強制改變狀態為已取消
        participant.setStatus("KICKED");
        participant.setHostNote(reason);
        participantRepository.save(participant);
        // 2. 釋放他佔用的數量
        int freedQuantity = participant.getQuantity();
        campaign.setAvailableQuantity(campaign.getAvailableQuantity() + freedQuantity);

        // 3. 既然有空位了，狀態自動退回 OPEN，並把解鎖開關關掉 (因為 OPEN 本來就可以自由修改)
        campaign.setStatus("OPEN");
        campaign.setAllowRevision(false);
        campaignRepository.save(campaign);

        // 4. 🔔 通知被踢的人與聊天室
        notificationService.sendNotification(participant.getUser(), "KICKED", campaignId,
                "您已被團主移出合購單「" + campaign.getItemName() + "」。");
        notificationService.broadcastChatAccessRevoked(campaignId, targetUserId, "KICKED");

        log.info("團主 {} 踢除了團員 {}，釋出 {} 份，合購單 {} 退回 OPEN 狀態", hostId, targetUserId, freedQuantity, campaignId);
    }

    // 🌟 團主專用：滿單後開放團員修改數量/退出
    @Transactional
    public void unlockRevision(Long hostId, Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));

        if (!campaign.getHost().getId().equals(hostId)) {
            throw new RuntimeException("權限不足！");
        }

        if (!"FULL".equals(campaign.getStatus())) {
            throw new RuntimeException("只有在滿單狀態才需要特別解鎖修改權限！");
        }

        // 打開開關
        campaign.setAllowRevision(true);
        campaignRepository.save(campaign);

        // 🔔 (可選) 推播通知所有人：團主已開放修改權限！
        log.info("合購單 {} 已由團主解鎖修改權限", campaignId);
    }


    @Transactional
    public void reviseCampaign(Long userId, Long campaignId, ReviseCampaignRequest request) {

        // 1. 防呆檢查
        if (request.getQuantity() <= 0) {
            throw new RuntimeException("減少的數量必須大於 0");
        }

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));

        // 🛡️ 防護鎖：只有在 OPEN 或「被團主解鎖」的 FULL 狀態下可以改
        if (!"OPEN".equals(campaign.getStatus()) && !campaign.isAllowRevision()) {
            throw new RuntimeException("合購單已滿單鎖定，如需修改請在聊天室請團主開放修改權限！");
        }

        // 只允許目前仍為 JOINED 的參與者調整數量
        Participant participant = participantRepository.findByCampaignIdAndUserIdAndStatus(campaignId, userId, "JOINED")
                .orElseThrow(() -> new RuntimeException("您尚未加入此合購單，或已經退出！"));

        // 3. 核心規則：減少後的數量不能低於 1
        int newQuantity = participant.getQuantity() - request.getQuantity();
        if (newQuantity < 1) {
            throw new RuntimeException("減少後的數量不能低於 1！如果您想完全退出，請使用取消參團功能。");
        }

        // 4. 更新該團員的認購數量
        participant.setQuantity(newQuantity);
        participantRepository.save(participant);

        // 5. 歸還庫存
        campaignRepository.incrementQuantity(campaignId, request.getQuantity());

        // 同步記憶體中的剩餘數量
        int currentAvailable = campaign.getAvailableQuantity() + request.getQuantity();
        campaign.setAvailableQuantity(currentAvailable);

        // 6. 釋出名額後恢復招募狀態並關閉特殊修改權限
        if (currentAvailable > 0) {
            campaign.setStatus("OPEN");
            campaign.setAllowRevision(false);
            log.info("合購單 {} 釋出名額，恢復為 OPEN 狀態，並關閉特權修改鎖！", campaignId);
        }

        log.info("User {} 在合購單 {} 減少了數量: {}，目前總共認購: {}",
                userId, campaignId, request.getQuantity(), newQuantity);
    }

    @Transactional
    public void withdrawCampaign(Long userId, Long campaignId) {

        // 1. 僅查詢目前仍為 JOINED 的參與紀錄
        Participant participant = participantRepository.findByCampaignIdAndUserIdAndStatus(campaignId, userId, "JOINED")
                .orElseThrow(() -> new RuntimeException("您並未參加此合購單，或已經退出過了！"));

        // 2. 檢查合購單狀態
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));

        // 🛡️ 防護鎖
        if (!"OPEN".equals(campaign.getStatus()) && !campaign.isAllowRevision()) {
            throw new RuntimeException("此合購單已進入交易階段或已結束，無法退出！請直接聯繫團主。");
        }
        if (LocalDateTime.now().isAfter(campaign.getExpireTime())) {
            throw new RuntimeException("合購單已過截止時間，無法單方面退出！若有緊急狀況請私訊團主。");
        }

        // 3. 變更參與明細狀態為「已取消」
        participant.setStatus("CANCELLED");
        participantRepository.save(participant);

        // 4. 原子性加回庫存
        // 4. 歸還庫存並恢復招募狀態
        campaign.setAvailableQuantity(campaign.getAvailableQuantity() + participant.getQuantity());
        campaign.setStatus("OPEN");
        campaign.setAllowRevision(false);
        campaignRepository.save(campaign);

        // 5. 增加參與者取消次數
        User user = userRepository.findById(userId).orElseThrow();
        user.setParticipantCancelCount(incrementCount(user.getParticipantCancelCount()));
        userRepository.save(user);

        log.info("User {} 成功退出了合購單 {}，釋放數量: {}，累積反悔次數: {}",
                userId, campaignId, participant.getQuantity(), user.getParticipantCancelCount());

        // 通知團主有參與者退出
         notificationService.sendNotification(campaign.getHost(), "MEMBER_WITHDRAW", campaignId,
            "😢 團員已退出您的合購單「" + campaign.getItemName() + "」，目前已恢復招募狀態。");
        notificationService.broadcastChatAccessRevoked(campaignId, userId, "WITHDRAWN");

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

        //  🌟 觸發 WebSocket 廣播事件到聊天室
        notificationService.broadcastCampaignStatus(
                campaignId,
                "DELIVERED",
                "主揪已發起面交"
        );
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
            campaign.setCompletedAt(LocalDateTime.now());
            campaignRepository.save(campaign);
            log.info("🎉 合購單 {} 的所有團員皆已確認收貨，訂單正式 COMPLETED！", campaignId);
            notificationService.notifyReviewTime(campaign);
            notificationService.broadcastCampaignStatus(campaignId, "COMPLETED", "團購已完成，聊天室將保留 3 天");
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
            notificationService.broadcastCampaignStatus(
                    campaign.getId(), "HOST_NO_SHOW", "團購已由系統結束，聊天室改為唯讀");

            log.warn("🚨 抓到幽靈團主！合購單 {} 強制結案。團主信用扣 10 分。共釋放了 {} 名無辜團員！",
                    campaign.getId(), releasedCount);
        }

        campaignRepository.saveAll(ghostedCampaigns);
    }
    // ==========================================
    // 💣 團主專用：主動取消合購單 (包含階梯式懲罰機制)
    // ==========================================
    // 🌟 團主主動取消合購單 (翻車/流局)
    @Transactional
    public void cancelCampaignByHost(Long hostId, Long campaignId) {

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));

        // 🛡️ 防護 1：驗證身分
        if (!campaign.getHost().getId().equals(hostId)) {
            throw new RuntimeException("權限不足：您不是此合購單的團主！");
        }

        // 🛡️ 防護 2：狀態防呆
        if ("CANCELLED".equals(campaign.getStatus()) || "COMPLETED".equals(campaign.getStatus()) || "DELIVERED".equals(campaign.getStatus())) {
            throw new RuntimeException("合購單當前狀態無法取消！");
        }

        // 🔍 清查車上的團員
        List<Participant> activeParticipants = participantRepository.findByCampaignIdAndStatus(campaignId, "JOINED");

        if (!activeParticipants.isEmpty()) {
            log.warn("團主 {} 取消了已有 {} 人上車的合購單 {}", hostId, activeParticipants.size(), campaignId);

            // 1. 記錄取消責任人與原因
            campaign.setBlameUser(campaign.getHost());
            campaign.setCancelReason("團主主動取消 (已有團員上車)");

            // 團主在已有參與者時取消合購，依規則扣除信用分
            creditScoreService.recordScoreChange (hostId, -10, "取消已有團員的合購單：「" + campaign.getItemName() + "」" ,campaignId);

            // 2. 取消所有仍在團內的參與紀錄
            User host = campaign.getHost();
            host.setHostCancelCount(incrementCount(host.getHostCancelCount()));
            userRepository.save(host);

            for (Participant p : activeParticipants) {
                p.setStatus("CANCELLED_BY_HOST");
            }
            participantRepository.saveAll(activeParticipants);

            // 3. 通知所有受影響的參與者
            notificationService.notifyCampaignCancelled(campaign, activeParticipants);

        } else {
            campaign.setCancelReason("團主主動取消 (無人上車)");
        }

        // 將合購單標記為取消並重設剩餘數量
        campaign.setStatus("CANCELLED");
        campaign.setAvailableQuantity(campaign.getTotalQuantity());

        campaignRepository.save(campaign);
        notificationService.broadcastCampaignStatus(campaignId, "CANCELLED", "團購已由團主取消，聊天室改為唯讀");
        log.info("合購單 {} 已成功被團主 {} 取消。", campaignId, hostId);
    }


    @Transactional(readOnly = true)
    public MyParticipationResponse getMyParticipation(Long campaignId, Long userId) {

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));

        boolean isHost = campaign.getHost().getId().equals(userId);
        String chatAccess = campaignChatAccessService.getAccessLevel(campaign, userId).name();

        if (isHost) {
            return MyParticipationResponse.builder()
                    .isHost(true)
                    .isJoined(true)
                    .quantity(campaign.getHostReservedQuantity())
                    .chatAccess(chatAccess)
                    .build();
        }

        // 僅查詢目前仍為 JOINED 的參與紀錄
        return participantRepository.findByCampaignIdAndUserIdAndStatus(campaignId, userId, "JOINED")
                .map(participant -> MyParticipationResponse.builder()
                        .isHost(false)
                        .isJoined(true) // 確定有 JOINED 紀錄才算已參加
                        .quantity(participant.getQuantity())
                        .chatAccess(chatAccess)
                        .build())
                .orElseGet(() -> MyParticipationResponse.builder()
                        .isHost(false)
                        .isJoined(false) // 如果被 CANCELLED 了，這裡就會找不到，變成未參加！
                        .quantity(0)
                        .chatAccess(chatAccess)
                        .build());
    }

    // 🌟 API 1：團主標記團員棄單/未現身 (NO_SHOW)
    @Transactional
    public void markParticipantAsNoShow(Long currentUserId, Long campaignId, Long targetUserId, String note) {

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));

        // 🛡️ 防護 1：驗證操作者是不是這張單的團主
        if (!campaign.getHost().getId().equals(currentUserId)) {
            throw new RuntimeException("越權操作！只有該單的團主可以標記團員棄單。");
        }

        Participant participant = participantRepository.findByCampaignIdAndUserId(campaignId, targetUserId)
                .orElseThrow(() -> new RuntimeException("找不到該團員的參團紀錄"));

        String currentStatus = participant.getStatus();

        // 🛡️ 防護 2：只有 JOINED (裝死中) 或是他偷按了 COMPLETED 的情況，團主才可以行使「一票否決權」
        if (!"JOINED".equals(currentStatus) && !"COMPLETED".equals(currentStatus)) {
            throw new RuntimeException("該團員目前狀態為 " + currentStatus + "，無法標記為棄單。");
        }

        // 🎯 執行狀態變更
        participant.setStatus("NO_SHOW");
        participant.setHostNote(note); // 🌟 關鍵：存入團主的備註
        participantRepository.save(participant);
        User targetUser = participant.getUser();
        targetUser.setNoShowCount(incrementCount(targetUser.getNoShowCount()));
        userRepository.save(targetUser);

        log.info("🚨 團主 {} 已將團員 {} 標記為棄單 (合購單 {})", currentUserId, targetUserId, campaignId);

        // 🌟 觸發推播：警告團員已被標記棄單，提醒他有權利提出仲裁！
        notificationService.notifyUserNoShowWarning(participant.getUser(), campaign);
    }


    // 🌟 API 2：團員提出仲裁/爭議 (DISPUTED)
    @Transactional
    public void raiseDispute(Long currentUserId, Long campaignId, String reason) {

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));

        Participant participant = participantRepository.findByCampaignIdAndUserId(campaignId, currentUserId)
                .orElseThrow(() -> new RuntimeException("找不到您的參團紀錄"));

        String currentStatus = participant.getStatus();

        // 🛡️ 防護：什麼情況可以按仲裁？
        // 1. JOINED (面交時間過了，團主消失或沒給貨)
        // 2. NO_SHOW (被團主誣賴棄單，我要反擊！)
        if (!"JOINED".equals(currentStatus) && !"NO_SHOW".equals(currentStatus)) {
            throw new RuntimeException("您目前的狀態無法提出仲裁。");
        }

        // 🎯 執行狀態變更
        participant.setStatus("DISPUTED");
        participant.setDisputeReason(reason);
        participantRepository.save(participant);

        // 如果資料庫有開欄位，這裡可以把 reason (爭議原因) 存起來，或是自動發送一則系統訊息到該合購單的聊天室
        log.info("⚖️ 團員 {} 對合購單 {} 提出仲裁！原因: {}", currentUserId, campaignId, reason);

        // 🌟 觸發推播：通知團主有人提出爭議了，訂單已凍結！
        notificationService.notifyHostAboutDispute(campaign);
    }

    private int incrementCount(Integer currentCount) {
        return currentCount == null ? 1 : currentCount + 1;
    }

}
