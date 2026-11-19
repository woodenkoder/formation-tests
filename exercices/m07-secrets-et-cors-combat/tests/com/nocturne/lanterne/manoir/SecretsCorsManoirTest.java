package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.nocturne.lanterne.LanterneApplication;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Combat 07.4 du manoir : aucun secret dans le code, CORS limité aux origines configurées. */
@SpringBootTest(properties = {"nocturne.cors.origines=https://nocturne.example",
        "nocturne.paiement.url=https://paiement.exemple.test", "nocturne.paiement.cle=cle-de-test"})
@AutoConfigureMockMvc
class SecretsCorsManoirTest {

    private static final Path MAIN = Path.of("src/main");
    private static final Pattern SECRET_JAVA =
            Pattern.compile("(?i)\\b\\w*(cle|key|secret|token|password|motdepasse)\\w*\\s*=\\s*\"([^\"]{12,})\"");
    private static final Pattern SECRET_CONFIG =
            Pattern.compile("(?i)^\\s*(?:[\\w.\\-]*[.\\-_])?(password|secret|cle|api[-_]?key|token)\\s*[:=]\\s*(.*)$");

    @Autowired
    MockMvc mvc;

    private static List<Path> fichiers(Path racine, String motif) throws IOException {
        if (!Files.exists(racine)) {
            return List.of();
        }
        try (Stream<Path> f = Files.walk(racine)) {
            return f.filter(Files::isRegularFile).filter(p -> p.toString().matches(motif)).toList();
        }
    }

    private static MockHttpServletRequestBuilder preflight(String origine) {
        return options("/api/commandes").header("Origin", origine).header("Access-Control-Request-Method", "POST");
    }

    @Test
    void aucun_secret_en_clair_dans_src_main() throws Exception {
        List<String> secrets = new ArrayList<>();
        for (Path f : fichiers(MAIN, ".*\\.(java|properties|ya?ml)$")) {
            List<String> lignes = Files.readAllLines(f);
            for (int i = 0; i < lignes.size(); i++) {
                String ligne = lignes.get(i);
                Matcher java = SECRET_JAVA.matcher(ligne);
                Matcher config = SECRET_CONFIG.matcher(ligne);
                String valeur = config.matches() ? config.group(2).trim().replaceAll("^[\"']|[\"']$", "") : "";
                if (ligne.contains("pay-live-3c9f1e7a5b2d4c6e") || (f.toString().endsWith(".java") && java.find())
                        || (!f.toString().endsWith(".java") && config.matches() && !valeur.isEmpty()
                                && !valeur.startsWith("${"))) {
                    secrets.add(f.getFileName() + ":" + (i + 1) + " " + ligne.trim());
                }
            }
        }
        assertThat(secrets).as("Secrets écrits en clair : lis-les dans l'environnement (${PAIEMENT_CLE})").isEmpty();
    }

    @Test
    void une_origine_non_autorisee_ne_recoit_pas_d_access_control_allow_origin() throws Exception {
        mvc.perform(preflight("https://pirate.example")).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(preflight("http://localhost:6666")).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void une_origine_configuree_le_recoit() throws Exception {
        mvc.perform(preflight("https://nocturne.example"))
                .andExpect(header().string("Access-Control-Allow-Origin", "https://nocturne.example"));
    }

    @Test
    void sans_cle_de_paiement_la_lanterne_refuse_de_demarrer() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(LanterneApplication.class)
                        .web(WebApplicationType.NONE)
                        .run("--nocturne.cors.origines=https://nocturne.example",
                                "--nocturne.paiement.url=https://paiement.exemple.test", "--nocturne.paiement.cle=")
                        .close())
                .as("Avec nocturne.paiement.cle vide, une classe @ConfigurationProperties validée (@NotBlank) doit "
                        + "empêcher le démarrage")
                .hasStackTraceContaining("nocturne.paiement");
    }

    @Test
    void les_origines_sont_differentes_en_dev_et_en_prod() throws Exception {
        assertThat(origines("dev")).as("Définis nocturne.cors.origines dans application-dev").isPresent();
        assertThat(origines("prod")).as("Définis nocturne.cors.origines dans application-prod").isPresent();
        assertThat(origines("dev")).as("dev et prod n'autorisent pas les mêmes origines").isNotEqualTo(origines("prod"));
    }

    private static Optional<String> origines(String profil) throws IOException {
        for (Path f : fichiers(MAIN.resolve("resources"), ".*application-" + profil + "\\.(properties|ya?ml)$")) {
            for (String ligne : Files.readAllLines(f)) {
                Matcher m = Pattern.compile("^\\s*(?:nocturne\\.cors\\.)?origines\\s*[:=]\\s*(\\S.*)$").matcher(ligne);
                if (m.matches()) {
                    return Optional.of(m.group(1).trim());
                }
            }
        }
        return Optional.empty();
    }

    @Test
    void la_ci_lance_gitleaks() throws Exception {
        boolean gitleaks = false;
        for (Path f : fichiers(Path.of(".github/workflows"), ".*\\.ya?ml$")) {
            gitleaks |= !f.getFileName().toString().equals("manoir.yml") && Files.readString(f).contains("gitleaks");
        }
        assertThat(gitleaks).as("Ajoute gitleaks au workflow de CI (.github/workflows/ci.yml)").isTrue();
    }
}
