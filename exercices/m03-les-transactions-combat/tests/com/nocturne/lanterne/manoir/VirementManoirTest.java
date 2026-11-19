package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nocturne.lanterne.virement.Compte;
import com.nocturne.lanterne.virement.CompteIntrouvableException;
import com.nocturne.lanterne.virement.CompteRepository;
import com.nocturne.lanterne.virement.MouvementRepository;
import com.nocturne.lanterne.virement.PlafondDepasseException;
import com.nocturne.lanterne.virement.VirementService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Combat 03.4 du manoir : un virement se fait en entier, ou pas du tout. */
@SpringBootTest
class VirementManoirTest {

    /** Empreinte du test d'intégration fourni, espaces et retours à la ligne ignorés. */
    private static final String TEST_FOURNI = "4698f3d9889c17a4bf43c43b21ecfd7169cbef826e982e4ae5640a1dbff3c19c";

    @Autowired
    VirementService service;

    @Autowired
    CompteRepository comptes;

    @Autowired
    MouvementRepository mouvements;

    Compte ada;
    Compte linus;

    @BeforeEach
    void preparer() {
        mouvements.deleteAll();
        comptes.deleteAll();
        ada = comptes.save(new Compte("Ada", new BigDecimal("5000.00")));
        linus = comptes.save(new Compte("Linus", new BigDecimal("0.00")));
    }

    private BigDecimal solde(Compte compte) {
        return comptes.findById(compte.getId()).orElseThrow().getSolde();
    }

    @Test
    void un_virement_reussi_enregistre_le_debit_et_le_credit() throws Exception {
        service.virer(ada.getId(), linus.getId(), new BigDecimal("200.00"));

        assertThat(solde(ada)).isEqualByComparingTo("4800.00");
        assertThat(solde(linus)).isEqualByComparingTo("200.00");
        assertThat(mouvements.findByCompteId(ada.getId())).singleElement()
                .satisfies(m -> assertThat(m.getMontant()).isEqualByComparingTo("-200.00"));
        assertThat(mouvements.findByCompteId(linus.getId())).singleElement()
                .satisfies(m -> assertThat(m.getMontant()).isEqualByComparingTo("200.00"));
    }

    @Test
    void exception_verifiee_le_plafond_annule_tout() {
        assertThatThrownBy(() -> service.virer(ada.getId(), linus.getId(), new BigDecimal("1000.01")))
                .as("au-dessus du plafond, virer() doit toujours lever PlafondDepasseException")
                .isInstanceOf(PlafondDepasseException.class);

        assertThat(mouvements.count())
                .as("PlafondDepasseException est une exception vérifiée : par défaut, @Transactional ne l'annule pas")
                .isZero();
        assertThat(solde(ada)).isEqualByComparingTo("5000.00");
    }

    @Test
    void appel_interne_un_compte_introuvable_annule_tout() {
        assertThatThrownBy(() -> service.virer(ada.getId(), 999_999L, new BigDecimal("100.00")))
                .isInstanceOf(CompteIntrouvableException.class);

        assertThat(mouvements.count())
                .as("le débit est resté alors que le crédit a échoué : la transaction ne couvre pas virer() "
                        + "(une méthode appelée depuis la même classe ne passe pas par le proxy de Spring)")
                .isZero();
        assertThat(solde(ada)).isEqualByComparingTo("5000.00");
    }

    @Test
    void le_plafond_est_toujours_de_1000_euros() throws Exception {
        service.virer(ada.getId(), linus.getId(), new BigDecimal("1000.00"));
        assertThat(solde(linus)).as("1000 € pile reste autorisé").isEqualByComparingTo("1000.00");
    }

    @Test
    void le_test_d_integration_fourni_n_a_pas_change() throws Exception {
        String source = Files.readString(Path.of("src/test/java/com/nocturne/lanterne/virement/VirementIntegrationTest.java"));
        byte[] empreinte = MessageDigest.getInstance("SHA-256")
                .digest(source.replaceAll("\\s", "").getBytes(StandardCharsets.UTF_8));
        assertThat(HexFormat.of().formatHex(empreinte))
                .as("VirementIntegrationTest a été modifié : remets-le tel quel et corrige le service")
                .isEqualTo(TEST_FOURNI);
    }
}
