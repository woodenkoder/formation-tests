package com.nocturne.lanterne.manoir;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Tests cachés du manoir, combat 03.3 « La validation sur un DTO ». Ils envoient à ta Lanterne les commandes de la
 * scène, comme un vrai client : POST /api/commandes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ValidationCommandeManoirTest {

    @Autowired
    MockMvc mvc;

    private ResultActions commander(String json) throws Exception {
        return mvc.perform(post("/api/commandes").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void refusee(String json, String champ) throws Exception {
        commander(json)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(Matchers.containsString(champ)));
    }

    @Test
    void une_commande_valide_est_toujours_creee() throws Exception {
        commander("""
                {"client": "ada@exemple.test", "lignes": [{"produitId": 1, "quantite": 2}], "codePromo": "BIENVENUE10"}
                """).andExpect(status().isCreated());
    }

    @Test
    void une_commande_sans_client_est_refusee() throws Exception {
        refusee("""
                {"lignes": [{"produitId": 1, "quantite": 2}]}
                """, "client");
    }

    @Test
    void une_commande_sans_ligne_est_refusee() throws Exception {
        refusee("""
                {"client": "ada@exemple.test", "lignes": []}
                """, "lignes");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -3, 100})
    void une_quantite_hors_de_1_a_99_est_refusee(int quantite) throws Exception {
        refusee("""
                {"client": "ada@exemple.test", "lignes": [{"produitId": 1, "quantite": %d}]}
                """.formatted(quantite), "quantite");
    }

    @Test
    void un_code_promo_de_plus_de_20_caracteres_est_refuse() throws Exception {
        refusee("""
                {"client": "ada@exemple.test", "lignes": [{"produitId": 1, "quantite": 1}], "codePromo": "%s"}
                """.formatted("X".repeat(21)), "codePromo");
    }

    @Test
    void un_code_promo_de_20_caracteres_est_accepte() throws Exception {
        commander("""
                {"client": "ada@exemple.test", "lignes": [{"produitId": 1, "quantite": 99}], "codePromo": "%s"}
                """.formatted("X".repeat(20))).andExpect(status().isCreated());
    }
}
