package local.kdt.gata.appconfig;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class AppConfigController {

    private final AppConfigService configService;

    // ==================== GET ====================

    @GetMapping("/{type}")
    public ResponseEntity<AppConfig> getConfig(@PathVariable AppConfigType type) {

        return configService.findByType(type)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Get only the parameters map (very common use case)
    @GetMapping("/{type}/params")
    public ResponseEntity<Map<String, Object>> getParameters(@PathVariable AppConfigType type) {

        return configService.findByType(type)
                .map(config -> ResponseEntity.ok(config.getParameters()))
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Typed Value Example ====================

    @GetMapping("/{type}/value")
    public ResponseEntity<Object> getValue(
            @PathVariable AppConfigType type,
            @RequestParam String key) {

        return configService.findByType(type)
                .map(config -> {
                    Object value = config.get(key);
                    return value != null ? ResponseEntity.ok(value) : ResponseEntity.notFound().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== POST / PUT (Create or Update) ====================

    @PutMapping("/{type}")
    public AppConfig updateConfig(
            @PathVariable AppConfigType type,
            @RequestBody Map<String, Object> parameters) {

        // This internally uses getOrCreate(), so it never returns empty
        return configService.updateParameters(type, parameters);
    }

    @PutMapping("/{type}/param")
    public AppConfig updateSingleParam(
            @PathVariable AppConfigType type,
            @RequestParam String key,
            @RequestBody Object value) {

        return configService.updateParameter(type, key, value);
    }

    // ==================== Other Optional patterns ====================

    @DeleteMapping("/{type}")
    public ResponseEntity<Void> deleteConfig(@PathVariable AppConfigType type) {
        if (configService.exists(type)) {
            configService.delete(type);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
