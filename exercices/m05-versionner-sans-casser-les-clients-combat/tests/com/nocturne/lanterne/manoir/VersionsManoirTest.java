package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Combat 05.4 du manoir : une v2 sans casser les clients de la v1. */
@SpringBootTest
@AutoConfigureMockMvc
class VersionsManoirTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext contexte;

    @Test
    void contrat_v1_identique_a_avant() throws Exception {
        mvc.perform(get("/api/v1/commandes/1"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"id": 1, "client": "ada@exemple.test", "montant": 50.00, "fraisPort": 4.90, "statut": "EN_ATTENTE"}
                        """, true));
        mvc.perform(get("/api/v1/commandes/2"))
                .andExpect(content().json("""
                        {"id": 2, "client": "linus@exemple.test", "montant": 125.00, "fraisPort": 0.00, "statut": "EXPEDIEE"}
                        """, true));
        mvc.perform(get("/api/v1/commandes/999")).andExpect(status().isNotFound());
    }

    @Test
    void v2_donne_le_ttc_et_la_livraison() throws Exception {
        mvc.perform(get("/api/v2/commandes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.montantTtc").value(60.0))
                .andExpect(jsonPath("$.livraison.mode").value("COLISSIMO"))
                .andExpect(jsonPath("$.livraison.prix").value(4.9))
                .andExpect(jsonPath("$.montant").doesNotExist())
                .andExpect(jsonPath("$.fraisPort").doesNotExist());
        mvc.perform(get("/api/v2/commandes/2")).andExpect(jsonPath("$.montantTtc").value(150.0));
        mvc.perform(get("/api/v2/commandes/999")).andExpect(status().isNotFound());
    }

    @Test
    void v1_est_marquee_depreciee_avec_une_date_de_retrait() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/commandes/1"))
                .andExpect(header().exists("Deprecation"))
                .andExpect(header().exists("Sunset"))
                .andReturn();
        ZonedDateTime retrait = ZonedDateTime.parse(r.getResponse().getHeader("Sunset"), DateTimeFormatter.RFC_1123_DATE_TIME);
        assertThat(retrait).as("la date de retrait (Sunset) est dans le futur").isAfter(ZonedDateTime.now());
    }

    @Test
    void sunset_au_format_date_http() throws Exception {
        String sunset = mvc.perform(get("/api/v1/commandes/2")).andReturn().getResponse().getHeader("Sunset");
        assertThat(sunset).as("Sunset au format date HTTP, par exemple « Mon, 31 May 2027 00:00:00 GMT »")
                .matches("[A-Z][a-z]{2}, \\d{2} [A-Z][a-z]{2} \\d{4} \\d{2}:\\d{2}:\\d{2} GMT");
    }

    @Test
    void v2_n_est_pas_depreciee() throws Exception {
        mvc.perform(get("/api/v2/commandes/1"))
                .andExpect(header().doesNotExist("Deprecation"))
                .andExpect(header().doesNotExist("Sunset"));
    }

    @Test
    void un_seul_service_des_commandes() {
        Map<String, Object> services = contexte.getBeansWithAnnotation(Service.class);
        assertThat(services.values().stream().map(b -> b.getClass().getSimpleName()).filter(n -> n.contains("Commande")))
                .as("v1 et v2 réutilisent le même service : ne le duplique pas")
                .hasSize(1);
    }
}
