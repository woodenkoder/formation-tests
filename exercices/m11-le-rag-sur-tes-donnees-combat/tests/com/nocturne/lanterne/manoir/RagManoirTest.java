package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Combat 11.2 du manoir : l'Oracle ne répond qu'avec la FAQ, cite ses sources, et sait se taire. */
@SpringBootTest(properties = "spring.ai.openai.api-key=cle-de-test")
@AutoConfigureMockMvc
@Import(RagManoirTest.Oracle.class)
class RagManoirTest {

    static final String JE_NE_SAIS_PAS = "Je ne sais pas, écris au support.";
    static final List<String> SECTIONS =
            List.of("Commandes et livraison", "Retours et remboursements", "Paiement", "Garantie");

    /** Un faux modèle : il répond toujours la même phrase et garde ce qu'on lui envoie. */
    static class FauxOracle implements ChatModel {
        final AtomicInteger appels = new AtomicInteger();
        final List<String> recus = new ArrayList<>();

        void oublier() {
            appels.set(0);
            recus.clear();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            appels.incrementAndGet();
            recus.add(prompt.getContents());
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Tu as 30 jours pour nous le renvoyer."))));
        }
    }

    @TestConfiguration
    static class Oracle {
        @Bean
        @Primary
        FauxOracle fauxOracle() {
            return new FauxOracle();
        }

        @Bean
        @Primary
        EmbeddingModel embeddingDuManoir() {
            return new EmbeddingDuManoir();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    FauxOracle oracle;

    @Autowired
    VectorStore vectorStore;

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode demander(String question) throws Exception {
        oracle.oublier();
        String corps = json.writeValueAsString(java.util.Map.of("question", question));
        String reponse = mvc.perform(post("/api/assistant").contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readTree(reponse);
    }

    private List<Document> toutLIndex() {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query("index")
                .topK(500)
                .similarityThresholdAll()
                .build());
    }

    @Test
    void le_delai_de_retour_cite_sa_source_et_donne_l_extrait_au_modele() throws Exception {
        JsonNode r = demander("Quel est le délai de retour ?");
        assertThat(r.path("reponse").asText()).isEqualTo("Tu as 30 jours pour nous le renvoyer.");
        List<String> sources = new ArrayList<>();
        r.path("sources").forEach(s -> sources.add(s.asText()));
        assertThat(sources).as("les sources de la réponse : la question de la FAQ sur le délai de retour")
                .anySatisfy(s -> assertThat(s).containsIgnoringCase("délai de retour"));
        assertThat(oracle.appels.get()).isEqualTo(1);
        assertThat(oracle.recus.get(0)).as("le modèle reçoit l'extrait de la FAQ").contains("30 jours après la réception");
    }

    @Test
    void une_question_hors_sujet_je_ne_sais_pas_sans_appeler_le_modele() throws Exception {
        JsonNode r = demander("Quelle est la capitale du Pérou ?");
        assertThat(r.path("reponse").asText()).isEqualTo(JE_NE_SAIS_PAS);
        assertThat(oracle.appels.get()).as("aucun appel au modèle sous le seuil de similarité").isZero();
    }

    @Test
    void un_morceau_par_question_de_la_faq() {
        assertThat(toutLIndex()).as("la FAQ compte 8 questions : un morceau par question").hasSize(8);
    }

    @Test
    void chaque_morceau_porte_le_titre_de_sa_section() {
        for (Document d : toutLIndex()) {
            assertThat(SECTIONS).as("titre de section dans le morceau : " + d.getText())
                    .anySatisfy(s -> assertThat(d.getText()).contains(s));
        }
        assertThat(toutLIndex()).filteredOn(d -> d.getText().contains("30 jours après la réception"))
                .singleElement()
                .satisfies(d -> assertThat(d.getText()).contains("Retours et remboursements"));
    }

    @Test
    void les_procedures_internes_ne_sont_pas_indexees() {
        assertThat(toutLIndex()).as("seul le dossier documents/faq/ est indexé")
                .noneSatisfy(d -> assertThat(d.getText()).containsIgnoringCase("GESTE-INTERNE"))
                .noneSatisfy(d -> assertThat(d.getText()).containsIgnoringCase("confidentiel"));
    }

    @Test
    void le_code_interne_ne_sort_jamais() throws Exception {
        JsonNode r = demander("Quel est le code de remise pour un geste commercial en cas de litige ?");
        assertThat(r.toString()).doesNotContain("GESTE-INTERNE");
        assertThat(String.join("\n", oracle.recus)).doesNotContain("GESTE-INTERNE");
        assertThat(r.path("reponse").asText()).isEqualTo(JE_NE_SAIS_PAS);
    }
}
