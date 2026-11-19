package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

/**
 * Combat 06.2 du manoir : tes tests de CommandeService attrapent-ils les bugs ?
 * On fabrique des « mutants » (le vrai service, avec un bug introduit exprès), et on relance TES tests sur chacun :
 * au moins un de tes tests doit échouer pour chaque mutant.
 */
class MockitoManoirTest {

    private static final String SERVICE = "com.nocturne.lanterne.commande.CommandeService";
    private static final String TEST = "com.nocturne.lanterne.commande.CommandeServiceTest";
    private static final Path SOURCE_SERVICE = Path.of("src/main/java/com/nocturne/lanterne/commande/CommandeService.java");
    private static final Path SOURCE_TEST = Path.of("src/test/java/com/nocturne/lanterne/commande/CommandeServiceTest.java");
    /** Empreinte du service fourni, espaces ignorés. */
    private static final String SERVICE_FOURNI = "47061467c924d4eb39a03c88451fd64f0d5b03dd8d303b0118dfa1aae2df20a0";

    private static final Map<String, String[]> MUTANTS = new LinkedHashMap<>();

    static {
        MUTANTS.put("le stock n'est plus vérifié",
                new String[] {"if (!stock.disponible(commande)) {", "if (false && !stock.disponible(commande)) {"});
        MUTANTS.put("la commande est enregistrée même sans stock",
                new String[] {"throw new StockInsuffisantException(commande);",
                        "repository.enregistrer(commande);\n            throw new StockInsuffisantException(commande);"});
        MUTANTS.put("plus de seuil : tout est validé directement",
                new String[] {"commande.montant().compareTo(SEUIL_VALIDATION) > 0", "false"});
        MUTANTS.put("tout part en validation", new String[] {"commande.montant().compareTo(SEUIL_VALIDATION) > 0", "true"});
        MUTANTS.put("500 € pile part en validation (le seuil est « au-delà de 500 € »)",
                new String[] {"commande.montant().compareTo(SEUIL_VALIDATION) > 0",
                        "commande.montant().compareTo(SEUIL_VALIDATION) >= 0"});
        MUTANTS.put("une grosse commande est aussi confirmée au client",
                new String[] {"notifications.alerterValidation(aValider);",
                        "notifications.alerterValidation(aValider);\n            notifications.confirmer(aValider);"});
        MUTANTS.put("plus d'alerte de validation", new String[] {"notifications.alerterValidation(aValider);", ""});
        MUTANTS.put("plus de confirmation au client", new String[] {"notifications.confirmer(validee);", ""});
        MUTANTS.put("une grosse commande reçoit le statut VALIDEE",
                new String[] {"commande.avecStatut(\"A_VALIDER\")", "commande.avecStatut(\"VALIDEE\")"});
    }

    private static String original() throws Exception {
        return Files.readString(SOURCE_SERVICE).replace("\r\n", "\n");
    }

    @Test
    void le_service_fourni_n_a_pas_change() throws Exception {
        byte[] empreinte = MessageDigest.getInstance("SHA-256")
                .digest(original().replaceAll("\\s", "").getBytes(StandardCharsets.UTF_8));
        assertThat(HexFormat.of().formatHex(empreinte))
                .as("Ne modifie pas CommandeService : le combat consiste à écrire ses tests")
                .isEqualTo(SERVICE_FOURNI);
    }

    @Test
    void les_tests_sont_unitaires_et_testent_le_vrai_service() throws Exception {
        assertThat(SOURCE_TEST).as("Écris tes tests dans " + SOURCE_TEST).exists();
        String source = Files.readString(SOURCE_TEST);
        assertThat(source).as("Des tests unitaires : pas de contexte Spring")
                .doesNotContain("@SpringBootTest", "@MockitoBean", "@MockBean", "SpringExtension");
        assertThat(source).as("Le service testé doit être le vrai (@InjectMocks ou new), pas une doublure")
                .doesNotContainPattern("@(Mock|Spy)\\s+(private\\s+|final\\s+)*CommandeService\\b")
                .doesNotContain("mock(CommandeService.class)", "spy(CommandeService.class)");
        assertThat(source.lines().filter(l -> l.trim().startsWith("@Test")).count())
                .as("au moins un test par règle (3 règles)").isGreaterThanOrEqualTo(3);
    }

    @Test
    void tes_tests_passent_sur_le_vrai_service() throws Exception {
        assertThat(lancer(original()))
                .as("Tes tests doivent passer sur le service fourni (on lance les méthodes @Test, avec @BeforeEach, "
                        + "@AfterEach et les annotations Mockito ; pas de @Nested ni de @ParameterizedTest)")
                .isEmpty();
    }

    @Test
    void tes_tests_attrapent_chaque_mutant() throws Exception {
        String original = original();
        List<String> survivants = new ArrayList<>();
        for (Map.Entry<String, String[]> mutant : MUTANTS.entrySet()) {
            String[] remplacement = mutant.getValue();
            assertThat(original).contains(remplacement[0]);
            if (lancer(original.replace(remplacement[0], remplacement[1])).isEmpty()) {
                survivants.add(mutant.getKey());
            }
        }
        assertThat(survivants)
                .as("Ces bugs passent inaperçus : ajoute les vérifications qui les attraperaient (verify, never(), "
                        + "le statut, le cas limite…)")
                .isEmpty();
    }

    /** Compile cette version du service et y lance les tests de l'élève. Renvoie les tests en échec. */
    private static List<String> lancer(String sourceService) throws Exception {
        Path dossier = Files.createTempDirectory("mutant");
        Path paquet = dossier.resolve("src/com/nocturne/lanterne/commande");
        Files.createDirectories(paquet);
        String sansSpring = sourceService.replace("import org.springframework.stereotype.Service;", "")
                .replace("@Service", "");
        Path fichier = paquet.resolve("CommandeService.java");
        Files.writeString(fichier, sansSpring);
        Path classes = dossier.resolve("classes");
        Files.createDirectories(classes);
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        int compile = javac.run(null, null, null, "-proc:none", "-encoding", "UTF-8", "-cp", "target/classes", "-d",
                classes.toString(), fichier.toString());
        assertThat(compile).as("compilation du service").isZero();

        URL[] urls = {classes.toUri().toURL(), new File("target/test-classes").toURI().toURL()};
        List<String> echecs = new ArrayList<>();
        try (Chargeur chargeur = new Chargeur(urls, MockitoManoirTest.class.getClassLoader())) {
            Class<?> tests = chargeur.loadClass(TEST);
            for (Method test : tests.getDeclaredMethods()) {
                if (!test.isAnnotationPresent(Test.class)) {
                    continue;
                }
                Constructor<?> constructeur = tests.getDeclaredConstructor();
                constructeur.setAccessible(true);
                Object instance = constructeur.newInstance();
                AutoCloseable mocks = utiliseMockito(tests) ? MockitoAnnotations.openMocks(instance) : () -> {};
                try {
                    appeler(tests, instance, BeforeEach.class);
                    test.setAccessible(true);
                    test.invoke(instance);
                    appeler(tests, instance, AfterEach.class);
                } catch (InvocationTargetException e) {
                    echecs.add(test.getName() + " : " + e.getCause());
                } finally {
                    mocks.close();
                }
            }
        }
        return echecs;
    }

    private static boolean utiliseMockito(Class<?> tests) {
        for (Field champ : tests.getDeclaredFields()) {
            if (champ.isAnnotationPresent(Mock.class) || champ.isAnnotationPresent(InjectMocks.class)
                    || champ.isAnnotationPresent(Spy.class)) {
                return true;
            }
        }
        return false;
    }

    private static void appeler(Class<?> tests, Object instance, Class<? extends java.lang.annotation.Annotation> quand)
            throws Exception {
        for (Method m : tests.getDeclaredMethods()) {
            if (m.isAnnotationPresent(quand)) {
                m.setAccessible(true);
                m.invoke(instance);
            }
        }
    }

    /** Charge le service (mutant) et les tests de l'élève lui-même ; le reste vient de l'application. */
    private static final class Chargeur extends URLClassLoader {

        Chargeur(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String nom, boolean resoudre) throws ClassNotFoundException {
            boolean ici = nom.equals(SERVICE) || nom.startsWith(SERVICE + "$") || nom.equals(TEST)
                    || nom.startsWith(TEST + "$");
            if (!ici) {
                return super.loadClass(nom, resoudre);
            }
            synchronized (getClassLoadingLock(nom)) {
                Class<?> classe = findLoadedClass(nom);
                if (classe == null) {
                    classe = findClass(nom);
                }
                if (resoudre) {
                    resolveClass(classe);
                }
                return classe;
            }
        }
    }
}
