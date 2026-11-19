package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Combat 05.1 du manoir : un CRUD qui respecte les conventions. */
@SpringBootTest
@AutoConfigureMockMvc
class CrudManoirTest {

    private static final List<String> VERBES = List.of("get", "create", "update", "delete", "add", "remove", "list");

    @Autowired
    MockMvc mvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping routes;

    /** Crée un produit et renvoie son adresse (en-tête Location). */
    private String creer(String nom) throws Exception {
        MvcResult r = mvc.perform(post("/api/produits").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\": \"" + nom + "\", \"prix\": 9.90}"))
                .andExpect(status().isCreated())
                .andReturn();
        String location = r.getResponse().getHeader("Location");
        assertThat(location).as("POST /api/produits doit renvoyer l'en-tête Location du produit créé").isNotBlank();
        assertThat(location).matches(".*/api/produits/\\d+$");
        return location.substring(location.indexOf("/api/produits/"));
    }

    @Test
    void aucune_url_ne_contient_de_verbe() {
        List<String> fautives = routes.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .filter(url -> url.startsWith("/api/produits"))
                .filter(url -> VERBES.stream().anyMatch(v -> url.toLowerCase(Locale.ROOT).contains("/" + v)))
                .toList();
        assertThat(fautives).as("Le verbe HTTP dit l'action : ces URL ne doivent contenir que des noms").isEmpty();
    }

    @Test
    void lister_les_produits() throws Exception {
        mvc.perform(get("/api/produits")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }

    @Test
    void creer_renvoie_201_et_location_vers_le_produit() throws Exception {
        String adresse = creer("Bougie du manoir");
        mvc.perform(get(adresse)).andExpect(status().isOk()).andExpect(jsonPath("$.nom").value("Bougie du manoir"));
    }

    @Test
    void modifier_avec_put() throws Exception {
        String adresse = creer("Ancien nom");
        mvc.perform(put(adresse).contentType(MediaType.APPLICATION_JSON).content("{\"nom\": \"Nouveau nom\", \"prix\": 5.00}"))
                .andExpect(status().isOk());
        mvc.perform(get(adresse)).andExpect(jsonPath("$.nom").value("Nouveau nom"));
    }

    @Test
    void supprimer_renvoie_204_puis_le_produit_est_absent() throws Exception {
        String adresse = creer("Éphémère");
        mvc.perform(delete(adresse)).andExpect(status().isNoContent());
        mvc.perform(get(adresse)).andExpect(status().isNotFound());
    }

    @Test
    void un_produit_absent_renvoie_404_en_lecture_modification_et_suppression() throws Exception {
        mvc.perform(get("/api/produits/987654")).andExpect(status().isNotFound());
        mvc.perform(put("/api/produits/987654").contentType(MediaType.APPLICATION_JSON).content("{\"nom\": \"x\", \"prix\": 1}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/produits/987654")).andExpect(status().isNotFound());
    }

    @Test
    void les_tests_du_controller_sont_a_jour() throws Exception {
        String source = Files.readString(Path.of("src/test/java/com/nocturne/lanterne/produit/ProduitControllerTest.java"));
        assertThat(source).as("Garde des tests @WebMvcTest pour le controller").contains("@WebMvcTest");
        assertThat(source).as("ProduitControllerTest appelle encore les anciennes URL").doesNotContain("/getAll", "/get/",
                "/create", "/update/", "/delete/");
    }
}
