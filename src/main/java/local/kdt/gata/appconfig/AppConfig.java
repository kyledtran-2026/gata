package local.kdt.gata.appconfig;

import jakarta.persistence.*;
import local.kdt.gata.common.util.JsonMapConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "app_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppConfig {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "config_type", nullable = false, length = 50)
    private AppConfigType appConfigType;

    @Column(name = "parameters")
    @Convert(converter = JsonMapConverter.class)
    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();

    // Helper methods
    public void put(String key, Object value) {
        this.parameters.put(key, value);
    }

    public Object get(String key) {
        return this.parameters.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = parameters.get(key);
        if (value == null) return null;
        if (type.isInstance(value)) {
            return (T) value;
        }
        throw new IllegalArgumentException("Type mismatch for key: " + key);
    }
}