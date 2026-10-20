package co.com.bancoias.r2dbc.adapter;

import co.com.bancoias.model.preapproved.PreApproved;
import co.com.bancoias.model.preapproved.PreApprovedStatus;
import co.com.bancoias.model.preapproved.gateways.PreApprovedRepository;
import co.com.bancoias.r2dbc.entity.PreApprovedEntity;
import co.com.bancoias.r2dbc.repository.PreApprovedR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class PreApprovedR2dbcAdapter implements PreApprovedRepository {

    private final PreApprovedR2dbcRepository repository;

    @Override
    public Mono<PreApproved> findById(String preApprovedId) {
        return repository.findById(preApprovedId).map(this::toDomain);
    }

    private PreApproved toDomain(PreApprovedEntity entity) {
        return PreApproved.builder()
                .preApprovedId(entity.getPreApprovedId())
                .customerId(entity.getCustomerId())
                .status(PreApprovedStatus.valueOf(entity.getStatus()))
                .availableAmount(entity.getAvailableAmount())
                .build();
    }
}
