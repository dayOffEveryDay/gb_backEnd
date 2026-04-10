# gb_backEnd

## Latest Updates

- Added review status query support so the client can check whether the current user has already reviewed a target user in a campaign.
- Campaigns now record `completed_at` when all joined participants confirm receipt.
- When a campaign becomes `COMPLETED`, the system sends review reminder notifications to the host and confirmed participants.
- WebSocket chat subscription now validates room access and blocks expired/completed chat rooms after the grace period.

Costco 團購平台後端專案，使用 Spring Boot 提供 REST API、JWT 驗證、WebSocket 即時聊天室與通知推播。

## 專案概述

這個專案的核心目標，是讓使用者能以 Costco 商品分購為主題建立團購、參與他人團購、在面交前透過聊天室協調細節，並在交易完成後透過評價與信用分數機制累積信任。

目前後端已涵蓋：

- LINE OAuth 登入與 JWT 驗證
- 團購建立、參加、修改、退出、取消、交付、收貨確認
- 滿團後由主揪解鎖修改、踢除團員
- 即時聊天室與 WebSocket 推播通知
- 主揪追蹤、封鎖名單、使用者公開資料
- 評價與信用分數紀錄

## 技術棧

- Java 17
- Spring Boot 3.2.3
- Spring Web
- Spring Data JPA
- Spring Security
- Spring Validation
- Spring WebSocket + STOMP
- Spring Data Redis
- MySQL
- JWT (`jjwt`)
- Thumbnailator
- Lombok
- Gradle

## 主要模組

專案主要程式碼位於 `src/main/java/com/costco/gb`，目前結構包含：

- `controller`：REST API 與 WebSocket 入口
- `service`：主要業務邏輯
- `repository`：資料存取層
- `entity`：JPA Entity
- `dto`：請求與回應資料模型
- `security`：JWT 與 Spring Security 設定
- `config`：WebSocket、靜態資源、TraceId 等設定
- `scheduler`：排程任務
- `mapper`：Entity 與 DTO 轉換

## 已實作功能

### 1. 認證與使用者

- LINE OAuth 登入
- 開發用快速登入
- JWT 驗證
- 更新個人資料
- 查詢使用者公開資料
- 封鎖與解除封鎖使用者
- 查詢封鎖名單
- 追蹤與取消追蹤主揪
- 查詢我的追蹤清單

### 2. 團購流程

- 查詢活動列表，可依門市、分類、關鍵字篩選
- 建立團購活動與上傳圖片
- 主揪調整開團數量與保留量
- 參加團購
- 修改參與數量
- 退出團購
- 主揪取消活動
- 主揪標記已交付
- 團員確認收貨
- 滿單後主揪可解鎖修改權限
- 主揪可踢除指定團員
- 查詢我的主揪活動
- 查詢我的參與活動
- 查詢我的信用分數紀錄
- 查詢主揪活動儀表板

### 3. 即時互動

- 團購聊天室歷史訊息查詢
- WebSocket / STOMP 即時聊天
- WebSocket 個人通知推播
- 滿團、取消、退出、被踢除等通知寫入資料庫並即時推送

### 4. 信用與評價

- 新增交易評價
- 依評價調整信用分數
- 寫入信用分數異動紀錄
- 排程處理主揪放鳥情境

## 主要 API 分類

目前主要 API 路徑如下：

- `/api/v1/auth`
- `/api/v1/campaigns`
- `/api/v1/users`
- `/api/v1/reviews`
- `/api/v1/notifications`
- `/api/v1/follows`
- `/api/v1/stores`
- `/api/v1/categories`

詳細欄位與範例請參考：

- [API.md](/c:/Users/a-hui/IdeaProjects/ahui/costco/gb_backEnd/API.md)
- [DB_SCHEMA.md](/c:/Users/a-hui/IdeaProjects/ahui/costco/gb_backEnd/DB_SCHEMA.md)
- [Function.md](/c:/Users/a-hui/IdeaProjects/ahui/costco/gb_backEnd/Function.md)

## WebSocket 說明

- STOMP 端點：`/ws`
- Client Send Prefix：`/app`
- Topic Prefix：`/topic`
- User Queue Prefix：`/user`

聊天室訊息：

- 發送：`/app/chat/{campaignId}/sendMessage`
- 訂閱：`/topic/campaigns/{campaignId}`

個人通知：

- 訂閱：`/user/queue/notifications`

連線時需帶入：

```http
Authorization: Bearer <JWT_TOKEN>
```

## 本機啟動需求

啟動前請先準備：

- Java 17
- MySQL
- Redis

預設設定位於 `src/main/resources/application.yml`：

- MySQL：`jdbc:mysql://localhost:3306/gbc`
- Redis：`localhost:6379`
- Server Port：`8080`

建議環境變數：

- `DB_USERNAME`
- `GBC_DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `GBC_LINE_CHANNEL_ID`
- `GBC_LINE_CHANNEL_SECRET`
- `APP_BASE_URL`

## 執行方式

啟動專案：

```bash
./gradlew bootRun
```

執行測試：

```bash
./gradlew test
```

Windows 也可使用：

```powershell
.\gradlew.bat bootRun
.\gradlew.bat test
```

## 圖片與靜態資源

團購圖片會儲存在：

- `uploads/campaigns/`

對外存取路徑：

- `/images/{fileName}`

## 備註

- 目前 `spring.jpa.hibernate.ddl-auto` 設為 `update`
- JWT、LINE OAuth、Redis 與 WebSocket 都已串接在同一專案中
- 若要對照資料表與 API 行為，請以程式碼與 `API.md` / `DB_SCHEMA.md` 為準
