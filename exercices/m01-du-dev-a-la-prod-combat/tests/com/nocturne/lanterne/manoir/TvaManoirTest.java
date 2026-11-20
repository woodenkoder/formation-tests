package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;

import com.nocturne.lanterne.prix.Tva;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Combat 01.5 du manoir : le bug de exercices/hotfix.md est corrigé (TVA à 20 %). */
class TvaManoirTest {

    @Test
    void cent_euros_ht_donnent_cent_vingt_euros_ttc() {
        assertThat(Tva.ttc(new BigDecimal("100.00"))).isEqualByComparingTo("120.00");
        assertThat(Tva.ttc(new BigDecimal("9.99"))).isEqualByComparingTo("11.99");
    }
}
