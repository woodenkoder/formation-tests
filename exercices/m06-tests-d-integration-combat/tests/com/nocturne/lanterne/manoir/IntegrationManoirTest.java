package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Combat 06.4 du manoir : le projet teste son repository contre un vrai PostgreSQL. */
class IntegrationManoirTest {

    private static final Path IT = Path.of("src/test/java/com/nocturne/lanterne/commande/CommandeRepositoryIT.java");

    @Test
    void h2_n_est_plus_une_dependance() throws Exception {
        assertThat(Files.readString(Path.of("pom.xml")))
                .as("Retire H2 du pom.xml : les tests tournent sur le même moteur que la prod")
                .doesNotContain("<artifactId>h2</artifactId>");
    }

    @Test
    void testcontainers_et_failsafe_sont_installes() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        assertThat(pom).as("Ajoute spring-boot-testcontainers et org.testcontainers:postgresql (scope test)")
                .contains("<artifactId>spring-boot-testcontainers</artifactId>", "<artifactId>postgresql</artifactId>");
        assertThat(pom).as("Ajoute maven-failsafe-plugin pour lancer les *IT dans ./mvnw verify")
                .contains("<artifactId>maven-failsafe-plugin</artifactId>");
    }

    @Test
    void commande_repository_it_demarre_postgres_17_et_teste_le_doublon() throws Exception {
        assertThat(IT).as("Écris " + IT).exists();
        String source = Files.readString(IT);
        assertThat(source).as("CommandeRepositoryIT démarre un conteneur postgres:17 relié par @ServiceConnection")
                .contains("PostgreSQLContainer", "postgres:17", "@ServiceConnection");
        assertThat(source).as("Le test du doublon de référence attend une DataIntegrityViolationException")
                .contains("DataIntegrityViolationException");
        assertThat(source.lines().filter(l -> l.trim().startsWith("@Test")).count())
                .as("au moins 3 tests : migrations, tri par date, doublon").isGreaterThanOrEqualTo(3);
    }
}
