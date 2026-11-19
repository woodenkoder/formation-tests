package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
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
import java.util.stream.Stream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Combat 10.4 du manoir : les tests écrits avec l'IA protègent-ils vraiment la règle des remises ?
 * Nos mutants (RemiseService avec un bug glissé exprès) doivent être attrapés à 80 % au moins par tes tests.
 */
class TestsIaManoirTest {

    private static final String SERVICE = "com.nocturne.lanterne.remise.RemiseService";
    private static final String TEST = "com.nocturne.lanterne.remise.RemiseServiceTest";
    private static final Path SOURCE_SERVICE = Path.of("src/main/java/com/nocturne/lanterne/remise/RemiseService.java");
    private static final Path SOURCE_TEST = Path.of("src/test/java/com/nocturne/lanterne/remise/RemiseServiceTest.java");
    private static final String SERVICE_FOURNI = "c6a3dcaca32031ce9b85242b54113b1ff20dfb80d2c00b9a1f2d6e577d503b50";
    private static final Map<String, String[]> MUTANTS = new LinkedHashMap<>();

    static {
        MUTANTS.put("50 € pile n'a plus de remise", new String[] {"compareTo(SEUIL_BRONZE) >= 0", "compareTo(SEUIL_BRONZE) > 0"});
        MUTANTS.put("5 % devient 4 %", new String[] {"taux = 5;", "taux = 4;"});
        MUTANTS.put("100 € pile reste à 5 %", new String[] {"compareTo(SEUIL_ARGENT) >= 0", "compareTo(SEUIL_ARGENT) > 0"});
        MUTANTS.put("10 % devient 9 %", new String[] {"taux = 10;", "taux = 9;"});
        MUTANTS.put("il faut 6 commandes pour être fidèle", new String[] {"commandesPassees >= CLIENT_FIDELE", "commandesPassees > CLIENT_FIDELE"});
        MUTANTS.put("la fidélité ne rapporte plus rien", new String[] {"taux += 5;", "taux += 0;"});
        MUTANTS.put("le code promo est ignoré", new String[] {"CODE_PROMO.equals(codePromo)", "false"});
        MUTANTS.put("le code promo ne vaut que 5 points", new String[] {"taux += 10;", "taux += 5;"});
        MUTANTS.put("plus de plafond à 20 %", new String[] {"taux = Math.min(taux, PLAFOND);", ""});
        MUTANTS.put("l'arrondi tronque au lieu d'arrondir", new String[] {"RoundingMode.HALF_UP", "RoundingMode.DOWN"});
    }

    private static String original() throws Exception {
        return Files.readString(SOURCE_SERVICE).replace("\r\n", "\n");
    }

    @Test
    void le_service_des_remises_n_a_pas_change() throws Exception {
        byte[] empreinte = MessageDigest.getInstance("SHA-256")
                .digest(original().replaceAll("\\s", "").getBytes(StandardCharsets.UTF_8));
        assertThat(HexFormat.of().formatHex(empreinte))
                .as("Ne modifie pas RemiseService : le combat consiste à écrire ses tests").isEqualTo(SERVICE_FOURNI);
    }

    @Test
    void aucun_test_creux() throws Exception {
        assertThat(SOURCE_TEST).exists();
        assertThat(Files.readString(SOURCE_TEST))
                .as("Garde seulement les tests qui vérifient une valeur exacte (pas de isNotNull ni de assertTrue(true))")
                .doesNotContain("isNotNull()", "assertNotNull(", "assertTrue(true)", "isTrue();\n    }");
    }

    @Test
    void couverture_et_mutation_sont_exigees_par_le_build_et_la_ci() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        assertThat(pom).as("JaCoCo vérifie 80 % de couverture sur les remises (jacoco-maven-plugin, règle 0.80)")
                .contains("jacoco-maven-plugin").containsPattern("<minimum>0\\.80?</minimum>");
        assertThat(pom).as("PIT vérifie RemiseService avec un seuil de 80 % (pitest-maven, mutationThreshold 80)")
                .contains("pitest-maven").contains("<mutationThreshold>80</mutationThreshold>");
        StringBuilder workflows = new StringBuilder();
        if (Files.isDirectory(Path.of(".github/workflows"))) {
            try (Stream<Path> f = Files.list(Path.of(".github/workflows"))) {
                for (Path w : f.toList()) {
                    workflows.append(Files.readString(w));
                }
            }
        }
        assertThat(workflows.toString()).as("La CI lance PIT (mutationCoverage)").containsAnyOf("mutationCoverage", "pitest");
    }

    @Test
    void tes_tests_passent_sur_le_vrai_service() throws Exception {
        assertThat(lancer(original())).as("Tes tests doivent passer sur RemiseService").isEmpty();
    }

    @Test
    void tes_tests_attrapent_80_pourcent_des_mutants() throws Exception {
        String original = original();
        List<String> survivants = new ArrayList<>();
        for (Map.Entry<String, String[]> mutant : MUTANTS.entrySet()) {
            assertThat(original).contains(mutant.getValue()[0]);
            if (lancer(original.replace(mutant.getValue()[0], mutant.getValue()[1])).isEmpty()) {
                survivants.add(mutant.getKey());
            }
        }
        int tues = MUTANTS.size() - survivants.size();
        assertThat(tues * 100 / MUTANTS.size())
                .as("Score de mutation " + tues + "/" + MUTANTS.size() + " ; bugs non attrapés : " + survivants)
                .isGreaterThanOrEqualTo(80);
    }

    /** Compile cette version du service et y lance les tests de l'élève (@Test et @ParameterizedTest + @CsvSource). */
    private static List<String> lancer(String source) throws Exception {
        Path dossier = Files.createTempDirectory("mutant");
        Path fichier = dossier.resolve("src/com/nocturne/lanterne/remise/RemiseService.java");
        Files.createDirectories(fichier.getParent());
        Files.writeString(fichier, source.replace("import org.springframework.stereotype.Service;", "").replace("@Service", ""));
        Path classes = Files.createDirectories(dossier.resolve("classes"));
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-proc:none", "-encoding", "UTF-8", "-cp",
                "target/classes", "-d", classes.toString(), fichier.toString())).isZero();
        URL[] urls = {classes.toUri().toURL(), new File("target/test-classes").toURI().toURL()};
        List<String> echecs = new ArrayList<>();
        try (Chargeur chargeur = new Chargeur(urls, TestsIaManoirTest.class.getClassLoader())) {
            Class<?> tests = chargeur.loadClass(TEST);
            for (Method m : tests.getDeclaredMethods()) {
                List<Object[]> cas = new ArrayList<>();
                if (m.isAnnotationPresent(Test.class)) {
                    cas.add(new Object[0]);
                } else if (m.isAnnotationPresent(ParameterizedTest.class) && m.isAnnotationPresent(CsvSource.class)) {
                    for (String ligne : m.getAnnotation(CsvSource.class).value()) {
                        cas.add(arguments(ligne, m.getParameterTypes()));
                    }
                }
                for (Object[] arguments : cas) {
                    Constructor<?> c = tests.getDeclaredConstructor();
                    c.setAccessible(true);
                    Object instance = c.newInstance();
                    try {
                        appeler(tests, instance, BeforeEach.class);
                        m.setAccessible(true);
                        m.invoke(instance, arguments);
                        appeler(tests, instance, AfterEach.class);
                    } catch (InvocationTargetException e) {
                        echecs.add(m.getName() + " : " + e.getCause());
                    }
                }
            }
        }
        return echecs;
    }

    private static Object[] arguments(String ligne, Class<?>[] types) {
        List<String> valeurs = new ArrayList<>();
        StringBuilder courante = new StringBuilder();
        boolean citation = false;
        for (char ch : ligne.toCharArray()) {
            if (ch == '\'') {
                citation = !citation;
            } else if (ch == ',' && !citation) {
                valeurs.add(courante.toString().trim());
                courante.setLength(0);
            } else {
                courante.append(ch);
            }
        }
        valeurs.add(courante.toString().trim());
        Object[] resultat = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            String v = i < valeurs.size() ? valeurs.get(i) : "";
            Class<?> t = types[i];
            resultat[i] = v.isEmpty() && !t.isPrimitive() ? null
                    : t == int.class || t == Integer.class ? Integer.valueOf(v)
                    : t == long.class || t == Long.class ? Long.valueOf(v)
                    : t == double.class || t == Double.class ? Double.valueOf(v)
                    : t == boolean.class || t == Boolean.class ? Boolean.valueOf(v)
                    : t == BigDecimal.class ? new BigDecimal(v) : v;
        }
        return resultat;
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

    private static final class Chargeur extends URLClassLoader {

        Chargeur(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String nom, boolean resoudre) throws ClassNotFoundException {
            boolean ici = nom.equals(SERVICE) || nom.startsWith(SERVICE + "$") || nom.equals(TEST) || nom.startsWith(TEST + "$");
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
