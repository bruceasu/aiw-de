package me.asu.aidev.approval.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal Telegram Bot API client. Keeps Telegram-specific HTTP outside approval authority. */
final class TelegramBotApiClient {
    private final HttpClient http;
    private final ObjectMapper json;
    private final String endpointPrefix;

    TelegramBotApiClient(String apiBase, String token) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.json = new ObjectMapper();
        String base = apiBase == null || apiBase.isBlank() ? "https://api.telegram.org" : apiBase.strip();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        this.endpointPrefix = base + "/bot" + token;
    }

    JsonNode getUpdates(long offset, int timeoutSeconds) throws IOException, InterruptedException {
        Map<String, String> params = new LinkedHashMap<>();
        if (offset > 0) params.put("offset", Long.toString(offset));
        params.put("timeout", Integer.toString(Math.max(0, timeoutSeconds)));
        params.put("allowed_updates", "[\"callback_query\",\"message\"]");
        URI uri = URI.create(endpointPrefix + "/getUpdates?" + encode(params));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(Math.max(10, timeoutSeconds + 10L)))
                .GET().build();
        return send(request, "getUpdates");
    }

    void sendApprovalMessage(String chatId, String text, String approvalId) throws IOException, InterruptedException {
        String keyboard = json.createObjectNode()
                .set("inline_keyboard", json.createArrayNode()
                        .add(json.createArrayNode()
                                .add(json.createObjectNode()
                                        .put("text", "Approve")
                                        .put("callback_data", "aiw:a:" + approvalId))
                                .add(json.createObjectNode()
                                        .put("text", "Reject")
                                        .put("callback_data", "aiw:r:" + approvalId))))
                .toString();
        postForm("sendMessage", Map.of(
                "chat_id", chatId,
                "text", text,
                "reply_markup", keyboard));
    }

    String sendQuestion(String chatId,String text,java.util.List<String> options,String nonce) throws IOException,InterruptedException {
        var keyboard=json.createArrayNode();
        for(int i=0;i<options.size();i++) {
            String label=options.get(i);if(label.length()>60) label="Option "+(i+1);
            keyboard.add(json.createArrayNode().add(json.createObjectNode().put("text",label).put("callback_data","aiw:q:"+nonce+":"+i)));
        }
        var response=postForm("sendMessage",Map.of("chat_id",chatId,"text",text,"reply_markup",json.createObjectNode().set("inline_keyboard",keyboard).toString()));
        if(!response.path("result").path("chat").path("id").asText("").equals(chatId)||!response.path("result").path("message_id").canConvertToLong()) throw new IOException("Telegram question delivery response unconfirmed");
        return response.path("result").path("message_id").asText();
    }

    void answerCallbackQuery(String callbackQueryId, String text, boolean alert)
            throws IOException, InterruptedException {
        postForm("answerCallbackQuery", Map.of(
                "callback_query_id", callbackQueryId,
                "text", text == null ? "" : text,
                "show_alert", Boolean.toString(alert)));
    }

    void clearInlineKeyboard(String chatId, long messageId) throws IOException, InterruptedException {
        postForm("editMessageReplyMarkup", Map.of(
                "chat_id", chatId,
                "message_id", Long.toString(messageId),
                "reply_markup", "{\"inline_keyboard\":[]}"));
    }

    private JsonNode postForm(String method, Map<String, String> params) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpointPrefix + "/" + method))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encode(params)))
                .build();
        return send(request, method);
    }

    private JsonNode send(HttpRequest request, String method) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Telegram " + method + " HTTP " + response.statusCode());
        }
        JsonNode body = json.readTree(response.body());
        if (!body.path("ok").asBoolean(false)) {
            throw new IOException("Telegram " + method + " returned ok=false: " + response.body());
        }
        return body;
    }

    private static String encode(Map<String, String> params) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!out.isEmpty()) out.append('&');
            out.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            out.append('=');
            out.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return out.toString();
    }
}
