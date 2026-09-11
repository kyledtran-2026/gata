package local.kdt.gata.common.util;


import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class JsonUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final JsonMapper jsonMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public static JsonMapper getJsonMapper() {
        return jsonMapper;
    }

}
