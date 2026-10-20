package co.com.bancoias.r2dbc.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import io.r2dbc.spi.R2dbcNonTransientResourceException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PreApprovedUsageR2dbcAdapterTest {

    @Test
    void detectsUniqueViolationInCauseChain() {
        DataIntegrityViolationException error = new DataIntegrityViolationException(
                "duplicate key", new R2dbcNonTransientResourceException("duplicate key", "23505"));

        assertThat(PreApprovedUsageR2dbcAdapter.isUniqueViolation(error)).isTrue();
    }

    @Test
    void ignoresOtherIntegrityViolations() {
        DataIntegrityViolationException error = new DataIntegrityViolationException(
                "foreign key", new R2dbcNonTransientResourceException("foreign key", "23503"));

        assertThat(PreApprovedUsageR2dbcAdapter.isUniqueViolation(error)).isFalse();
    }
}
