package com.nocturne.lanterne.manoir;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nocturne.lanterne.commande.CommandeService;
import com.nocturne.lanterne.commande.StatutCommande;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Combat 12.3 du manoir : la règle du ticket LAN-42, vérifiée par l'API. */
@SpringBootTest
@AutoConfigureMockMvc
class AnnulationManoirTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    CommandeService service;

    long commande() throws Exception {
        String corps = "{\"client\":\"ada@exemple.test\",\"lignes\":[{\"produitId\":1,\"quantite\":1}]}";
        String reponse = mvc.perform(post("/api/commandes").contentType(MediaType.APPLICATION_JSON).content(corps))
                .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(reponse).path("id").asLong();
    }

    @Test
    void une_commande_en_attente_ou_payee_est_annulee() throws Exception {
        mvc.perform(post("/api/commandes/{id}/annulation", commande()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("ANNULEE"));
        long payee = commande();
        service.avancer(payee, StatutCommande.PAYEE);
        mvc.perform(post("/api/commandes/{id}/annulation", payee)).andExpect(status().isOk());
    }

    @Test
    void une_commande_expediee_ou_livree_ne_peut_plus_l_etre() throws Exception {
        for (StatutCommande statut : new StatutCommande[] {StatutCommande.EXPEDIEE, StatutCommande.LIVREE}) {
            long id = commande();
            service.avancer(id, statut);
            mvc.perform(post("/api/commandes/{id}/annulation", id))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }
    }

    @Test
    void une_commande_inconnue_renvoie_404() throws Exception {
        mvc.perform(post("/api/commandes/{id}/annulation", 987654)).andExpect(status().isNotFound());
    }
}
