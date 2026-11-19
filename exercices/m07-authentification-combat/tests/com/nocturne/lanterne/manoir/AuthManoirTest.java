package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nocturne.lanterne.compte.CompteRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Combat 07.2 du manoir : bcrypt, migration des anciennes empreintes, JWT. */
@SpringBootTest(properties = "lanterne.jwt.secret=" + AuthManoirTest.SECRET)
@AutoConfigureMockMvc
class AuthManoirTest {

    static final String SECRET = "cle-du-manoir-pour-les-tests-0123456789";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectProvider<PasswordEncoder> encodeurs;

    @Autowired
    CompteRepository comptes;

    @Autowired
    ObjectMapper json;

    private String connecter(String email, String motDePasse) throws Exception {
        String corps = mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"motDePasse\": \"" + motDePasse + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corps).get("jeton").asText();
    }

    private static String b64(byte[] octets) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    private static String signer(String entetePointContenu) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return b64(mac.doFinal(entetePointContenu.getBytes(StandardCharsets.US_ASCII)));
    }

    private JsonNode partie(String jeton, int i) throws Exception {
        return json.readTree(Base64.getUrlDecoder().decode(jeton.split("\\.")[i]));
    }

    @Test
    void les_nouvelles_empreintes_sont_en_bcrypt() {
        PasswordEncoder encodeur = encodeurs.getIfAvailable();
        assertThat(encodeur).as("Déclare un bean PasswordEncoder (DelegatingPasswordEncoder, bcrypt par défaut)").isNotNull();
        String empreinte = encodeur.encode("un-mot-de-passe");
        assertThat(empreinte).as("PasswordEncoder doit produire des empreintes {bcrypt}…").startsWith("{bcrypt}");
        assertThat(encodeur.matches("un-mot-de-passe", empreinte)).isTrue();
    }

    @Test
    void un_compte_en_sha256_se_connecte_et_passe_en_bcrypt() throws Exception {
        connecter("support@nocturne.test", "Grimoire#42");
        assertThat(comptes.trouver("support@nocturne.test").orElseThrow().empreinte())
                .as("après une connexion réussie, l'ancienne empreinte SHA-256 doit être remplacée par du bcrypt")
                .startsWith("{bcrypt}");
        connecter("support@nocturne.test", "Grimoire#42");
    }

    @Test
    void un_mauvais_mot_de_passe_est_refuse() throws Exception {
        mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin@nocturne.test\", \"motDePasse\": \"faux\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void le_jwt_est_signe_hs256_avec_lanterne_jwt_secret_et_expire_en_15_minutes() throws Exception {
        String jeton = connecter("admin@nocturne.test", "Lanterne!2026");
        String[] parties = jeton.split("\\.");
        assertThat(parties).as("un JWT a trois parties : en-tête.contenu.signature").hasSize(3);
        assertThat(partie(jeton, 0).path("alg").asText()).isEqualTo("HS256");
        assertThat(parties[2])
                .as("la signature doit être un HMAC-SHA256 avec la clé lanterne.jwt.secret (ses octets UTF-8)")
                .isEqualTo(signer(parties[0] + "." + parties[1]));
        JsonNode contenu = partie(jeton, 1);
        long maintenant = Instant.now().getEpochSecond();
        assertThat(contenu.path("exp").asLong()).as("le jeton doit avoir une expiration (exp)").isGreaterThan(maintenant);
        assertThat(contenu.path("exp").asLong() - maintenant).as("15 minutes au plus").isLessThanOrEqualTo(15 * 60 + 5);
    }

    @Test
    void le_jwt_ne_contient_aucune_donnee_personnelle() throws Exception {
        String contenu = partie(connecter("admin@nocturne.test", "Lanterne!2026"), 1).toString();
        assertThat(contenu).as("ni e-mail ni nom dans le jeton : il est lisible par tous")
                .doesNotContain("@", "nocturne.test", "Ada");
        assertThat(contenu).as("le rôle est dans le jeton").contains("ADMIN");
    }

    @Test
    void un_jwt_valide_ouvre_l_espace_admin() throws Exception {
        String jeton = connecter("admin@nocturne.test", "Lanterne!2026");
        mvc.perform(get("/api/admin/moi").header("Authorization", "Bearer " + jeton))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void un_jwt_modifie_expire_ou_sans_signature_est_refuse() throws Exception {
        String jeton = connecter("admin@nocturne.test", "Lanterne!2026");
        String[] parties = jeton.split("\\.");

        ObjectNode modifie = (ObjectNode) partie(jeton, 1);
        modifie.put("role", "SUPER_ADMIN");
        String falsifie = parties[0] + "." + b64(json.writeValueAsBytes(modifie)) + "." + parties[2];
        mvc.perform(get("/api/admin/moi").header("Authorization", "Bearer " + falsifie))
                .andExpect(status().isUnauthorized());

        ObjectNode perime = (ObjectNode) partie(jeton, 1);
        perime.put("iat", Instant.now().getEpochSecond() - 3600);
        perime.put("exp", Instant.now().getEpochSecond() - 60);
        String debut = parties[0] + "." + b64(json.writeValueAsBytes(perime));
        mvc.perform(get("/api/admin/moi").header("Authorization", "Bearer " + debut + "." + signer(debut)))
                .andExpect(status().isUnauthorized());

        String sansSignature = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + parties[1] + ".";
        mvc.perform(get("/api/admin/moi").header("Authorization", "Bearer " + sansSignature))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/moi")).andExpect(status().isUnauthorized());
    }
}
