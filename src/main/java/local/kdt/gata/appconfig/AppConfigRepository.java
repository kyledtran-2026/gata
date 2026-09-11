package local.kdt.gata.appconfig;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppConfigRepository extends JpaRepository<AppConfig, AppConfigType> {

    Optional<AppConfig> findByAppConfigType(AppConfigType configType);

    boolean existsByAppConfigType(AppConfigType configType);

    void deleteByAppConfigType(AppConfigType configType);
}