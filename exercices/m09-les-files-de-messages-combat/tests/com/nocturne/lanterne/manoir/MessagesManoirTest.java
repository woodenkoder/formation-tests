package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nocturne.lanterne.fidelite.FideliteService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Combat 09.6 du manoir : l'événement « commande créée », contre un vrai RabbitMQ. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MessagesManoirTest {

    static final String ECHANGE = "lanterne.commandes";
    static final String CLE = "commande.creee";
    static final String FILE_ECHECS = "fidelite.commande-creee.dlq";

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4-management");

    @Autowired
    MockMvc mvc;

    @Autowired
    FideliteService fidelite;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    ObjectMapper json;

    @Autowired
    Environment env;

    private static String client() {
        return "client-" + UUID.randomUUID() + "@exemple.test";
    }

    private long commander(String client, String montant) throws Exception {
        String corps = mvc.perform(post("/api/commandes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client\": \"" + client + "\", \"montant\": " + montant + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corps).get("id").asLong();
    }

    private void valider(long id, ResultMatcher attendu) throws Exception {
        mvc.perform(post("/api/commandes/" + id + "/validation")).andExpect(attendu);
    }

    /** Attend que le client ait ce nombre de points (au plus 15 s). */
    private int attendrePoints(String client, int attendus) throws InterruptedException {
        long fin = System.currentTimeMillis() + 15_000;
        while (fidelite.points(client) != attendus && System.currentTimeMillis() < fin) {
            Thread.sleep(200);
        }
        return fidelite.points(client);
    }

    private void envoyer(String corps) {
        MessageProperties proprietes = new MessageProperties();
        proprietes.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message message = MessageBuilder.withBody(corps.getBytes(StandardCharsets.UTF_8)).andProperties(proprietes).build();
        rabbit.send(ECHANGE, CLE, message);
    }

    @Test
    void une_commande_validee_credite_les_points_une_fois() throws Exception {
        String client = client();
        long id = commander(client, "42.90");
        valider(id, status().isOk());
        assertThat(attendrePoints(client, 42)).as("1 point par euro entier : 42.90 € → 42 points").isEqualTo(42);
        Thread.sleep(1500);
        assertThat(fidelite.points(client)).as("crédité une seule fois").isEqualTo(42);
    }

    @Test
    void le_meme_evenement_recu_deux_fois_ne_credite_qu_une_fois() throws Exception {
        String client = client();
        String evenement = "{\"evenementId\": \"" + UUID.randomUUID() + "\", \"commandeId\": 999, \"client\": \"" + client
                + "\", \"montant\": 100.00}";
        envoyer(evenement);
        envoyer(evenement);
        assertThat(attendrePoints(client, 100)).isEqualTo(100);
        Thread.sleep(2000);
        assertThat(fidelite.points(client)).as("le consommateur doit être idempotent (même evenementId)").isEqualTo(100);
    }

    @Test
    void un_message_qui_echoue_toujours_finit_dans_la_file_des_messages_en_echec() {
        envoyer("ceci n'est pas un événement");
        Message echec;
        try {
            echec = rabbit.receive(FILE_ECHECS, 20_000);
        } catch (org.springframework.amqp.AmqpException e) {
            throw new AssertionError("Déclare la file des messages en échec " + FILE_ECHECS
                    + " et relie-la à la file de la fidélité (x-dead-letter-…)", e);
        }
        assertThat(echec).as("le message en échec doit arriver dans " + FILE_ECHECS).isNotNull();
    }

    @Test
    void trois_essais_avant_la_file_des_messages_en_echec() {
        assertThat(env.getProperty("spring.rabbitmq.listener.simple.retry.enabled")).isEqualTo("true");
        assertThat(env.getProperty("spring.rabbitmq.listener.simple.retry.max-attempts")).isEqualTo("3");
    }

    @Test
    void rien_n_est_publie_si_la_transaction_est_annulee() throws Exception {
        String client = client();
        long id = commander(client, "6000.00");
        valider(id, status().isUnprocessableEntity());
        Thread.sleep(3000);
        assertThat(fidelite.points(client))
                .as("la validation a été annulée : l'événement ne doit pas partir (publie après le commit)")
                .isZero();
    }
}
