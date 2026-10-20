package co.com.bancoias.model.preapprovedusage;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PayloadHasherTest {

    @Test
    void normalizesWhitespaceAndAmountScale() {
        String first = PayloadHasher.hash(" REF-1 ", "PRA-1", "USR-10", new BigDecimal("100.00"));
        String second = PayloadHasher.hash("REF-1", " PRA-1 ", "USR-10", new BigDecimal("100"));

        assertThat(first).isEqualTo(second).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void changesWhenPayloadChanges() {
        String base = PayloadHasher.hash("REF-1", "PRA-1", "USR-10", new BigDecimal("100"));

        assertThat(PayloadHasher.hash("REF-2", "PRA-1", "USR-10", new BigDecimal("100")))
                .isNotEqualTo(base);
        assertThat(PayloadHasher.hash("REF-1", "PRA-2", "USR-10", new BigDecimal("100")))
                .isNotEqualTo(base);
        assertThat(PayloadHasher.hash("REF-1", "PRA-1", "USR-20", new BigDecimal("100")))
                .isNotEqualTo(base);
        assertThat(PayloadHasher.hash("REF-1", "PRA-1", "USR-10", new BigDecimal("100.01")))
                .isNotEqualTo(base);
    }
}
