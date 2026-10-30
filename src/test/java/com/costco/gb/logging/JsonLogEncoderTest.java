package com.costco.gb.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.mock.env.MockEnvironment;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonLogEncoderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonLogEncoder encoder = new JsonLogEncoder();
    private final LoggerContext context = new LoggerContext();

    @Test
    void springLoadsJsonConsoleConfiguration() throws Exception {
        LoggingSystem system = LoggingSystem.get(getClass().getClassLoader());
        MockEnvironment environment = new MockEnvironment().withProperty("spring.application.name", "gb-backend");
        system.beforeInitialize();
        try {
            system.initialize(new LoggingInitializationContext(environment), "classpath:logback-spring.xml", null);
            LoggerContext activeContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            var root = activeContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            var appender = root.getAppender("CONSOLE");
            assertNotNull(appender);
            assertTrue(appender.isStarted());
            var outputAppender = (OutputStreamAppender<?>) appender;
            assertInstanceOf(JsonLogEncoder.class, outputAppender.getEncoder());
            JsonLogEncoder configuredEncoder = (JsonLogEncoder) outputAppender.getEncoder();
            JsonNode json = mapper.readTree(configuredEncoder.encode(event("org.springframework.Example", "啟動完成", null)));
            assertEquals("gb-backend", json.get("service.name").asText());
        } finally {
            system.cleanUp();
        }
    }

    @Test
    void refreshTokenEventPreservesTypedFieldsAndTraceId() throws Exception {
        LoggingEvent event = new LoggingEvent(getClass().getName(), context.getLogger("com.costco.gb.service.RefreshTokenService"), Level.DEBUG,
                "更新權杖已建立，event=REFRESH_TOKEN_CREATED userId={} tokenRecordId={} expiresAt={}",
                null, new Object[]{5L, 17L, Instant.parse("2026-11-29T06:50:07.733020100Z")});
        event.setThreadName("nio-8080-exec-4");
        event.setTimeStamp(Instant.parse("2026-10-30T06:50:07.905Z").toEpochMilli());
        event.setMDCPropertyMap(Map.of("traceId", "68d25f6a25a247c98db683401607400c"));
        JsonNode json = mapper.readTree(encoder.encode(event));
        assertEquals("2026-10-30T14:50:07.905+08:00", json.get("@timestamp").asText());
        assertEquals("DEBUG", json.get("log.level").asText());
        assertEquals("nio-8080-exec-4", json.get("process.thread.name").asText());
        assertEquals("gb-backend", json.get("service.name").asText());
        assertEquals(event.getLoggerName(), json.get("log.logger").asText());
        assertEquals("68d25f6a25a247c98db683401607400c", json.get("trace.id").asText());
        assertEquals("更新權杖已建立", json.get("message").asText());
        assertEquals("REFRESH_TOKEN_CREATED", json.get("event.action").asText());
        assertTrue(json.get("userId").isIntegralNumber());
        assertEquals(5, json.get("userId").asInt());
        assertEquals(17, json.get("tokenRecordId").asInt());
        assertEquals("2026-11-29T06:50:07.733020100Z", json.get("expiresAt").asText());
    }

    @Test
    void valuesAreTakenFromArgumentsWithoutParsingUserContent() throws Exception {
        String content = "含空白 event=FAKE\n\"引號\"\\路徑";
        LoggingEvent event = event("com.costco.gb.service.Example",
                "資料已更新，event={} changedFields={} active={} optional={} fixed=true reason={}",
                null, "UPDATED", List.of("displayName"), false, null, content);
        JsonNode json = mapper.readTree(encoder.encode(event));
        assertEquals("UPDATED", json.get("event.action").asText());
        assertTrue(json.get("changedFields").isArray());
        assertEquals("displayName", json.get("changedFields").get(0).asText());
        assertFalse(json.get("active").asBoolean());
        assertTrue(json.get("fixed").asBoolean());
        assertTrue(json.get("optional").isNull());
        assertEquals(content, json.get("reason").asText());
        assertEquals(1, new String(encoder.encode(event), StandardCharsets.UTF_8).lines().count());
    }

    @Test
    void frameworkMessagesAndExceptionsRemainOneJsonLineThroughAppender() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        encoder.setContext(context);
        encoder.start();
        OutputStreamAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new OutputStreamAppender<>();
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.setOutputStream(output);
        appender.start();
        appender.doAppend(event("org.springframework.Example", "啟動失敗\n詳細：{}",
                new IllegalStateException("錯誤\n第二行", new IllegalArgumentException("原因")), "資料庫"));
        appender.stop();
        String line = output.toString(StandardCharsets.UTF_8);
        assertTrue(line.startsWith("{"));
        assertTrue(line.endsWith("}\n"));
        assertEquals(1, line.lines().count());
        JsonNode json = mapper.readTree(line);
        assertEquals("啟動失敗\n詳細：資料庫", json.get("message").asText());
        assertEquals("java.lang.IllegalStateException", json.get("error.type").asText());
        assertTrue(json.get("error.stack_trace").asText().contains("Caused by: java.lang.IllegalArgumentException"));
        assertFalse(json.has("trace.id"));
    }

    @Test
    void plainMessagesAndIncompleteTemplatesAreNotTruncated() throws Exception {
        for (String template : List.of("使用者 {} 已加入", "資料，event=UPDATED id={} 額外文字", "資料，event=UPDATED id={} extra={}")) {
            LoggingEvent event = event("com.costco.gb.service.Example", template, null, 5L);
            JsonNode json = mapper.readTree(encoder.encode(event));
            assertEquals(event.getFormattedMessage(), json.get("message").asText());
            assertFalse(json.has("event.action"));
        }
    }

    private LoggingEvent event(String logger, String message, Throwable error, Object... args) {
        LoggingEvent event = new LoggingEvent(getClass().getName(), context.getLogger(logger),
                Level.DEBUG, message, error, args);
        event.setThreadName("nio-8080-exec-4");
        event.setMDCPropertyMap(Map.of());
        return event;
    }
}
