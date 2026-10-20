package co.com.bancoias.r2dbc.repository;

import co.com.bancoias.r2dbc.entity.PreApprovedEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface PreApprovedR2dbcRepository extends ReactiveCrudRepository<PreApprovedEntity, String> {
}
