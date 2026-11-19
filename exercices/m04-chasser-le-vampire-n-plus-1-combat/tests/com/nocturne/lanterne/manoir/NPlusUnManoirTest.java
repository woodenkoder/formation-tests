package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nocturne.lanterne.admin.Client;
import com.nocturne.lanterne.admin.ClientRepository;
import com.nocturne.lanterne.admin.Commande;
import com.nocturne.lanterne.admin.CommandeRepository;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

/** Combat 04.3 du manoir : chasser le vampire N+1. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class NPlusUnManoirTest {

    private static final String TEST_FOURNI = "01ab95607a68d520b67790f89a1ef34a4ce670741fd00bb4ff87ca6089ab524d";

    @Autowired
    MockMvc mvc;

    @Autowired
    ClientRepository clients;

    @Autowired
    CommandeRepository commandes;

    @Autowired
    EntityManagerFactory emf;

    @Autowired
    Environment env;

    @Test
    void la_reponse_de_l_endpoint_ne_change_pas() throws Exception {
        LocalDate jour = LocalDate.of(2026, 2, 20);
        Client ada = clients.save(new Client("Ada", "ada@exemple.test"));
        Client linus = clients.save(new Client("Linus", "linus@exemple.test"));
        Commande c1 = commandes.save(new Commande(ada, jour, new BigDecimal("12.50")));
        Commande c2 = commandes.save(new Commande(linus, jour, new BigDecimal("30.00")));
        commandes.save(new Commande(ada, jour.plusDays(1), new BigDecimal("4.90")));

        mvc.perform(get("/api/admin/commandes").param("date", "2026-02-20"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [
                          {"id": %d, "date": "2026-02-20", "montant": 12.50, "client": "Ada", "email": "ada@exemple.test"},
                          {"id": %d, "date": "2026-02-20", "montant": 30.00, "client": "Linus", "email": "linus@exemple.test"}
                        ]
                        """.formatted(c1.getId(), c2.getId()), true));
    }

    @Test
    void une_journee_vide_renvoie_une_liste_vide() throws Exception {
        mvc.perform(get("/api/admin/commandes").param("date", "1999-01-01"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", true));
    }

    @Test
    void cinquante_commandes_en_deux_requetes_au_plus() throws Exception {
        LocalDate jour = LocalDate.of(2026, 3, 1);
        for (int i = 0; i < 50; i++) {
            Client client = clients.save(new Client("Manoir " + i, "manoir" + i + "@exemple.test"));
            commandes.save(new Commande(client, jour, BigDecimal.ONE));
        }
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        mvc.perform(get("/api/admin/commandes").param("date", "2026-03-01")).andExpect(status().isOk());
        assertThat(stats.getPrepareStatementCount()).as("requêtes SQL pour 50 commandes").isLessThanOrEqualTo(2);
    }

    @Test
    void la_relation_client_reste_lazy() throws Exception {
        ManyToOne relation = Commande.class.getDeclaredField("client").getAnnotation(ManyToOne.class);
        assertThat(relation).isNotNull();
        assertThat(relation.fetch())
                .as("Commande.client doit rester en FetchType.LAZY : EAGER chargerait le client partout, même inutile")
                .isEqualTo(FetchType.LAZY);
    }

    @Test
    void open_in_view_est_desactive() {
        assertThat(env.getProperty("spring.jpa.open-in-view"))
                .as("Ajoute spring.jpa.open-in-view=false : sinon le N+1 peut se cacher dans l'écriture de la réponse")
                .isEqualTo("false");
    }

    @Test
    void le_test_fourni_n_a_pas_change() throws Exception {
        String source = Files.readString(Path.of("src/test/java/com/nocturne/lanterne/admin/CommandesAdminRequetesTest.java"));
        byte[] empreinte = MessageDigest.getInstance("SHA-256")
                .digest(source.replaceAll("\\s", "").getBytes(StandardCharsets.UTF_8));
        assertThat(HexFormat.of().formatHex(empreinte))
                .as("CommandesAdminRequetesTest a été modifié : remets-le tel quel et corrige le chargement")
                .isEqualTo(TEST_FOURNI);
    }
}
