package com.mindup.caiu_sos;

import java.util.Locale;

// Decide se houve uma queda olhando só números: a aceleração e a rotação do celular.
// Não usa nada do Android (nenhum import android.*), por isso dá para testar no DetectorDeQuedaTest.
//
// Uma queda passa por 4 fases, nesta ordem:
//   NORMAL      → celular parado, no bolso ou na mão
//   QUEDA_LIVRE → a aceleração cai perto de zero (o celular "flutua" enquanto cai) por 150 ms ou mais
//   IMPACTO     → logo depois vem um pico forte: bateu no chão
//   IMOVEL      → a pessoa fica parada no chão por 2 segundos inteiros
// Além disso, o giroscópio tem que ter visto uma rotação brusca perto do impacto
// (quem cai gira o corpo; quem pula e aterrissa de pé, não).
//
// O tempo de cada leitura sempre vem por parâmetro (em milissegundos), nunca do relógio do
// sistema: assim o teste consegue "fingir" o tempo passando.
public class DetectorDeQueda {

    // Quem quer ser avisado da queda (o MonitorService) implementa esta interface
    public interface Listener {
        void onQuedaDetectada(float pico);
    }

    // As fases da queda
    public enum Estado {
        NORMAL,
        QUEDA_LIVRE,
        IMPACTO,
        IMOVEL
    }

    // Celular parado mede só a gravidade da Terra: cerca de 9,8 m/s²
    static final float GRAVIDADE = 9.8f;
    // Abaixo disto o celular está em queda livre
    static final float LIMIAR_QUEDA_LIVRE = 3f;
    // A queda livre precisa durar pelo menos 150 ms (≈ cair 11 cm). Colocar o celular com força
    // na mesa também dá uma leitura perto de zero, mas por poucos milissegundos.
    static final long TEMPO_MINIMO_QUEDA_LIVRE_MS = 150;
    // O impacto tem que vir em até 500 ms depois da queda livre
    static final long JANELA_IMPACTO_MS = 500;
    // Na mão o celular balança mais que no bolso: fora do bolso o impacto precisa ser 5 m/s² mais forte
    static final float EXTRA_FORA_DO_BOLSO = 5f;
    // "Parado" = entre 7,8 e 11,8 m/s² (a gravidade com uma folga de 2 para cada lado)
    static final float FOLGA_IMOVEL = 2f;
    // Tempo parado que confirma a queda
    static final long TEMPO_IMOVEL_MS = 2000;
    // Tempo que o celular tem para parar de quicar depois do impacto
    static final long TEMPO_PARA_PARAR_MS = 1500;
    // Rotação brusca no giroscópio, em rad/s (3 rad/s é quase meia volta por segundo)
    static final float LIMIAR_ROTACAO = 3f;
    // A rotação vale se acontecer até 300 ms antes ou depois do impacto
    static final long JANELA_ROTACAO_MS = 300;
    // Depois de uma queda, ignora tudo por 10 s (não dispara duas vezes pela mesma queda)
    static final long COOLDOWN_MS = 10000;

    private final Listener listener;
    private final boolean temGiroscopio;

    // Configurações que o MonitorService pode mudar
    private Sensibilidade sensibilidade = Sensibilidade.MEDIA;
    private boolean foraDoBolso = false;
    // 1 no uso normal; 2 no modo demonstração (limiares pela metade)
    private float divisorLimiares = 1f;

    // Fase atual e o porquê da última mudança (o MonitorService mostra no Logcat)
    private Estado estado = Estado.NORMAL;
    private String motivo = "";

    // Memória de cada fase
    private long inicioQuedaLivreMs;
    private long ultimaQuedaLivreMs;
    private float maiorLeituraSemImpacto;
    private long impactoMs;
    private float pico;
    private long inicioImovelMs;
    private boolean rotacaoConfirmada;
    // Hora da última rotação brusca (-1 = nenhuma ainda)
    private long ultimaRotacaoForteMs = -1;
    // Até quando as leituras são ignoradas depois de uma queda
    private long fimDoCooldownMs = 0;

    // Cria o detector. Sem giroscópio no aparelho, a confirmação pela rotação é pulada.
    public DetectorDeQueda(Listener listener, boolean temGiroscopio) {
        this.listener = listener;
        this.temGiroscopio = temGiroscopio;
    }

    // ---- Configurações ----

    // Troca a sensibilidade (muda o limiar de impacto)
    public void setSensibilidade(Sensibilidade sensibilidade) {
        this.sensibilidade = sensibilidade;
    }

    // Avisa se o celular está fora do bolso (o sensor de proximidade não vê nada encostado)
    public void setForaDoBolso(boolean foraDoBolso) {
        this.foraDoBolso = foraDoBolso;
    }

    // Modo demonstração: divide os limiares de impacto e de rotação por 2 (para apresentar em sala)
    public void setModoDemonstracao(boolean ligado) {
        divisorLimiares = ligado ? 2f : 1f;
    }

    // Pico de aceleração que conta como impacto, em m/s²
    public float getLimiarImpacto() {
        float limiar = sensibilidade.limiarImpacto;
        if (foraDoBolso) {
            limiar += EXTRA_FORA_DO_BOLSO;
        }
        return limiar / divisorLimiares;
    }

    // Rotação que conta como "girou o corpo", em rad/s
    public float getLimiarRotacao() {
        return LIMIAR_ROTACAO / divisorLimiares;
    }

    // Fase atual da queda
    public Estado getEstado() {
        return estado;
    }

    // Explicação da última mudança de fase (ex.: "queda livre sem impacto")
    public String getMotivo() {
        return motivo;
    }

    // ---- Leituras dos sensores ----

    // Chamado a cada leitura do acelerômetro (x, y, z em m/s²; tempo em ms)
    public void novaAceleracao(float x, float y, float z, long tempoMs) {
        // Logo depois de uma queda, não procura outra
        if (tempoMs < fimDoCooldownMs) {
            return;
        }

        // Magnitude: o "tamanho" da aceleração, não importa para que lado o celular está virado
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);

        switch (estado) {
            case NORMAL:
                // Aceleração perto de zero: começou a cair
                if (magnitude < LIMIAR_QUEDA_LIVRE) {
                    inicioQuedaLivreMs = tempoMs;
                    ultimaQuedaLivreMs = tempoMs;
                    maiorLeituraSemImpacto = 0;
                    mudarPara(Estado.QUEDA_LIVRE, formatar("aceleração em %.1f m/s²", magnitude));
                }
                break;

            case QUEDA_LIVRE:
                if (magnitude < LIMIAR_QUEDA_LIVRE) {
                    // Ainda caindo: o prazo do impacto conta a partir da última leitura de queda livre
                    ultimaQuedaLivreMs = tempoMs;
                } else if (tempoMs - ultimaQuedaLivreMs > JANELA_IMPACTO_MS) {
                    // Passou meio segundo sem impacto: o celular foi pego no ar ou só balançou
                    mudarPara(Estado.NORMAL, formatar("queda livre sem impacto (maior leitura %.1f m/s², limiar %.1f)",
                            maiorLeituraSemImpacto, getLimiarImpacto()));
                } else if (magnitude > getLimiarImpacto()
                        && ultimaQuedaLivreMs - inicioQuedaLivreMs < TEMPO_MINIMO_QUEDA_LIVRE_MS) {
                    // Bateu forte, mas caiu só alguns centímetros: celular colocado na mesa, não é queda
                    mudarPara(Estado.NORMAL, formatar("queda livre curta demais (%d ms, mínimo %d): pancada de %.1f m/s² ignorada",
                            ultimaQuedaLivreMs - inicioQuedaLivreMs, TEMPO_MINIMO_QUEDA_LIVRE_MS, magnitude));
                } else if (magnitude > getLimiarImpacto()) {
                    // Bateu forte: guarda a hora e o valor do impacto
                    impactoMs = tempoMs;
                    pico = magnitude;
                    // Vale a rotação brusca que aconteceu até 300 ms antes do impacto
                    rotacaoConfirmada = ultimaRotacaoForteMs >= 0
                            && impactoMs - ultimaRotacaoForteMs <= JANELA_ROTACAO_MS;
                    mudarPara(Estado.IMPACTO, formatar("pico de %.1f m/s² (limiar %.1f)",
                            magnitude, getLimiarImpacto()));
                } else {
                    // Guarda a maior leitura para mostrar no Logcat se não chegar no limiar
                    maiorLeituraSemImpacto = Math.max(maiorLeituraSemImpacto, magnitude);
                }
                break;

            case IMPACTO:
                // O celular ainda está batendo e quicando: guarda a leitura mais forte
                pico = Math.max(pico, magnitude);
                if (estaParado(magnitude)) {
                    inicioImovelMs = tempoMs;
                    mudarPara(Estado.IMOVEL, "parou de quicar");
                } else if (tempoMs - impactoMs > TEMPO_PARA_PARAR_MS) {
                    mudarPara(Estado.NORMAL, "continuou se mexendo depois do impacto");
                }
                break;

            case IMOVEL:
                if (!estaParado(magnitude)) {
                    if (tempoMs - impactoMs > TEMPO_PARA_PARAR_MS) {
                        // Mexeu bem depois do impacto: a pessoa se levantou ou pegou o celular
                        mudarPara(Estado.NORMAL, "se mexeu antes de completar 2 s parado");
                    } else {
                        // Logo depois do impacto: ainda é o celular quicando, espera parar de novo
                        mudarPara(Estado.IMPACTO, "ainda quicando");
                    }
                } else if (tempoMs - inicioImovelMs >= TEMPO_IMOVEL_MS) {
                    confirmarQueda(tempoMs);
                }
                break;
        }
    }

    // Chamado a cada leitura do giroscópio (x, y, z em rad/s; tempo em ms)
    public void novaRotacao(float x, float y, float z, long tempoMs) {
        float rotacao = (float) Math.sqrt(x * x + y * y + z * z);
        if (rotacao <= getLimiarRotacao()) {
            return;
        }
        ultimaRotacaoForteMs = tempoMs;
        // Rotação brusca até 300 ms depois do impacto também confirma
        boolean depoisDoImpacto = estado == Estado.IMPACTO || estado == Estado.IMOVEL;
        if (depoisDoImpacto && Math.abs(tempoMs - impactoMs) <= JANELA_ROTACAO_MS) {
            rotacaoConfirmada = true;
        }
    }

    // ---- Auxiliares ----

    // Ficou 2 s parado depois do impacto: confere o giroscópio e avisa o Listener
    private void confirmarQueda(long tempoMs) {
        // Sem giroscópio no aparelho, esta confirmação é pulada
        if (temGiroscopio && !rotacaoConfirmada) {
            mudarPara(Estado.NORMAL, "sem rotação brusca perto do impacto (parece pulo ou pancada)");
            return;
        }
        float picoDaQueda = pico;
        fimDoCooldownMs = tempoMs + COOLDOWN_MS;
        mudarPara(Estado.NORMAL, formatar("QUEDA CONFIRMADA (pico %.1f m/s²)", picoDaQueda));
        listener.onQuedaDetectada(picoDaQueda);
    }

    // Diz se a leitura é só a gravidade, com a folga de 2 m/s² para cada lado
    private boolean estaParado(float magnitude) {
        return Math.abs(magnitude - GRAVIDADE) <= FOLGA_IMOVEL;
    }

    // Troca de fase e guarda o motivo
    private void mudarPara(Estado novo, String motivo) {
        this.estado = novo;
        this.motivo = motivo;
    }

    // Monta o texto do motivo com ponto decimal (Locale.US), igual em qualquer celular
    private static String formatar(String texto, Object... valores) {
        return String.format(Locale.US, texto, valores);
    }
}
