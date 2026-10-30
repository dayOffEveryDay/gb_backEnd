# 後端日誌

所有 Spring profiles 均透過 `logback-spring.xml` 將應用程式及框架日誌輸出至 console，每行一筆 UTF-8 JSON，沒有文字前綴。Spring 啟動 banner 已停用。日誌等級仍由 `application.yml` 的 `logging.level` 控制。

基本欄位為 `@timestamp`（Asia/Taipei，含時區）、`log.level`、`process.thread.name`、`log.logger`、`service.name` 與 `message`。HTTP 請求若有 MDC traceId，另輸出 `trace.id`；排程等沒有 traceId 的事件不產生該欄位。例外寫入 `error.type`、`error.message`、`error.stack_trace`，堆疊換行以 JSON 跳脫字元儲存。

## 業務欄位

`JsonLogEncoder` 相容既有的 `中文訊息，event=EVENT_NAME userId={} active=true` 模板：使用原始參數拆出欄位，`event` 映射為 `event.action`，數字、布林、null 與清單保留 JSON 型別，時間與列舉以字串表示。此處解析的是程式中的模板，不是使用者輸入或格式化後的訊息。整段尾部必須符合以空白分隔的 `key=value` 格式，且參數數量相符，否則保留完整 `message`。未包含業務欄位的一般敘述仍是合法 JSON 日誌。

新紀錄亦可使用 SLF4J fluent API 明確提供欄位：

```java
log.atInfo().addKeyValue("event.action", "EXAMPLE_COMPLETED")
        .addKeyValue("userId", userId).log("作業已完成");
```

需要交易提交後才輸出的既有紀錄仍使用 `BusinessLog`，提交、回滾與日誌等級行為不變。不要將完整 Entity、權杖或密碼作為日誌參數。

## 收集

Filebeat 可從部署環境儲存的 console 日誌以 NDJSON 解析後直接送至 Elasticsearch；若容器日誌外層另有封裝，先解析容器格式再解析應用程式 JSON。Elasticsearch 的 index template 需為欄位設定適當型別，例如 `event.action` 為 keyword、ID 為 long、時間欄位為 date。此變更未部署 Filebeat 或 Elasticsearch。

直接寫入 stdout/stderr 的 JVM 診斷、啟動腳本或外部程序不屬於 Logback 的編碼範圍。
