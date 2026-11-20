package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;

import com.nocturne.lanterne.frais.FraisPort;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Combat 01.3 du manoir : après le conflit, les DEUX règles de livraison gratuite s'appliquent. */
class FraisPortManoirTest {

    FraisPort frais = new FraisPort();

    @Test
    void livraison_gratuite_des_50_euros() {
        assertThat(frais.frais(new BigDecimal("50.00"), false)).isEqualByComparingTo("0");
        assertThat(frais.frais(new BigDecimal("80.00"), false)).isEqualByComparingTo("0");
    }

    @Test
    void livraison_gratuite_pour_un_client_premium() {
        assertThat(frais.frais(new BigDecimal("12.00"), true)).isEqualByComparingTo("0");
    }

    @Test
    void frais_standard_sous_50_euros_pour_les_autres() {
        assertThat(frais.frais(new BigDecimal("49.99"), false)).isEqualByComparingTo("4.90");
    }
}
