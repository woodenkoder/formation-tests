package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Combat 06.5 du manoir : le parcours d'achat décrit en français, et vérifié contre la vraie API. */
class BddManoirTest {

    private static final Path RESSOURCES = Path.of("src/test/resources");
    private static final Path RAPPORT = Path.of("target/manoir-cucumber.json");

    private static List<Path> fichiers(Path racine, String suffixe) throws IOException {
        if (!Files.exists(racine)) {
            return List.of();
        }
        try (Stream<Path> f = Files.walk(racine)) {
            return f.filter(p -> p.toString().endsWith(suffixe)).toList();
        }
    }

    private static String scenarios() throws IOException {
        StringBuilder tout = new StringBuilder();
        for (Path f : fichiers(RESSOURCES, ".feature")) {
            tout.append(Files.readString(f)).append('\n');
        }
        return tout.toString();
    }

    @Test
    void achat_feature_decrit_deux_scenarios_en_francais() throws Exception {
        String texte = scenarios();
        assertThat(texte).as("Écris achat.feature dans src/test/resources/features, en français (# language: fr)")
                .contains("# language: fr");
        long scenarios = texte.lines().map(String::trim)
                .filter(l -> l.startsWith("Scénario") || l.startsWith("Scenario") || l.startsWith("Exemple"))
                .count();
        assertThat(scenarios).as("au moins deux scénarios : la commande payée et le paiement refusé")
                .isGreaterThanOrEqualTo(2);
        assertThat(texte).as("le scénario payé vérifie le statut PAYEE").contains("PAYEE");
        assertThat(texte).as("le scénario refusé vérifie le statut et le message")
                .contains("EN_ATTENTE_DE_PAIEMENT", "Paiement refusé");
    }

    @Test
    void aucune_attente_fixe_dans_les_tests() throws Exception {
        List<String> attentes = new ArrayList<>();
        for (Path f : fichiers(Path.of("src/test/java"), ".java")) {
            if (!f.toString().contains("manoir") && Files.readString(f).contains("Thread.sleep")) {
                attentes.add(f.getFileName().toString());
            }
        }
        assertThat(attentes).as("Pas de Thread.sleep : attends un état (une réponse, un statut), pas une durée").isEmpty();
    }

    @Test
    void les_scenarios_passent_contre_la_lanterne() throws Exception {
        Files.deleteIfExists(RAPPORT);
        byte statut = io.cucumber.core.cli.Main.run(new String[] {"--glue", "com.nocturne.lanterne", "--plugin",
                "json:" + RAPPORT, "--monochrome", RESSOURCES.toString()}, BddManoirTest.class.getClassLoader());

        assertThat(RAPPORT).as("Cucumber n'a produit aucun rapport").exists();
        JsonNode rapport = new ObjectMapper().readTree(RAPPORT.toFile());
        int scenarios = 0;
        List<String> problemes = new ArrayList<>();
        for (JsonNode fonctionnalite : rapport) {
            for (JsonNode element : fonctionnalite.path("elements")) {
                if (!"scenario".equals(element.path("type").asText())) {
                    continue;
                }
                scenarios++;
                for (JsonNode etape : element.path("steps")) {
                    String resultat = etape.path("result").path("status").asText();
                    if (!"passed".equals(resultat)) {
                        problemes.add(element.path("name").asText() + " → « " + etape.path("name").asText() + " » : "
                                + resultat);
                    }
                }
            }
        }
        assertThat(problemes).as("Des étapes ne passent pas (undefined = étape Java manquante)").isEmpty();
        assertThat(scenarios).as("scénarios exécutés").isGreaterThanOrEqualTo(2);
        assertThat(statut).as("Cucumber a terminé en échec").isZero();
    }
}
