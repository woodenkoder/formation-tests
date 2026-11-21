package com.nocturne.lanterne.manoir;

import java.util.List;
import java.util.Locale;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** Le faux modèle d'embedding du manoir : un vecteur par sujet, sans réseau. */
public class EmbeddingDuManoir implements EmbeddingModel {

    static final String[][] SUJETS = {
        {"livr", "expédi", "colis", "port"},
        {"retour", "renvo", "rembours"},
        {"paie", "paye", "carte", "paypal", "virement"},
        {"garanti", "allume", "panne", "cassé"},
        {"modifi", "annul"},
        {"geste", "remise", "code", "litige"},
    };

    static float[] vecteur(String texte) {
        String t = texte.toLowerCase(Locale.FRENCH);
        float[] v = new float[SUJETS.length + 1];
        for (int i = 0; i < SUJETS.length; i++) {
            for (String mot : SUJETS[i]) {
                for (int k = t.indexOf(mot); k >= 0; k = t.indexOf(mot, k + 1)) {
                    v[i]++;
                }
            }
        }
        v[SUJETS.length] = 0.3f;
        return v;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> textes = request.getInstructions();
        return new EmbeddingResponse(
                java.util.stream.IntStream.range(0, textes.size())
                        .mapToObj(i -> new Embedding(vecteur(textes.get(i)), i))
                        .toList());
    }

    @Override
    public float[] embed(Document document) {
        return vecteur(document.getText());
    }
}
