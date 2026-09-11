package local.kdt.gata.common.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

@Converter(autoApply = true)
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final JsonMapper objectMapper = JsonUtil.getJsonMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(attribute);
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new HashMap<>();
        }
        return objectMapper.readValue(dbData, new TypeReference<>() {});
    }
}
