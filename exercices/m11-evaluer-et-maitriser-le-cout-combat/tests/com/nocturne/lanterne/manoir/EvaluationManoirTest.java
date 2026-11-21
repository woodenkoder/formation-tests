package com.nocturne.lanterne.manoir;

import static org.assertj.core.api.Assertions.assertThat;

import com.nocturne.lanterne.evaluation.Evaluation;
import com.nocturne.lanterne.evaluation.Evaluation.Cas;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Combat 11.3 du manoir : un jeu d'évaluation sérieux, et un score qui ne ment pas. */
class EvaluationManoirTest {

    static List<Cas> jeu() {
        return Evaluation.charger(Path.of("evaluation/questions.yaml"));
    }

    static List<Cas> synthetiques(int n) {
        return IntStream.range(0, n)
                .mapToObj(i -> new Cas("question " + i, List.of("réponse " + i), List.of("interdit"), false))
                .toList();
    }

    /** Un assistant qui répond juste aux n premières questions synthétiques. */
    static String repondJusteAux(int n, String question) {
        int i = Integer.parseInt(question.substring("question ".length()));
        return i < n ? "voici la réponse " + i : "aucune idée";
    }

    @Test
    void le_jeu_compte_au_moins_20_questions() {
        assertThat(jeu()).as("evaluation/questions.yaml : au moins 20 questions").hasSizeGreaterThanOrEqualTo(20);
    }

    @Test
    void le_jeu_compte_au_moins_5_questions_pieges() {
        assertThat(jeu()).as("au moins 5 questions avec piege: true (hors sujet, injection, données d'un autre client)")
                .filteredOn(Cas::piege)
                .hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void chaque_question_dit_ce_qu_on_attend() {
        for (Cas c : jeu()) {
            assertThat(c.question()).as("une question vide").isNotBlank();
            assertThat(c.doitContenir().size() + c.neDoitPasContenir().size())
                    .as("« %s » : rien à vérifier (doitContenir ou neDoitPasContenir)", c.question())
                    .isPositive();
        }
    }

    @Test
    void un_assistant_parfait_obtient_100() {
        List<Cas> jeu = jeu();
        var r = Evaluation.evaluer(jeu, q -> jeu.stream().filter(c -> c.question().equals(q)).findFirst()
                .map(c -> String.join(" ", c.doitContenir()).toUpperCase(Locale.ROOT))
                .orElse(""));
        assertThat(r.reussies()).as("les comparaisons ignorent les majuscules ; ratées : %s", r.ratees())
                .isEqualTo(jeu.size());
        assertThat(r.total()).isEqualTo(jeu.size());
        assertThat(r.suffisant()).isTrue();
    }

    @Test
    void un_mot_interdit_fait_rater_la_question() {
        List<Cas> cas = synthetiques(3);
        var r = Evaluation.evaluer(cas, q -> q.endsWith("1") ? "réponse 1, et un mot INTERDIT" : "voici la ré" + "ponse " + q.substring(9));
        assertThat(r.reussies()).isEqualTo(2);
        assertThat(r.ratees()).containsExactly("question 1");
    }

    @Test
    void le_seuil_de_90_pourcent_est_exact() {
        List<Cas> vingt = synthetiques(20);
        var dixHuit = Evaluation.evaluer(vingt, q -> repondJusteAux(18, q));
        assertThat(dixHuit.reussies()).isEqualTo(18);
        assertThat(dixHuit.suffisant()).as("18/20 = 90 % : suffisant").isTrue();
        var dixSept = Evaluation.evaluer(vingt, q -> repondJusteAux(17, q));
        assertThat(dixSept.suffisant()).as("17/20 = 85 % : l'évaluation doit échouer").isFalse();
        assertThat(dixSept.ratees()).hasSize(3);
        List<String> attendues = new ArrayList<>(List.of("question 17", "question 18", "question 19"));
        assertThat(dixSept.ratees()).containsExactlyElementsOf(attendues);
    }
}
