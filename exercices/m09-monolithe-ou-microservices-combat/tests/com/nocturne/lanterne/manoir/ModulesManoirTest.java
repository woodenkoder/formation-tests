package com.nocturne.lanterne.manoir;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Combat 09.4 du manoir : des domaines qui ne se touchent que par leur API publique. */
@SpringBootTest
@AutoConfigureMockMvc
class ModulesManoirTest {

    private static final String PAIEMENT = "com.nocturne.lanterne.paiement";
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.nocturne.lanterne");

    @Autowired
    MockMvc mvc;

    private ResultActions commander(String client, String montant) throws Exception {
        return mvc.perform(post("/api/commandes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"client\": \"" + client + "\", \"montant\": " + montant + "}"));
    }

    @Test
    void aucun_domaine_n_utilise_l_interne_d_un_autre() {
        for (String domaine : List.of("paiement", "catalogue", "commande")) {
            String racine = "com.nocturne.lanterne." + domaine;
            noClasses().that().resideOutsideOfPackage(racine + "..")
                    .should().dependOnClassesThat().resideInAPackage(racine + ".(*)..")
                    .because("un domaine n'utilise que l'API publique (le package racine) de " + domaine)
                    .check(CLASSES);
        }
    }

    @Test
    void le_paiement_n_expose_que_son_service_et_ses_dto() {
        List<String> exposes = CLASSES.stream()
                .filter(c -> c.getPackageName().equals(PAIEMENT))
                .filter(c -> c.getSimpleName().equals("Paiement") || c.getSimpleName().endsWith("Repository")
                        || c.isAnnotatedWith(Repository.class))
                .map(JavaClass::getSimpleName)
                .toList();
        assertThat(exposes)
                .as("L'entité et le repository du paiement sont internes : range-les dans un sous-package "
                        + "(par exemple paiement.interne)")
                .isEmpty();
    }

    @Test
    void les_commandes_passent_par_paiement_service() {
        boolean utiliseLeService = CLASSES.stream()
                .filter(c -> c.getPackageName().startsWith("com.nocturne.lanterne.commande"))
                .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
                .anyMatch(d -> d.getTargetClass().getFullName().equals(PAIEMENT + ".PaiementService"));
        assertThat(utiliseLeService).as("Les commandes demandent le paiement à PaiementService").isTrue();
    }

    /** Les règles @ArchTest de l'élève (champs ArchRule et méthodes prenant JavaClasses), appliquées à ces classes. */
    private static List<String> reglesDeLEleve(JavaClasses classes) throws Exception {
        Class<?> test = Class.forName("com.nocturne.lanterne.ArchitectureTest");
        List<String> violations = new ArrayList<>();
        int regles = 0;
        for (Field champ : test.getDeclaredFields()) {
            if (champ.isAnnotationPresent(ArchTest.class) && ArchRule.class.isAssignableFrom(champ.getType())
                    && Modifier.isStatic(champ.getModifiers())) {
                champ.setAccessible(true);
                regles++;
                ArchRule regle = (ArchRule) champ.get(null);
                if (regle.evaluate(classes).hasViolation()) {
                    violations.add(champ.getName());
                }
            }
        }
        for (Method methode : test.getDeclaredMethods()) {
            if (methode.isAnnotationPresent(ArchTest.class) && methode.getParameterCount() == 1
                    && methode.getParameterTypes()[0] == JavaClasses.class) {
                methode.setAccessible(true);
                regles++;
                Object instance = Modifier.isStatic(methode.getModifiers()) ? null : instancier(test);
                try {
                    methode.invoke(instance, classes);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    violations.add(methode.getName());
                }
            }
        }
        assertThat(regles).as("ArchitectureTest doit contenir au moins une règle ArchUnit @ArchTest").isPositive();
        return violations;
    }

    private static Object instancier(Class<?> classe) throws Exception {
        var constructeur = classe.getDeclaredConstructor();
        constructeur.setAccessible(true);
        return constructeur.newInstance();
    }

    @Test
    void ton_test_d_architecture_passe() throws Exception {
        assertThat(Path.of("src/test/java/com/nocturne/lanterne/ArchitectureTest.java"))
                .as("Écris ton test d'architecture ArchUnit dans src/test/java/com/nocturne/lanterne/ArchitectureTest.java")
                .exists();
        assertThat(reglesDeLEleve(CLASSES)).as("Tes règles d'architecture doivent passer sur ton code").isEmpty();
    }

    @Test
    void ton_test_d_architecture_attrape_un_acces_direct_a_l_interne_du_paiement() throws Exception {
        JavaClass interne = CLASSES.stream()
                .filter(c -> c.getPackageName().startsWith(PAIEMENT + ".") && !c.isAnonymousClass() && c.getModifiers()
                        .contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC))
                .findFirst()
                .orElse(null);
        assertThat(interne).as("Range l'interne du paiement dans un sous-package public (par exemple paiement.interne)")
                .isNotNull();
        // Un mutant : une classe des commandes qui utilise directement une classe interne du paiement.
        Path dossier = Files.createTempDirectory("fuite");
        Path source = dossier.resolve("src/com/nocturne/lanterne/commande/FuiteManoir.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package com.nocturne.lanterne.commande;\n"
                + "class FuiteManoir { Object fuite(" + interne.getFullName().replace('$', '.') + " x) { return x; } }\n");
        Path classes = Files.createDirectories(dossier.resolve("classes"));
        int compile = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-proc:none", "-cp", "target/classes",
                "-d", classes.toString(), source.toString());
        assertThat(compile).isZero();
        JavaClasses avecFuite = new ClassFileImporter().importPaths(Path.of("target/classes"), classes);
        assertThat(reglesDeLEleve(avecFuite))
                .as("Ton test d'architecture doit échouer si les commandes utilisent " + interne.getSimpleName()
                        + " directement")
                .isNotEmpty();
    }

    @Test
    void l_api_marche_toujours() throws Exception {
        commander("ada@exemple.test", "42.00").andExpect(status().isCreated()).andExpect(jsonPath("$.statut").value("PAYEE"));
        commander("ada@exemple.test", "1500.00").andExpect(jsonPath("$.statut").value("PAIEMENT_REFUSE"));
        commander("fraude@exemple.test", "10.00").andExpect(status().isForbidden());
    }
}
