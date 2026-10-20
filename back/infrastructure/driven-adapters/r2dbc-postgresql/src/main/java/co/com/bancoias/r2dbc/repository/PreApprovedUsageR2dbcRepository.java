package co.com.bancoias.r2dbc.repository;

import co.com.bancoias.r2dbc.entity.PreApprovedUsageEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface PreApprovedUsageR2dbcRepository
        extends ReactiveCrudRepository<PreApprovedUsageEntity, Long> {

    Mono<PreApprovedUsageEntity> findByRequestReference(String requestReference);
}
