package com.mindup.caiu_sos;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

// Testa o DetectorDeQueda com leituras inventadas, sem celular.
// Cada leitura usa só o eixo z (a magnitude é a mesma) e o tempo avança 20 ms por leitura,
// igual ao SENSOR_DELAY_GAME do MonitorService.
public class DetectorDeQuedaTest {

    private static final long PASSO_MS = 20;
    private static final float PARADO = 9.8f;
    private static final float CAINDO = 0.5f;
    private static final float GIRO_FORTE = 5f;

    private DetectorDeQueda detector;
    // Relógio de mentira: começa em 1 s e vai avançando conforme as leituras
    private long tempo;
    // Quantas vezes o detector avisou queda, e com que pico
    private int quedas;
    private float ultimoPico;

    // Roda antes de cada teste: detector novo, com giroscópio, sensibilidade Média, no bolso
    @Before
    public void preparar() {
        tempo = 1000;
        quedas = 0;
        ultimoPico = 0;
        detector = criarDetector(true);
    }

    // Cria um detector que conta as quedas avisadas
    private DetectorDeQueda criarDetector(boolean temGiroscopio) {
        return new DetectorDeQueda(pico -> {
            quedas++;
            ultimoPico = pico;
        }, temGiroscopio);
    }

    // Manda a mesma aceleração durante um tempo
    private void acelerar(float magnitude, long duracaoMs) {
        for (long passou = 0; passou < duracaoMs; passou += PASSO_MS) {
            detector.novaAceleracao(0, 0, magnitude, tempo);
            tempo += PASSO_MS;
        }
    }

    // Manda uma leitura do giroscópio no momento atual
    private void girar(float radPorSegundo) {
        detector.novaRotacao(radPorSegundo, 0, 0, tempo);
    }

    // Uma queda completa: em pé, queda livre, impacto girando o corpo, caído e parado
    private void cair(float impacto, float rotacao) {
        acelerar(PARADO, 500);
        acelerar(CAINDO, 400);
        girar(rotacao);
        acelerar(impacto, 40);
        acelerar(PARADO, 2500);
    }

    private void cair(float impacto) {
        cair(impacto, GIRO_FORTE);
    }

    // ---- Os casos pedidos ----

    @Test
    public void queda_disparaComOPico() {
        cair(35f);
        assertEquals(1, quedas);
        assertEquals(35f, ultimoPico, 0.01f);
        assertEquals(DetectorDeQueda.Estado.NORMAL, detector.getEstado());
    }

    @Test
    public void pulo_aterrissaDePeSemGirar_naoDispara() {
        // Impulso para subir, tempo no ar, aterrissagem forte sem rotação, fica parado em pé
        acelerar(PARADO, 500);
        acelerar(20f, 100);
        acelerar(CAINDO, 300);
        girar(0.5f);
        acelerar(30f, 60);
        acelerar(PARADO, 2500);
        assertEquals(0, quedas);
    }

    @Test
    public void pulo_continuaAndandoDepois_naoDispara() {
        // Mesmo girando um pouco, quem continua andando não ficou caído
        acelerar(PARADO, 500);
        acelerar(CAINDO, 300);
        girar(GIRO_FORTE);
        acelerar(30f, 60);
        for (int passo = 0; passo < 8; passo++) {
            acelerar(14f, 200);
            acelerar(6f, 200);
        }
        assertEquals(0, quedas);
        assertEquals(DetectorDeQueda.Estado.NORMAL, detector.getEstado());
    }

    @Test
    public void pancadaSemQuedaLivre_naoDispara() {
        // Bater o celular na mesa: pico forte e parado depois, mas sem cair antes
        acelerar(PARADO, 500);
        girar(GIRO_FORTE);
        acelerar(40f, 40);
        assertEquals(DetectorDeQueda.Estado.NORMAL, detector.getEstado());
        acelerar(PARADO, 2500);
        assertEquals(0, quedas);
    }

    @Test
    public void quedaLivreSemImpacto_expira() {
        acelerar(PARADO, 500);
        acelerar(CAINDO, 300);
        assertEquals(DetectorDeQueda.Estado.QUEDA_LIVRE, detector.getEstado());

        // Pegou o celular no ar: mais de 500 ms sem impacto volta ao normal
        acelerar(PARADO, 600);
        assertEquals(DetectorDeQueda.Estado.NORMAL, detector.getEstado());

        // Um impacto atrasado não conta mais
        girar(GIRO_FORTE);
        acelerar(35f, 40);
        acelerar(PARADO, 2500);
        assertEquals(0, quedas);
    }

    @Test
    public void sensibilidade_mudaOLimiar() {
        detector.setSensibilidade(Sensibilidade.BAIXA);
        assertEquals(30f, detector.getLimiarImpacto(), 0.01f);
        detector.setSensibilidade(Sensibilidade.MEDIA);
        assertEquals(25f, detector.getLimiarImpacto(), 0.01f);
        detector.setSensibilidade(Sensibilidade.ALTA);
        assertEquals(20f, detector.getLimiarImpacto(), 0.01f);

        // Impacto de 22 m/s²: fraco demais para a Média, suficiente para a Alta
        detector.setSensibilidade(Sensibilidade.MEDIA);
        cair(22f);
        assertEquals(0, quedas);
        detector.setSensibilidade(Sensibilidade.ALTA);
        cair(22f);
        assertEquals(1, quedas);
    }

    @Test
    public void cooldown_ignoraNovaQuedaPor10Segundos() {
        cair(35f);
        assertEquals(1, quedas);

        // Outra queda logo em seguida (menos de 10 s): ignorada
        cair(35f);
        assertEquals(1, quedas);

        // Depois de 10 s, volta a detectar
        acelerar(PARADO, DetectorDeQueda.COOLDOWN_MS);
        cair(35f);
        assertEquals(2, quedas);
    }

    // ---- Casos extras ----

    @Test
    public void foraDoBolso_impactoPrecisaSer5Maior() {
        detector.setForaDoBolso(true);
        assertEquals(30f, detector.getLimiarImpacto(), 0.01f);
        cair(28f);
        assertEquals(0, quedas);

        detector.setForaDoBolso(false);
        cair(28f);
        assertEquals(1, quedas);
    }

    @Test
    public void semRotacaoPertoDoImpacto_naoDispara() {
        // Girou, mas 1 s antes do impacto: fora da janela de ±300 ms
        acelerar(PARADO, 500);
        girar(GIRO_FORTE);
        acelerar(CAINDO, 1000);
        acelerar(35f, 40);
        acelerar(PARADO, 2500);
        assertEquals(0, quedas);
    }

    @Test
    public void rotacaoLogoDepoisDoImpacto_tambemConfirma() {
        acelerar(PARADO, 500);
        acelerar(CAINDO, 400);
        acelerar(35f, 40);
        girar(GIRO_FORTE);
        acelerar(PARADO, 2500);
        assertEquals(1, quedas);
    }

    @Test
    public void semGiroscopio_confirmacaoEhIgnorada() {
        detector = criarDetector(false);
        cair(35f, 0f);
        assertEquals(1, quedas);
    }

    @Test
    public void mexeuAntesDe2Segundos_naoDispara() {
        // Caiu, ficou parado só 1,6 s e se levantou
        acelerar(PARADO, 500);
        acelerar(CAINDO, 400);
        girar(GIRO_FORTE);
        acelerar(35f, 40);
        acelerar(PARADO, 1600);
        acelerar(15f, 200);
        acelerar(PARADO, 2500);
        assertEquals(0, quedas);
    }

    @Test
    public void celularColocadoNaMesa_naoDispara() {
        // O alarme falso visto no celular: modo demonstração, "queda" de 3 cm (só 3 leituras
        // perto de zero), pancada forte na mesa dura, com giro da mão, e o celular parado depois
        detector.setModoDemonstracao(true);
        acelerar(PARADO, 500);
        acelerar(CAINDO, 60);
        girar(GIRO_FORTE);
        acelerar(37.7f, 20);
        assertEquals(DetectorDeQueda.Estado.NORMAL, detector.getEstado());
        acelerar(PARADO, 2500);
        assertEquals(0, quedas);
    }

    @Test
    public void quedaDe20cm_aindaDispara() {
        // 200 ms de queda livre (≈ 20 cm) já passa do mínimo de 150 ms
        acelerar(PARADO, 500);
        acelerar(CAINDO, 200);
        girar(GIRO_FORTE);
        acelerar(35f, 40);
        acelerar(PARADO, 2500);
        assertEquals(1, quedas);
    }

    @Test
    public void modoDemonstracao_divideOsLimiaresPor2() {
        detector.setModoDemonstracao(true);
        assertEquals(12.5f, detector.getLimiarImpacto(), 0.01f);
        assertEquals(1.5f, detector.getLimiarRotacao(), 0.01f);

        // Celular jogado no sofá: impacto e rotação fracos, que só contam no modo demonstração
        cair(15f, 2f);
        assertEquals(1, quedas);
    }
}
