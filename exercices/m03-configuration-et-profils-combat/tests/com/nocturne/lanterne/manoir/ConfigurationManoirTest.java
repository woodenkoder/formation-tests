package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nocturne.lanterne.LanterneApplication;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.validation.annotation.Validated;

/** Combat 03.5 du manoir : variables d'environnement, profils dev et prod. */
class ConfigurationManoirTest {

    private static final Path RESSOURCES = Path.of("src/main/resources");
    private static final String PROPRIETES = "com.nocturne.lanterne.paiement.PaiementProperties";
    /** Une clé de secret (password, secret, cle, api-key, token) et sa valeur. */
    private static final Pattern SECRET =
            Pattern.compile("(?i)^\\s*(?:[\\w.\\-]*[.\\-_])?(password|secret|cle|api[-_]?key|token)\\s*[:=]\\s*(.*)$");
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)");

    private static List<Path> fichiersDeConfiguration() throws IOException {
        try (Stream<Path> fichiers = Files.walk(RESSOURCES)) {
            return fichiers.filter(f -> f.toString().matches(".*\\.(properties|ya?ml)$")).toList();
        }
    }

    private static Path profil(String nom) {
        Path yml = RESSOURCES.resolve("application-" + nom + ".yml");
        return Files.exists(yml) ? yml : RESSOURCES.resolve("application-" + nom + ".yaml");
    }

    /** Démarre la Lanterne en profil prod, avec une base H2 et la configuration de paiement donnée. */
    private static ConfigurableApplicationContext demarrerEnProd(String url, String cle) throws IOException {
        Map<String, String> variables = new LinkedHashMap<>();
        Path exemple = Path.of(".env.example");
        if (Files.exists(exemple)) {
            for (String ligne : Files.readAllLines(exemple)) {
                int egal = ligne.indexOf('=');
                if (egal > 0 && !ligne.trim().startsWith("#")) {
                    String valeur = ligne.substring(egal + 1).trim();
                    variables.put(ligne.substring(0, egal).trim(), valeur.isEmpty() ? "manoir" : valeur);
                }
            }
        }
        variables.forEach(System::setProperty);
        try {
            return new SpringApplicationBuilder(LanterneApplication.class)
                    .web(WebApplicationType.NONE)
                    .profiles("prod")
                    .run("--spring.datasource.url=jdbc:h2:mem:manoir", "--spring.datasource.username=sa",
                            "--spring.datasource.password=", "--lanterne.paiement.url=" + url,
                            "--lanterne.paiement.cle=" + cle);
        } finally {
            variables.keySet().forEach(System::clearProperty);
        }
    }

    @Test
    void aucun_secret_en_clair_dans_la_configuration() throws Exception {
        List<String> secrets = new ArrayList<>();
        for (Path fichier : fichiersDeConfiguration()) {
            List<String> lignes = Files.readAllLines(fichier);
            for (int i = 0; i < lignes.size(); i++) {
                Matcher m = SECRET.matcher(lignes.get(i));
                if (m.matches()) {
                    String valeur = m.group(2).trim().replaceAll("^[\"']|[\"']$", "");
                    if (!valeur.isEmpty() && !valeur.startsWith("${")) {
                        secrets.add(fichier.getFileName() + ":" + (i + 1) + " " + lignes.get(i).trim());
                    }
                }
                if (lignes.get(i).contains("Lanterne-Prod-2024!") || lignes.get(i).contains("pay-live-7f3a9c2e1b8d4f60")) {
                    secrets.add(fichier.getFileName() + ":" + (i + 1) + " (l'ancien secret)");
                }
            }
        }
        assertThat(secrets)
                .as("Des secrets sont écrits en clair : remplace-les par ${VARIABLE} (même en dev : ${DB_PASSWORD:…})")
                .isEmpty();
    }

    @Test
    void les_profils_dev_et_prod_existent() {
        assertThat(profil("dev")).as("Crée src/main/resources/application-dev.yml").exists();
        assertThat(profil("prod")).as("Crée src/main/resources/application-prod.yml").exists();
    }

    @Test
    void la_configuration_du_paiement_est_regroupee_et_validee() throws Exception {
        Class<?> proprietes = Class.forName(PROPRIETES);
        ConfigurationProperties config =
                AnnotatedElementUtils.findMergedAnnotation(proprietes, ConfigurationProperties.class);
        assertThat(config).as("PaiementProperties doit porter @ConfigurationProperties").isNotNull();
        assertThat(config.prefix()).as("le préfixe attendu est lanterne.paiement").isEqualTo("lanterne.paiement");
        assertThat(AnnotatedElementUtils.hasAnnotation(proprietes, Validated.class))
                .as("PaiementProperties doit porter @Validated, sinon rien n'est vérifié au démarrage")
                .isTrue();
    }

    @Test
    void en_prod_la_lanterne_refuse_de_demarrer_sans_configuration_de_paiement() {
        assertThatThrownBy(() -> demarrerEnProd("", "cle-de-test").close())
                .as("En prod, avec lanterne.paiement.url vide, la Lanterne doit refuser de démarrer (@NotBlank)")
                .hasStackTraceContaining("lanterne.paiement");
    }

    @Test
    void en_prod_la_lanterne_demarre_quand_tout_est_fourni() throws Exception {
        try (ConfigurableApplicationContext contexte = demarrerEnProd("https://paiement.exemple.test", "cle-de-test")) {
            assertThat(contexte.getBeansOfType(Class.forName(PROPRIETES))).hasSize(1);
        }
    }

    @Test
    void env_est_ignore_et_env_example_liste_toutes_les_variables() throws Exception {
        Path gitignore = Path.of(".gitignore");
        assertThat(gitignore).exists();
        assertThat(Files.readAllLines(gitignore).stream().map(String::trim))
                .as("Ajoute .env au .gitignore : tes vrais secrets ne doivent jamais partir sur GitHub")
                .containsAnyOf(".env", "/.env");

        Path exemple = Path.of(".env.example");
        assertThat(exemple).as("Crée .env.example à la racine (les noms des variables, sans les vraies valeurs)").exists();
        Set<String> listees = new TreeSet<>();
        for (String ligne : Files.readAllLines(exemple)) {
            if (ligne.contains("=") && !ligne.trim().startsWith("#")) {
                listees.add(ligne.substring(0, ligne.indexOf('=')).trim());
            }
        }
        Set<String> utilisees = new TreeSet<>();
        for (Path fichier : fichiersDeConfiguration()) {
            Matcher m = VARIABLE.matcher(Files.readString(fichier));
            while (m.find()) {
                utilisees.add(m.group(1));
            }
        }
        assertThat(utilisees).as("Les variables utilisées dans ta configuration").isNotEmpty();
        assertThat(listees).as(".env.example doit lister toutes les variables utilisées").containsAll(utilisees);
    }
}
