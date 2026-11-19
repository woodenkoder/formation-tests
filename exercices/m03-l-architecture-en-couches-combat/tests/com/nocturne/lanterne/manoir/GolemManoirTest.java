package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Combat 03.1 du manoir : sortir la logique du controller (le Golem). */
@SpringBootTest
@AutoConfigureMockMvc
class GolemManoirTest {

    private static final String PAQUET = "com.nocturne.lanterne.commande.";
    private static final Path SOURCES = Path.of("src/main/java/com/nocturne/lanterne/commande");
    private static final Path TESTS = Path.of("src/test/java/com/nocturne/lanterne/commande");

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext contexte;

    private ResultActions commander(String client, String lignes) throws Exception {
        return mvc.perform(post("/api/commandes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"client\": \"" + client + "\", \"lignes\": " + lignes + "}"));
    }

    private static String client() {
        return "client-" + UUID.randomUUID() + "@exemple.test";
    }

    @Test
    void l_api_calcule_toujours_le_total() throws Exception {
        commander(client(), "[{\"produitId\": 1, \"quantite\": 2}, {\"produitId\": 2, \"quantite\": 1}]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.total").value(55.0));
    }

    @Test
    void l_api_applique_toujours_la_remise_fidelite_a_la_quatrieme_commande() throws Exception {
        String client = client();
        String lignes = "[{\"produitId\": 1, \"quantite\": 2}, {\"produitId\": 2, \"quantite\": 1}]";
        for (int i = 0; i < 3; i++) {
            commander(client, lignes).andExpect(jsonPath("$.total").value(55.0));
        }
        commander(client, lignes).andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(49.5));
    }

    @Test
    void un_produit_inconnu_est_toujours_refuse() throws Exception {
        commander(client(), "[{\"produitId\": 999, \"quantite\": 1}]").andExpect(status().isBadRequest());
    }

    @Test
    void le_controller_ne_parle_plus_au_repository_ni_au_catalogue() throws Exception {
        Class<?> controller = Class.forName(PAQUET + "CommandeController");
        List<String> dependances = new ArrayList<>();
        for (Field champ : controller.getDeclaredFields()) {
            dependances.add(champ.getType().getSimpleName());
        }
        for (Constructor<?> constructeur : controller.getDeclaredConstructors()) {
            for (Class<?> parametre : constructeur.getParameterTypes()) {
                dependances.add(parametre.getSimpleName());
            }
        }
        assertThat(dependances)
                .as("CommandeController dépend encore du repository ou du catalogue : il doit passer par CommandeService")
                .noneMatch(nom -> nom.contains("Repository") || nom.equals("Catalogue"));
    }

    @Test
    void le_controller_ne_calcule_plus_de_montant() throws Exception {
        String source = Files.readString(SOURCES.resolve("CommandeController.java"));
        assertThat(source)
                .as("CommandeController calcule encore un montant : ce calcul appartient à CommandeService")
                .doesNotContain("multiply(", ".add(", "setScale(", "0.90", "compterParClient");
    }

    @Test
    void commande_service_porte_la_logique() throws Exception {
        Class<?> service = Class.forName(PAQUET + "CommandeService");
        assertThat(contexte.getBeansOfType(service))
                .as("CommandeService doit être un bean Spring (@Service)")
                .hasSize(1);
        boolean recoitLeRepository = false;
        for (Constructor<?> constructeur : service.getDeclaredConstructors()) {
            for (Class<?> parametre : constructeur.getParameterTypes()) {
                recoitLeRepository |= parametre.getSimpleName().equals("CommandeRepository");
            }
        }
        assertThat(recoitLeRepository)
                .as("CommandeService doit recevoir CommandeRepository par son constructeur (pour le simuler dans ses tests)")
                .isTrue();
    }

    @Test
    void commande_service_a_au_moins_trois_tests_unitaires_sans_spring() throws Exception {
        Path fichier = TESTS.resolve("CommandeServiceTest.java");
        assertThat(fichier)
                .as("Crée tes tests unitaires dans src/test/java/com/nocturne/lanterne/commande/CommandeServiceTest.java")
                .exists();
        String source = Files.readString(fichier);
        long tests = source.lines().filter(l -> l.trim().startsWith("@Test")).count();
        assertThat(tests).as("CommandeServiceTest doit contenir au moins trois tests (@Test)").isGreaterThanOrEqualTo(3);
        assertThat(source)
                .as("Des tests UNITAIRES : new CommandeService(...) avec un repository simulé, sans démarrer Spring")
                .doesNotContain("@SpringBootTest", "@MockitoBean", "@MockBean", "SpringExtension");
    }
}
