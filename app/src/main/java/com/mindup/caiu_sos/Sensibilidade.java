package com.mindup.caiu_sos;

// As três sensibilidades da detecção de queda.
// Quanto mais alta, menor o impacto que já conta como queda (e mais alarmes falsos).
public enum Sensibilidade {
    BAIXA(30f),
    MEDIA(25f),
    ALTA(20f);

    // Pico de aceleração, em m/s², que conta como impacto (usado pelo DetectorDeQueda)
    public final float limiarImpacto;

    // Cada valor do enum guarda o seu limiar
    Sensibilidade(float limiarImpacto) {
        this.limiarImpacto = limiarImpacto;
    }
}
