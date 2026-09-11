package local.kdt.gata.appconfig;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppConfigService {
    private static final Logger LOG = LoggerFactory.getLogger(AppConfigService.class);

    private final AppConfigRepository repository;

    /**
     * Get configuration by type. Creates it with empty parameters if it doesn't exist.
     */
    @Transactional
    public AppConfig getOrCreate(AppConfigType configType) {
        return repository.findByAppConfigType(configType)
                .orElseGet(() -> {
                    AppConfig newConfig = AppConfig.builder()
                            .appConfigType(configType)
                            .build();
                    return repository.save(newConfig);
                });
    }

    /**
     * Get configuration (returns empty Optional if not found)
     */
    public Optional<AppConfig> findByType(AppConfigType configType) {
        return repository.findByAppConfigType(configType);
    }

    /**
     * Update a single parameter
     */
    @Transactional
    public AppConfig updateParameter(AppConfigType configType, String key, Object value) {
        AppConfig config = getOrCreate(configType);
        config.put(key, value);
        return repository.save(config);
    }

    /**
     * Update multiple parameters at once
     */
    @Transactional
    public AppConfig updateParameters(AppConfigType configType, Map<String, Object> newParams) {
        AppConfig config = getOrCreate(configType);
        config.getParameters().putAll(newParams);
        return repository.save(config);
    }

    /**
     * Get a typed value from config (with default fallback)
     */
    public <T> T getValue(AppConfigType configType, String key, Class<T> type, T defaultValue) {
        return findByType(configType)
                .map(config -> config.get(key, type))
                .orElse(defaultValue);
    }

    /**
     * Get a typed value (returns null if not found or not present)
     */
    public <T> T getValue(AppConfigType configType, String key, Class<T> type) {
        return getValue(configType, key, type, null);
    }

    /**
     * Remove a specific parameter
     */
    @Transactional
    public AppConfig removeParameter(AppConfigType configType, String key) {
        AppConfig config = getOrCreate(configType);
        config.getParameters().remove(key);
        return repository.save(config);
    }

    /**
     * Delete entire configuration
     */
    @Transactional
    public void delete(AppConfigType configType) {
        repository.deleteByAppConfigType(configType);
    }

    /**
     * Get all configurations
     */
    public List<AppConfig> getAll() {
        return repository.findAll();
    }

    /**
     * Check if config exists
     */
    public boolean exists(AppConfigType configType) {
        return repository.existsByAppConfigType(configType);
    }
}