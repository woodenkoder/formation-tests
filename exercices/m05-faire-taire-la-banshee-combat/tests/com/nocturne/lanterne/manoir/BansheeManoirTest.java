package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Combat 05.3 du manoir : faire taire la Banshee (des erreurs claires, au format standard, sans fuite). */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class BansheeManoirTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Environment env;

    @Test
    void une_commande_absente_renvoie_404_problem_json() throws Exception {
        mvc.perform(get("/api/commandes/424242"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void annuler_une_commande_expediee_renvoie_409() throws Exception {
        mvc.perform(post("/api/commandes/2/annulation"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void une_donnee_invalide_renvoie_400_problem_json() throws Exception {
        mvc.perform(post("/api/commandes").contentType(MediaType.APPLICATION_JSON).content("{\"client\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void une_annulation_possible_marche_toujours() throws Exception {
        mvc.perform(post("/api/commandes/3/annulation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULEE"));
    }

    @Test
    void une_erreur_inattendue_renvoie_500_sans_rien_reveler_et_part_dans_les_logs(CapturedOutput logs) throws Exception {
        String corps = mvc.perform(get("/api/commandes/1/facture"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();
        assertThat(corps)
                .as("La réponse d'une erreur inattendue ne doit rien révéler de l'intérieur de la Lanterne")
                .doesNotContain("IllegalStateException", "facturation.nocturne.internal", "pool épuisé", "at com.",
                        "\"trace\"", "java.");
        assertThat(logs.getAll())
                .as("L'erreur inattendue doit être journalisée avec sa stack trace (LOG.error(\"…\", e))")
                .contains("IllegalStateException", "facturation.nocturne.internal")
                .containsPattern("\\s+at com\\.nocturne\\.lanterne\\.");
    }

    @Test
    void les_problem_details_sont_actives() {
        assertThat(env.getProperty("spring.mvc.problemdetails.enabled"))
                .as("Active spring.mvc.problemdetails.enabled=true")
                .isEqualTo("true");
        assertThat(env.getProperty("server.error.include-stacktrace", "never"))
                .as("Ne renvoie jamais la stack trace au client : retire server.error.include-stacktrace=always")
                .isNotEqualTo("always");
    }
}
