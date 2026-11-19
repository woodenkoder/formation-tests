package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/** Combat 03.2 du manoir : l'injection par constructeur. */
@SpringBootTest
@AutoConfigureMockMvc
class InjectionManoirTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path TESTS = Path.of("src/test/java/com/nocturne/lanterne/commande");

    @Autowired
    MockMvc mvc;

    /** Toutes les classes de src/main/java. */
    private static List<Class<?>> classes() throws IOException, ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> fichiers = Files.walk(MAIN)) {
            for (Path fichier : fichiers.filter(f -> f.toString().endsWith(".java")).toList()) {
                String nom = MAIN.relativize(fichier).toString().replace('/', '.').replace('\\', '.');
                classes.add(Class.forName(nom.substring(0, nom.length() - ".java".length())));
            }
        }
        return classes;
    }

    @Test
    void aucun_autowired_sur_un_champ() throws Exception {
        List<String> champs = new ArrayList<>();
        for (Class<?> classe : classes()) {
            for (Field champ : classe.getDeclaredFields()) {
                if (champ.isAnnotationPresent(Autowired.class)) {
                    champs.add(classe.getSimpleName() + "." + champ.getName());
                }
            }
        }
        assertThat(champs)
                .as("Ces champs sont encore injectés avec @Autowired : passe-les par le constructeur")
                .isEmpty();
    }

    @Test
    void les_dependances_des_beans_sont_final() throws Exception {
        List<String> champs = new ArrayList<>();
        for (Class<?> classe : classes()) {
            if (!AnnotatedElementUtils.hasAnnotation(classe, Component.class)) {
                continue;
            }
            for (Field champ : classe.getDeclaredFields()) {
                int m = champ.getModifiers();
                if (!Modifier.isStatic(m) && !Modifier.isFinal(m) && !champ.isSynthetic()) {
                    champs.add(classe.getSimpleName() + "." + champ.getName());
                }
            }
        }
        assertThat(champs)
                .as("Ces champs de beans ne sont pas final : une dépendance reçue par le constructeur ne change plus")
                .isEmpty();
    }

    @Test
    void les_tests_des_services_tournent_sans_spring() throws Exception {
        for (String nom : List.of("CommandeServiceTest.java", "FideliteServiceTest.java")) {
            Path fichier = TESTS.resolve(nom);
            assertThat(fichier).as("Garde ton test " + nom + " (dans src/test/java/com/nocturne/lanterne/commande)").exists();
            assertThat(Files.readString(fichier))
                    .as(nom + " démarre encore Spring : crée le service avec new et ses dépendances avec mock(...)")
                    .doesNotContain("@SpringBootTest", "@MockitoBean", "@MockBean", "@Autowired", "SpringExtension",
                            "@ContextConfiguration");
        }
    }

    @Test
    void l_application_demarre_et_l_api_repond() throws Exception {
        mvc.perform(post("/api/commandes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client\": \"injection@exemple.test\", \"lignes\": [{\"produitId\": 1, \"quantite\": 2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(25.0));
    }
}
