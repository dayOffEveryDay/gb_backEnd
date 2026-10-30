package com.costco.gb.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.encoder.EncoderBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** 將應用程式與框架日誌統一編碼為 UTF-8 單行 JSON。 */
public class JsonLogEncoder extends EncoderBase<ILoggingEvent> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneId.of("Asia/Taipei"));
    private static final Pattern FIELD = Pattern.compile("[A-Za-z][A-Za-z0-9]*=(?:\\{\\}|[^\\s{}=]+)");
    private String serviceName = "gb-backend";

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("@timestamp", TIMESTAMP.format(Instant.ofEpochMilli(event.getTimeStamp())));
        fields.put("log.level", event.getLevel().toString());
        fields.put("process.thread.name", event.getThreadName());
        fields.put("log.logger", event.getLoggerName());
        fields.put("service.name", serviceName);
        fields.put("message", event.getFormattedMessage());
        String traceId = event.getMDCPropertyMap().get("traceId");
        if (traceId != null) {
            fields.put("trace.id", traceId);
        }
        if (event.getThrowableProxy() != null) {
            fields.put("error.type", event.getThrowableProxy().getClassName());
            fields.put("error.message", event.getThrowableProxy().getMessage());
            fields.put("error.stack_trace", ThrowableProxyUtil.asString(event.getThrowableProxy()));
        }

        // 僅解析自家程式的固定模板，不從參數內容猜測欄位，避免空白、換行或等號造成誤判。
        if (event.getLoggerName() != null && event.getLoggerName().startsWith("com.costco.gb.")) {
            extractBusinessFields(event, fields);
        }
        // 支援後續以 SLF4J fluent API 明確附加欄位，且不允許覆寫基本日誌資訊。
        if (event.getKeyValuePairs() != null) {
            event.getKeyValuePairs().forEach(pair -> fields.putIfAbsent(pair.key, jsonValue(pair.value)));
        }
        try {
            return (MAPPER.writeValueAsString(fields) + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException ex) {
            // 編碼失敗仍輸出合法 JSON，避免收集器失去事件或混入純文字錯誤。
            return "{\"log.level\":\"ERROR\",\"message\":\"日誌 JSON 編碼失敗\"}\n".getBytes(StandardCharsets.UTF_8);
        }
    }

    private void extractBusinessFields(ILoggingEvent event, Map<String, Object> fields) {
        String template = event.getMessage();
        if (template == null) {
            return;
        }
        int separator = template.indexOf('，');
        String message = separator < 0 ? "" : template.substring(0, separator);
        String suffix = separator < 0 ? template : template.substring(separator + 1);
        String[] tokens = suffix.split(" ", -1);
        Object[] arguments = event.getArgumentArray();
        int argumentCount = arguments == null ? 0 : arguments.length;
        int placeholders = 0;
        if (message.contains("{}")) {
            return;
        }
        // 必須整段符合 key=value 契約才拆欄位；一般敘述與不完整模板保留完整 message。
        for (String token : tokens) {
            if (!FIELD.matcher(token).matches()) {
                return;
            }
            if (token.endsWith("={}")) {
                placeholders++;
            }
        }
        if (placeholders != argumentCount) {
            return;
        }
        Map<String, Object> businessFields = new LinkedHashMap<>();
        int index = 0;
        for (String token : tokens) {
            int equals = token.indexOf('=');
            String key = token.substring(0, equals);
            String value = token.substring(equals + 1);
            Object typedValue = value.equals("{}") ? jsonValue(arguments[index++]) : literal(value);
            businessFields.put(key.equals("event") ? "event.action" : key, typedValue);
        }
        businessFields.forEach(fields::putIfAbsent);
        fields.put("message", message);
    }

    private static Object literal(String value) {
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> value;
        };
    }

    private static Object jsonValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        // 清單保留陣列結構，其餘物件沿用字串表示，避免序列化實體關聯或額外敏感欄位。
        if (value instanceof Iterable<?> iterable) {
            ArrayList<Object> values = new ArrayList<>();
            iterable.forEach(item -> values.add(jsonValue(item)));
            return values;
        }
        if (value.getClass().isArray()) {
            ArrayList<Object> values = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) {
                values.add(jsonValue(Array.get(value, i)));
            }
            return values;
        }
        return value.toString();
    }

    @Override
    public byte[] headerBytes() {
        return null;
    }

    @Override
    public byte[] footerBytes() {
        return null;
    }
}
