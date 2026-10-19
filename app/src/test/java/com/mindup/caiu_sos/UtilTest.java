package com.mindup.caiu_sos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;

// Testa os métodos de telefone e de data do Util (os que não usam nada do Android)
public class UtilTest {

    // ---- Telefone ----

    @Test
    public void normalizar_tiraFormatacao() {
        assertEquals("41998765432", Util.normalizarTelefone("(41) 99876-5432"));
    }

    @Test
    public void normalizar_tiraCodigoDoPais() {
        assertEquals("41998765432", Util.normalizarTelefone("+55 (41) 99876-5432"));
    }

    @Test
    public void normalizar_tiraZeroDeLongaDistancia() {
        assertEquals("41998765432", Util.normalizarTelefone("041 99876-5432"));
    }

    @Test
    public void normalizar_cortaEm11Digitos() {
        assertEquals("41998765432", Util.normalizarTelefone("419987654321234"));
    }

    @Test
    public void normalizar_numeroIncompletoFicaComoEsta() {
        assertEquals("4199", Util.normalizarTelefone("(41) 99"));
        assertEquals("", Util.normalizarTelefone(""));
    }

    @Test
    public void celularValido() {
        assertTrue(Util.ehCelularValido("41998765432"));
        assertTrue(Util.ehCelularValido("11976543210"));
    }

    @Test
    public void celularInvalido() {
        assertFalse("fixo", Util.ehCelularValido("4133334444"));
        assertFalse("sem o 9", Util.ehCelularValido("41898765432"));
        assertFalse("DDD com zero", Util.ehCelularValido("01998765432"));
        assertFalse("incompleto", Util.ehCelularValido("4199876"));
        assertFalse("vazio", Util.ehCelularValido(""));
    }

    @Test
    public void formatar_enquantoDigita() {
        assertEquals("", Util.formatarTelefone(""));
        assertEquals("(4", Util.formatarTelefone("4"));
        assertEquals("(41", Util.formatarTelefone("41"));
        assertEquals("(41) 9", Util.formatarTelefone("419"));
        assertEquals("(41) 9987", Util.formatarTelefone("419987"));
        assertEquals("(41) 9987-6", Util.formatarTelefone("4199876"));
    }

    @Test
    public void formatar_completo() {
        assertEquals("(41) 99876-5432", Util.formatarTelefone("41998765432"));
        assertEquals("(41) 3333-4444", Util.formatarTelefone("4133334444"));
    }

    // ---- Datas ----

    private static final TimeZone FUSO = TimeZone.getTimeZone("America/Sao_Paulo");

    // Agora: quarta-feira, 29 de setembro de 2027, 10:00 (horário de Brasília)
    private static final long AGORA = em(2027, Calendar.SEPTEMBER, 29, 10, 0);

    // Monta uma data/hora no fuso de Brasília
    private static long em(int ano, int mes, int dia, int hora, int minuto) {
        Calendar calendario = Calendar.getInstance(FUSO);
        calendario.clear();
        calendario.set(ano, mes, dia, hora, minuto);
        return calendario.getTimeInMillis();
    }

    @Test
    public void diasAtras_mesmoDia() {
        assertEquals(0, Util.diasAtras(em(2027, Calendar.SEPTEMBER, 29, 0, 1), AGORA, FUSO));
    }

    @Test
    public void diasAtras_contaPeloCalendarioNaoPor24Horas() {
        // Ontem às 23:59 são só 10 horas atrás, mas já é "ontem"
        assertEquals(1, Util.diasAtras(em(2027, Calendar.SEPTEMBER, 28, 23, 59), AGORA, FUSO));
        assertEquals(1, Util.diasAtras(em(2027, Calendar.SEPTEMBER, 28, 0, 0), AGORA, FUSO));
        assertEquals(2, Util.diasAtras(em(2027, Calendar.SEPTEMBER, 27, 23, 0), AGORA, FUSO));
    }

    @Test
    public void diasAtras_viraOMes() {
        assertEquals(29, Util.diasAtras(em(2027, Calendar.AUGUST, 31, 12, 0), AGORA, FUSO));
    }

    @Test
    public void hora_24Horas() {
        assertEquals("08:30", Util.hora(em(2027, Calendar.SEPTEMBER, 18, 8, 30), FUSO));
        assertEquals("20:04", Util.hora(em(2027, Calendar.SEPTEMBER, 18, 20, 4), FUSO));
    }

    @Test
    public void diaEMes_semAnoNoAnoAtual() {
        assertEquals("18 de setembro", Util.diaEMes(em(2027, Calendar.SEPTEMBER, 18, 8, 30), AGORA, FUSO));
    }

    @Test
    public void diaEMes_comAnoEmOutroAno() {
        assertEquals("18 de outubro de 2026", Util.diaEMes(em(2026, Calendar.OCTOBER, 18, 8, 30), AGORA, FUSO));
    }

    @Test
    public void dataCompleta_comDiaDaSemanaEmMaiuscula() {
        assertEquals("Sábado, 18 de outubro de 2025", Util.dataCompleta(em(2025, Calendar.OCTOBER, 18, 8, 30), FUSO));
    }

    // ---- Mensagem de alerta ----

    @Test
    public void linkDoMapa_usaPontoDecimalESeisCasas() {
        assertEquals("https://maps.google.com/?q=-21.294744,-50.350441",
                Util.linkDoMapa(-21.2947443, -50.3504413));
    }

    @Test
    public void semAcentos_tiraAcentosECedilha() {
        assertEquals("Localizacao: Joao, Ines, voce, Acai", Util.semAcentos("Localização: João, Inês, você, Açaí"));
    }

    @Test
    public void contatoDoWhatsapp_ehOFavorito() {
        Contato ana = new Contato("Ana", "41998765432");
        Contato beto = new Contato("Beto", "41998765433");
        beto.setFavorito(true);
        assertSame(beto, Util.contatoDoWhatsapp(Arrays.asList(ana, beto)));
    }

    @Test
    public void contatoDoWhatsapp_semFavoritoEhOPrimeiro() {
        Contato ana = new Contato("Ana", "41998765432");
        Contato beto = new Contato("Beto", "41998765433");
        assertSame(ana, Util.contatoDoWhatsapp(Arrays.asList(ana, beto)));
        assertNull(Util.contatoDoWhatsapp(new ArrayList<>()));
    }

    @Test
    public void semAcentos_mensagemDeCheckinCabeEmUmSms() {
        // A mensagem mais longa: check-in sem resposta, com nome longo
        String mensagem = "Caiu SOS: Maria Aparecida dos Santos Oliveira não respondeu ao check-in de segurança. "
                + "Localização: " + Util.linkDoMapa(-21.2947443, -50.3504413);
        assertTrue(Util.semAcentos(mensagem).length() <= 160);
    }

    // ---- Título dos eventos do histórico ----

    @Test
    public void tituloDoEvento_checkins() {
        assertEquals(R.string.evento_checkin,
                Util.tituloDoEvento(new Evento(Evento.Tipo.CHECKIN, false, 0, "", 0, 0)));
        assertEquals(R.string.evento_checkin_sem_resposta,
                Util.tituloDoEvento(new Evento(Evento.Tipo.CHECKIN_SEM_RESPOSTA, false, 0, "", 0, 0)));
    }

    @Test
    public void tituloDoEvento_canceladoVemPrimeiro() {
        // Qualquer alerta que a pessoa cancelou aparece como "Alerta cancelado"
        assertEquals(R.string.evento_cancelado,
                Util.tituloDoEvento(new Evento(Evento.Tipo.CHECKIN_SEM_RESPOSTA, true, 0, "", 0, 0)));
        assertEquals(R.string.evento_cancelado,
                Util.tituloDoEvento(new Evento(Evento.Tipo.QUEDA, true, 0, "", 0, 0)));
    }

    @Test
    public void semAcentos_mensagemDeQuedaCabeEmUmSms() {
        // Sem acento, um SMS leva até 160 letras: a mensagem real (nome longo) tem que caber em 1
        String mensagem = "Caiu SOS: Maria Aparecida dos Santos Oliveira pode ter sofrido uma queda. "
                + "Localização: " + Util.linkDoMapa(-21.2947443, -50.3504413);
        assertTrue(Util.semAcentos(mensagem).length() <= 160);
    }
}
