package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Combat 07.3 du manoir : qui a le droit de quoi. */
@SpringBootTest
@AutoConfigureMockMvc
class AutorisationManoirTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping routes;

    @Test
    void un_anonyme_recoit_401() throws Exception {
        mvc.perform(get("/api/commandes/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/commandes/1").param("client", "ada")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/commandes")).andExpect(status().isUnauthorized());
    }

    @Test
    void le_public_reste_ouvert() throws Exception {
        mvc.perform(get("/api/public/produits")).andExpect(status().isOk());
    }

    @Test
    void un_client_recoit_403_sur_l_admin() throws Exception {
        mvc.perform(get("/api/admin/commandes").with(user("ada").roles("CLIENT"))).andExpect(status().isForbidden());
    }

    @Test
    void un_administrateur_ouvre_l_admin() throws Exception {
        mvc.perform(get("/api/admin/commandes").with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void un_client_lit_sa_commande() throws Exception {
        mvc.perform(get("/api/commandes/1").with(user("ada").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void la_commande_d_un_autre_client_renvoie_404_meme_en_trichant_sur_le_parametre() throws Exception {
        mvc.perform(get("/api/commandes/2").with(user("ada").roles("CLIENT"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/commandes/2").param("client", "linus").with(user("ada").roles("CLIENT")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/commandes/2").with(user("linus").roles("CLIENT"))).andExpect(status().isOk());
    }

    @Test
    void aucun_endpoint_ne_lit_l_identite_du_client_dans_la_requete() {
        List<String> fautifs = routes.getHandlerMethods().values().stream()
                .flatMap(m -> List.of(m.getMethodParameters()).stream())
                .filter(p -> p.hasParameterAnnotation(RequestParam.class) || p.hasParameterAnnotation(RequestHeader.class))
                .filter(p -> nom(p).contains("client") || nom(p).contains("user") || nom(p).contains("compte"))
                .map(p -> p.getMethod().getDeclaringClass().getSimpleName() + "." + p.getMethod().getName() + "(" + nom(p) + ")")
                .toList();
        assertThat(fautifs)
                .as("L'identité du client vient du jeton (Authentication.getName()), jamais d'un paramètre qu'il choisit")
                .isEmpty();
    }

    private static String nom(MethodParameter p) {
        p.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
        RequestParam param = p.getParameterAnnotation(RequestParam.class);
        String nom = param != null && !param.name().isEmpty() ? param.name() : p.getParameterName();
        return String.valueOf(nom).toLowerCase(Locale.ROOT);
    }
}
