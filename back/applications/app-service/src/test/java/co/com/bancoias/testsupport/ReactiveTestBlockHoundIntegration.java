package co.com.bancoias.testsupport;

import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

public class ReactiveTestBlockHoundIntegration implements BlockHoundIntegration {

    @Override
    public void applyTo(BlockHound.Builder builder) {
        builder.allowBlockingCallsInside("java.security.SecureRandom", "nextBytes");
        builder.allowBlockingCallsInside("java.io.RandomAccessFile", "readBytes");
        builder.allowBlockingCallsInside("org.springframework.asm.ClassReader", "readStream");
        builder.allowBlockingCallsInside("org.springdoc.core.service.OpenAPIService", "getApiDefClass");
    }
}
