package com.warehouse.assistant.core.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.assistant.core.config.AssistantRuntimeConfig;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;

/**
 * Thin client for the OpenAI Images API {@code /v1/images/edits} endpoint, used to
 * combine multiple product reference photos into a single AI-generated set cover.
 * <p>
 * Spring AI's ImageModel only supports text-to-image generation, so this multi-image
 * edit call is made directly. The gpt-image-1 model family always returns base64
 * ({@code b64_json}); the {@code response_format} parameter must NOT be sent.
 */
@Service
public class OpenAiImageEditClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiImageEditClient.class);

    private static final String DEFAULT_BASE_URL = "https://api.openai.com";

    private final AssistantRuntimeConfig runtimeConfig;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public record ImageInput(byte[] bytes, String contentType, String fileName) {}

    public OpenAiImageEditClient(AssistantRuntimeConfig runtimeConfig, ObjectMapper objectMapper) {
        this.runtimeConfig = runtimeConfig;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(120_000); // image generation routinely takes 30-90s
        this.restTemplate = new RestTemplate(factory);
    }

    /** True when an API key is configured (admin setting or OPENAI_API_KEY env). */
    public boolean isConfigured() {
        String key = runtimeConfig.getImageApiKey();
        return key != null && !key.isBlank();
    }

    /**
     * Combines the given reference images into one generated image.
     *
     * @return PNG bytes of the generated image
     */
    public byte[] generateCover(List<ImageInput> inputs, String prompt) {
        String apiKey = runtimeConfig.getImageApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new WarehouseManagementException(ErrorCode.AI_COVER_API_KEY_MISSING);
        }
        String endpoint = runtimeConfig.getImageEndpoint();
        String baseUrl = (endpoint != null && !endpoint.isBlank()) ? endpoint.replaceAll("/+$", "") : DEFAULT_BASE_URL;
        String url = baseUrl + "/v1/images/edits";

        String model = runtimeConfig.getImageModel();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", model);
        body.add("prompt", prompt);
        body.add("n", "1");
        body.add("size", runtimeConfig.getImageSize());
        body.add("quality", runtimeConfig.getImageQuality());
        if ("gpt-image-1".equals(model)) {
            // Preserve product details from the references. Only gpt-image-1 accepts
            // this parameter — mini/1.5 reject it with a 400.
            body.add("input_fidelity", "high");
        }
        for (ImageInput input : inputs) {
            body.add("image[]", filePart(input));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(apiKey);

        long start = System.currentTimeMillis();
        try {
            String response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode b64 = root.path("data").path(0).path("b64_json");
            if (b64.isMissingNode() || b64.asText().isBlank()) {
                log.error("OpenAI image edit returned no b64_json payload: {}", abbreviate(response));
                throw new WarehouseManagementException(ErrorCode.AI_COVER_GENERATION_FAILED);
            }
            byte[] png = Base64.getDecoder().decode(b64.asText());
            log.info("AI set cover generated — model={}, inputs={}, {} bytes in {} ms",
                    runtimeConfig.getImageModel(), inputs.size(), png.length, System.currentTimeMillis() - start);
            return png;
        } catch (HttpStatusCodeException e) {
            String apiMessage = extractApiError(e.getResponseBodyAsString());
            log.error("OpenAI image edit failed with status {}: {}", e.getStatusCode(), apiMessage);
            throw new WarehouseManagementException(ErrorCode.AI_COVER_GENERATION_FAILED,
                    ErrorCode.AI_COVER_GENERATION_FAILED.getMessage()
                            + (apiMessage.isBlank() ? "" : " (" + apiMessage + ")"));
        } catch (ResourceAccessException e) {
            log.error("OpenAI image edit timed out after {} ms: {}", System.currentTimeMillis() - start, e.getMessage());
            throw new WarehouseManagementException(ErrorCode.AI_COVER_GENERATION_TIMEOUT);
        } catch (WarehouseManagementException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI image edit response could not be processed", e);
            throw new WarehouseManagementException(ErrorCode.AI_COVER_GENERATION_FAILED);
        }
    }

    /** Multipart file part with a stable filename (required by the OpenAI API). */
    private HttpEntity<ByteArrayResource> filePart(ImageInput input) {
        ByteArrayResource resource = new ByteArrayResource(input.bytes()) {
            @Override
            public String getFilename() {
                return input.fileName();
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        String contentType = (input.contentType() != null && !input.contentType().isBlank())
                ? input.contentType() : MediaType.IMAGE_PNG_VALUE;
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        return new HttpEntity<>(resource, partHeaders);
    }

    private String extractApiError(String responseBody) {
        try {
            return objectMapper.readTree(responseBody).path("error").path("message").asText("");
        } catch (Exception ignored) {
            return abbreviate(responseBody);
        }
    }

    private String abbreviate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
