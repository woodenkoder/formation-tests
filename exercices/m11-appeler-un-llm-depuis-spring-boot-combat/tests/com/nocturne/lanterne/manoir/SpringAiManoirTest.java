package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Combat 11.1 du manoir : l'Oracle répond parfois juste, parfois n'importe quoi. La Lanterne ne plante jamais. */
@SpringBootTest(properties = "spring.ai.openai.api-key=cle-de-test")
@AutoConfigureMockMvc
@Import(SpringAiManoirTest.Oracle.class)
class SpringAiManoirTest {

    static final String BON = """
            {"resume": "Commande de 55 € avec un grimoire abîmé.", "pointsDAttention": ["grimoire déchiré"],
             "litigePossible": true}""";

    /** Un faux modèle : il répond, dans l'ordre, les textes donnés, et compte les appels. */
    static class FauxOracle implements ChatModel {
        final Deque<String> reponses = new ArrayDeque<>();
        final AtomicInteger appels = new AtomicInteger();

        void repondra(String... textes) {
            reponses.clear();
            reponses.addAll(List.of(textes));
            appels.set(0);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            appels.incrementAndGet();
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reponses.isEmpty() ? "" : reponses.poll()))));
        }
    }

    @TestConfiguration
    static class Oracle {
        @Bean
        @Primary
        FauxOracle fauxOracle() {
            return new FauxOracle();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    FauxOracle oracle;

    @Autowired
    MeterRegistry metriques;

    private ResultActions resumer(long id) throws Exception {
        return mvc.perform(post("/api/commandes/" + id + "/resume"));
    }

    private double invalides() {
        Counter compteur = metriques.find("lanterne.resume.invalides").counter();
        return compteur == null ? -1 : compteur.count();
    }

    @Test
    void une_reponse_correcte_donne_le_resume_structure() throws Exception {
        oracle.repondra(BON);
        resumer(1).andExpect(status().isOk())
                .andExpect(jsonPath("$.resume").value("Commande de 55 € avec un grimoire abîmé."))
                .andExpect(jsonPath("$.pointsDAttention[0]").value("grimoire déchiré"))
                .andExpect(jsonPath("$.litigePossible").value(true));
        assertThat(oracle.appels.get()).as("un seul appel quand tout va bien").isEqualTo(1);
    }

    @Test
    void une_reponse_illisible_puis_correcte_un_seul_nouvel_essai() throws Exception {
        oracle.repondra("Désolé, je ne peux pas répondre en JSON aujourd'hui.", BON);
        resumer(1).andExpect(status().isOk()).andExpect(jsonPath("$.litigePossible").value(true));
        assertThat(oracle.appels.get()).isEqualTo(2);
    }

    @Test
    void deux_reponses_illisibles_le_repli_jamais_une_erreur_500() throws Exception {
        oracle.repondra("n'importe quoi", "");
        resumer(1).andExpect(status().isOk()).andExpect(jsonPath("$.resume").value("Résumé indisponible"));
        assertThat(oracle.appels.get()).as("un nouvel essai, pas plus").isEqualTo(2);
    }

    @Test
    void un_json_invalide_selon_bean_validation_est_refuse() throws Exception {
        String vide = "{\"resume\": \"\", \"pointsDAttention\": [], \"litigePossible\": false}";
        oracle.repondra(vide, vide);
        resumer(2).andExpect(status().isOk()).andExpect(jsonPath("$.resume").value("Résumé indisponible"));
        assertThat(oracle.appels.get()).isEqualTo(2);
    }

    @Test
    void une_metrique_compte_les_reponses_invalides() throws Exception {
        assertThat(invalides()).as("Ajoute le compteur Micrometer lanterne.resume.invalides").isGreaterThanOrEqualTo(0);
        double avant = invalides();
        oracle.repondra("illisible", "toujours illisible");
        resumer(2).andExpect(status().isOk());
        assertThat(invalides() - avant).as("deux réponses invalides comptées").isEqualTo(2);
    }

    @Test
    void une_commande_absente_renvoie_404() throws Exception {
        oracle.repondra(BON);
        resumer(424242).andExpect(status().isNotFound());
    }

    @Test
    void la_cle_du_fournisseur_vient_de_l_environnement() throws Exception {
        String config = Files.readString(Path.of("src/main/resources/application.properties"));
        assertThat(config).as("spring.ai.openai.api-key doit être lue dans une variable : ${OPENAI_API_KEY}")
                .containsPattern("spring\\.ai\\.openai\\.api-key\\s*=\\s*\\$\\{")
                .doesNotContain("cle-de-demo-ecrite-en-dur");
    }
}
