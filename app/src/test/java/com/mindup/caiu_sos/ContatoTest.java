package com.mindup.caiu_sos;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ContatoTest {

    private static String iniciais(String nome) {
        return new Contato(nome, "41998765432").getIniciais();
    }

    @Test
    public void iniciais_primeiroEUltimoNome() {
        assertEquals("MO", iniciais("Maria Aparecida dos Santos Oliveira"));
        assertEquals("CS", iniciais("Carlos Silva"));
    }

    @Test
    public void iniciais_umNomeSo() {
        assertEquals("C", iniciais("Carlos"));
    }

    @Test
    public void iniciais_ignoraEspacosEMinusculas() {
        assertEquals("AP", iniciais("  ana   paula  "));
    }

    @Test
    public void iniciais_mantemAcento() {
        assertEquals("ÉÁ", iniciais("élio ávila"));
    }

    @Test
    public void iniciais_nomeVazio() {
        assertEquals("", iniciais(""));
        assertEquals("", iniciais("   "));
    }

    @Test
    public void primeiroNome() {
        assertEquals("Maria", new Contato("  Maria Aparecida dos Santos", "41998765432").getPrimeiroNome());
        assertEquals("Carlos", new Contato("Carlos", "41998765432").getPrimeiroNome());
    }
}
