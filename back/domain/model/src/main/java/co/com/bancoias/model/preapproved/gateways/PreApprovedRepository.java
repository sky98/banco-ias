package co.com.bancoias.model.preapproved.gateways;

import co.com.bancoias.model.preapproved.PreApproved;
import reactor.core.publisher.Mono;

public interface PreApprovedRepository {
    Mono<PreApproved> findById(String preApprovedId);
}
