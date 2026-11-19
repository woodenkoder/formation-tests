package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Combat 09.2 du manoir : un faux transporteur, que l'on rend lent ou en panne, derrière un disjoncteur. */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class CircuitBreakerManoirTest {

    enum Mode { OK, PANNE, LENT }

    static final AtomicReference<Mode> MODE = new AtomicReference<>(Mode.OK);
    static final AtomicInteger APPELS = new AtomicInteger();
    static final HttpServer TRANSPORTEUR = demarrer();

    static HttpServer demarrer() {
        try {
            HttpServer serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            serveur.setExecutor(Executors.newCachedThreadPool());
            serveur.createContext("/delai", echange -> {
                APPELS.incrementAndGet();
                Mode mode = MODE.get();
                if (mode == Mode.LENT) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                byte[] corps = "{\"jours\": 2}".getBytes(StandardCharsets.UTF_8);
                echange.getResponseHeaders().add("Content-Type", "application/json");
                echange.sendResponseHeaders(mode == Mode.PANNE ? 500 : 200, mode == Mode.PANNE ? -1 : corps.length);
                if (mode != Mode.PANNE) {
                    echange.getResponseBody().write(corps);
                }
                echange.close();
            });
            serveur.start();
            return serveur;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void transporteur(DynamicPropertyRegistry proprietes) {
        proprietes.add("lanterne.transporteur.url", () -> "http://127.0.0.1:" + TRANSPORTEUR.getAddress().getPort());
    }

    @AfterAll
    static void arreter() {
        TRANSPORTEUR.stop(0);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    CircuitBreakerRegistry disjoncteurs;

    CircuitBreaker disjoncteur() {
        return disjoncteurs.circuitBreaker("transporteur");
    }

    @BeforeEach
    void remettreAZero() {
        MODE.set(Mode.OK);
        disjoncteur().reset();
        APPELS.set(0);
    }

    private String delai() throws Exception {
        return mvc.perform(get("/api/livraison/delai").param("codePostal", "75001"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private long chronometrer(String attendu) throws Exception {
        long debut = System.nanoTime();
        assertThat(delai()).isEqualTo(attendu);
        return Duration.ofNanos(System.nanoTime() - debut).toMillis();
    }

    @Test
    void le_transporteur_repond_le_vrai_delai_est_annonce() throws Exception {
        assertThat(delai()).isEqualTo("livraison sous 2 jours");
    }

    @Test
    void transporteur_en_panne_repli_et_avertissement_dans_les_logs(CapturedOutput logs) throws Exception {
        MODE.set(Mode.PANNE);
        assertThat(chronometrer("livraison sous 3 à 5 jours")).isLessThan(3000);
        assertThat(logs.getAll()).as("le repli doit laisser un log d'avertissement (WARN)").contains("WARN");
    }

    @Test
    void transporteur_lent_repli_en_moins_de_3_secondes() throws Exception {
        MODE.set(Mode.LENT);
        assertThat(chronometrer("livraison sous 3 à 5 jours"))
                .as("un timeout de lecture de 2 secondes doit couper l'appel lent").isLessThan(3000);
    }

    @Test
    void apres_10_echecs_le_transporteur_n_est_plus_appele() throws Exception {
        MODE.set(Mode.PANNE);
        for (int i = 0; i < 15; i++) {
            assertThat(delai()).isEqualTo("livraison sous 3 à 5 jours");
        }
        assertThat(disjoncteur().getState()).as("le circuit doit être ouvert").isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(APPELS.get()).as("circuit ouvert : le transporteur n'est plus appelé").isLessThanOrEqualTo(10);
    }

    @Test
    void la_configuration_du_disjoncteur_est_celle_demandee() {
        CircuitBreakerConfig config = disjoncteur().getCircuitBreakerConfig();
        assertThat(config.getSlidingWindowSize()).as("fenêtre de 20 appels").isEqualTo(20);
        assertThat(config.getMinimumNumberOfCalls()).as("10 appels au minimum").isEqualTo(10);
        assertThat(config.getFailureRateThreshold()).as("50 % d'échecs").isEqualTo(50f);
        assertThat(config.getSlowCallDurationThreshold()).as("appel lent au-delà de 2 s").isEqualTo(Duration.ofSeconds(2));
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).as("30 s ouvert").isEqualTo(30_000L);
    }

    @Test
    void le_transporteur_revenu_le_circuit_se_referme() throws Exception {
        disjoncteur().transitionToOpenState();
        disjoncteur().transitionToHalfOpenState();
        for (int i = 0; i < 20 && disjoncteur().getState() != CircuitBreaker.State.CLOSED; i++) {
            delai();
        }
        assertThat(disjoncteur().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(delai()).isEqualTo("livraison sous 2 jours");
    }

    @Test
    void l_etat_du_circuit_est_expose_par_actuator_health() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components.circuitBreakers.details.transporteur").exists());
    }
}
