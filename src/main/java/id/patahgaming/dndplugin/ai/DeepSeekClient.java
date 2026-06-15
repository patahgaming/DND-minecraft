package id.patahgaming.dndplugin.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import id.patahgaming.dndplugin.DndPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP client minimal ke DeepSeek API (OpenAI-compatible /chat/completions).
 * Tidak menggunakan LangChain — panggilan langsung, sinkron (blocking).
 *
 * PENTING: panggil method ini di luar main thread Bukkit (gunakan
 * runTaskAsynchronously / CompletableFuture) untuk menghindari blocking server tick.
 */
public class DeepSeekClient {

    private final DndPlugin plugin;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public DeepSeekClient(DndPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Kirim system prompt + user prompt, kembalikan teks respons AI.
     * Blocking — wajib dipanggil dari async task.
     */
    public String complete(String systemPrompt, String userPrompt) throws Exception {
        String apiKey = plugin.getConfig().getString("deepseek.api-key", "");
        String baseUrl = plugin.getConfig().getString("deepseek.base-url", "https://api.deepseek.com/v1");
        String model = plugin.getConfig().getString("deepseek.model", "deepseek-chat");
        double temperature = plugin.getConfig().getDouble("deepseek.temperature", 0.7);

        if (apiKey == null || apiKey.isBlank() || apiKey.equals("PUT_YOUR_DEEPSEEK_API_KEY_HERE")) {
            throw new IllegalStateException("DeepSeek API key belum diset di config.yml");
        }

        JsonArray messages = new JsonArray();

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemPrompt);
        messages.add(systemMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messages.add(userMsg);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("temperature", temperature);
        body.addProperty("stream", false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("DeepSeek API error (" + response.statusCode() + "): " + response.body());
        }

        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
        return json.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();
    }
}