package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/** Combat 05.5 du manoir : documenter l'API et limiter le débit par clé. */
@SpringBootTest
@AutoConfigureMockMvc
class DocDebitManoirTest {

    @Autowired
    MockMvc mvc;

    private static String cle() {
        return "cle-" + UUID.randomUUID();
    }

    @Test
    void le_contrat_openapi_decrit_la_lecture_d_une_commande() throws Exception {
        mvc.perform(get("/v3/api-docs").header("X-Api-Key", cle()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/commandes/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/commandes/{id}'].get.parameters[?(@.in == 'path')]").isNotEmpty());
    }

    @Test
    void les_erreurs_des_commandes_sont_documentees() throws Exception {
        mvc.perform(get("/v3/api-docs").header("X-Api-Key", cle()))
                .andExpect(jsonPath("$.paths['/api/v1/commandes/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/commandes/{id}/annulation'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/commandes'].post.responses['400']").exists());
    }

    @Test
    void l_api_repond_toujours() throws Exception {
        mvc.perform(get("/api/v1/commandes/1").header("X-Api-Key", cle())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/commandes/424242").header("X-Api-Key", cle())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/commandes/2/annulation").header("X-Api-Key", cle())).andExpect(status().isConflict());
    }

    @Test
    void la_21e_requete_immediate_d_une_cle_recoit_429_avec_retry_after() throws Exception {
        String cle = cle();
        List<Integer> statuts = new ArrayList<>();
        MockHttpServletResponse refusee = null;
        // 20 requêtes d'un coup sont permises ; la recharge (5 par seconde) ne peut pas suivre 30 requêtes immédiates.
        for (int i = 0; i < 30; i++) {
            MockHttpServletResponse r = mvc.perform(get("/api/v1/commandes/1").header("X-Api-Key", cle))
                    .andReturn().getResponse();
            statuts.add(r.getStatus());
            if (r.getStatus() == 429 && refusee == null) {
                refusee = r;
            }
        }
        assertThat(statuts.subList(0, 20)).as("les 20 premières requêtes d'une clé passent").containsOnly(200);
        assertThat(refusee).as("au-delà de 20 requêtes immédiates, une clé reçoit 429").isNotNull();
        assertThat(refusee.getHeader("Retry-After")).as("un 429 dit quand réessayer (en-tête Retry-After)").isNotBlank();
    }

    @Test
    void une_autre_cle_n_est_pas_limitee() throws Exception {
        String bavarde = cle();
        for (int i = 0; i < 30; i++) {
            mvc.perform(get("/api/v1/commandes/1").header("X-Api-Key", bavarde));
        }
        mvc.perform(get("/api/v1/commandes/1").header("X-Api-Key", cle())).andExpect(status().isOk());
    }
}
