package com.mindup.caiu_sos;

import android.app.Activity;
import android.content.Intent;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

// Métodos pequenos usados por mais de uma tela (ou por uma tela e o adapter dela).
// Os de telefone e de data não usam nada do Android e são testados no UtilTest.
public class Util {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final long UM_DIA_MS = 24L * 60 * 60 * 1000;

    // ---- Navegação inferior (Início, Contatos, Histórico) ----

    // Liga a barra de navegação de baixo. O Início fica sempre no fundo da pilha:
    // Contatos e Histórico se fecham ao trocar de aba, então o "voltar" sempre leva ao Início.
    public static void configurarNavegacao(Activity telaAtual, BottomNavigationView navegacao, int itemAtual) {
        navegacao.setSelectedItemId(itemAtual);

        // Chamado quando a pessoa toca em outra aba
        navegacao.setOnItemSelectedListener(item -> {
            Class<?> destino = MainActivity.class;
            if (item.getItemId() == R.id.nav_contatos) {
                destino = ContatosActivity.class;
            } else if (item.getItemId() == R.id.nav_historico) {
                destino = HistoricoActivity.class;
            }
            Intent intent = new Intent(telaAtual, destino);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            telaAtual.startActivity(intent);
            if (!(telaAtual instanceof MainActivity)) {
                telaAtual.finish();
            }
            // false: a aba desta tela continua marcada (a outra tela marca a dela)
            return false;
        });

        // Tocar na aba que já está marcada não faz nada (sem isto a tela se fecharia)
        navegacao.setOnItemReselectedListener(item -> {
        });
    }

    // ---- Telefone (celular brasileiro) ----

    // Deixa só os dígitos com DDD, sem o +55 e sem o 0 de longa distância:
    // "+55 (41) 99876-5432" vira "41998765432". Corta em 11 dígitos.
    public static String normalizarTelefone(String texto) {
        String digitos = texto.replaceAll("\\D", "");
        if (digitos.length() > 11 && digitos.startsWith("55")) {
            digitos = digitos.substring(2);
        }
        if (digitos.length() > 11 && digitos.startsWith("0")) {
            digitos = digitos.substring(1);
        }
        if (digitos.length() > 11) {
            digitos = digitos.substring(0, 11);
        }
        return digitos;
    }

    // Confere se é um celular com DDD: 11 dígitos, DDD sem zero e número começando com 9.
    // Telefone fixo não serve porque não recebe SMS nem WhatsApp.
    public static boolean ehCelularValido(String digitos) {
        return digitos.matches("[1-9]{2}9\\d{8}");
    }

    // Formata também números pela metade, para usar enquanto a pessoa digita:
    // "41" vira "(41", "4199" vira "(41) 99" e "41998765432" vira "(41) 99876-5432".
    // Com 10 dígitos usa o formato de fixo: "(41) 3333-4444".
    public static String formatarTelefone(String digitos) {
        int total = digitos.length();
        if (total == 0) {
            return "";
        }
        if (total <= 2) {
            return "(" + digitos;
        }
        String ddd = digitos.substring(0, 2);
        String numero = digitos.substring(2);
        if (numero.length() <= 4) {
            return "(" + ddd + ") " + numero;
        }
        // O hífen fica depois do 4º dígito (fixo) ou do 5º (celular)
        int hifen = numero.length() <= 8 ? 4 : 5;
        return "(" + ddd + ") " + numero.substring(0, hifen) + "-" + numero.substring(hifen);
    }

    // ---- Datas (em português) ----

    // Quantos dias de calendário se passaram: 0 = hoje, 1 = ontem.
    // De 23:59 para 00:01 já conta 1 dia, mesmo tendo passado só 2 minutos.
    public static int diasAtras(long quando, long agora, TimeZone fuso) {
        long dias = inicioDoDia(agora, fuso) - inicioDoDia(quando, fuso);
        // Arredondar absorve a hora a mais ou a menos dos dias de horário de verão
        return (int) Math.round(dias / (double) UM_DIA_MS);
    }

    // Hora no formato "14:32"
    public static String hora(long quando, TimeZone fuso) {
        return formato("HH:mm", fuso).format(new Date(quando));
    }

    // "18 de outubro"; o ano só aparece se não for o ano atual: "18 de outubro de 2025"
    public static String diaEMes(long quando, long agora, TimeZone fuso) {
        String padrao = mesmoAno(quando, agora, fuso) ? "d 'de' MMMM" : "d 'de' MMMM 'de' yyyy";
        return formato(padrao, fuso).format(new Date(quando));
    }

    // Data completa com o dia da semana: "Sábado, 18 de outubro de 2025"
    public static String dataCompleta(long quando, TimeZone fuso) {
        String data = formato("EEEE, d 'de' MMMM 'de' yyyy", fuso).format(new Date(quando));
        // O Java escreve o dia da semana em minúscula: troca a primeira letra por maiúscula
        return data.substring(0, 1).toUpperCase(PT_BR) + data.substring(1);
    }

    // Diz se as duas datas são do mesmo ano
    private static boolean mesmoAno(long a, long b, TimeZone fuso) {
        Calendar ca = Calendar.getInstance(fuso, PT_BR);
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance(fuso, PT_BR);
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR);
    }

    // Meia-noite do dia da data recebida
    private static long inicioDoDia(long instante, TimeZone fuso) {
        Calendar calendario = Calendar.getInstance(fuso, PT_BR);
        calendario.setTimeInMillis(instante);
        calendario.set(Calendar.HOUR_OF_DAY, 0);
        calendario.set(Calendar.MINUTE, 0);
        calendario.set(Calendar.SECOND, 0);
        calendario.set(Calendar.MILLISECOND, 0);
        return calendario.getTimeInMillis();
    }

    // Formatador de data em português no fuso pedido
    private static SimpleDateFormat formato(String padrao, TimeZone fuso) {
        SimpleDateFormat formato = new SimpleDateFormat(padrao, PT_BR);
        formato.setTimeZone(fuso);
        return formato;
    }

    // ---- Mensagem de alerta ----

    // Link do Google Maps para a posição: "https://maps.google.com/?q=-21.294744,-50.350441".
    // Locale.US: em pt-BR o número sairia com vírgula ("-21,294744") e o link quebraria.
    public static String linkDoMapa(double latitude, double longitude) {
        return String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", latitude, longitude);
    }

    // Tira os acentos: "Localização" vira "Localizacao". Um SMS com acento vai num formato em que
    // só cabem 70 letras (sem acento cabem 160), e a mensagem de alerta viraria 2 SMS cobrados.
    public static String semAcentos(String texto) {
        // NFD separa a letra do acento ("ã" vira "a" + "~"); \p{M} apaga as marcas de acento
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    // Contato que recebe o alerta pelo WhatsApp: o marcado como favorito ou, se nenhum foi
    // marcado, o primeiro da lista. null se não houver contatos.
    public static Contato contatoDoWhatsapp(List<Contato> contatos) {
        for (Contato contato : contatos) {
            if (contato.isFavorito()) {
                return contato;
            }
        }
        return contatos.isEmpty() ? null : contatos.get(0);
    }

    // ---- Rede ----

    // Lê a resposta inteira de uma conexão HTTP como texto (usado pela SocorroApi e pelo clima)
    public static String lerTexto(InputStream entrada) throws IOException {
        if (entrada == null) {
            return "";
        }
        BufferedReader leitor = new BufferedReader(new InputStreamReader(entrada, "UTF-8"));
        StringBuilder texto = new StringBuilder();
        String linha;
        while ((linha = leitor.readLine()) != null) {
            texto.append(linha);
        }
        leitor.close();
        return texto.toString();
    }

    // ---- Eventos do histórico ----

    // Título padronizado de cada evento (usado na lista e no diálogo de detalhes)
    public static int tituloDoEvento(Evento evento) {
        if (evento.isCancelado()) {
            return R.string.evento_cancelado;
        }
        if (evento.getTipo() == Evento.Tipo.PANICO) {
            return R.string.evento_panico;
        }
        if (evento.getTipo() == Evento.Tipo.CHECKIN) {
            return R.string.evento_checkin;
        }
        if (evento.getTipo() == Evento.Tipo.CHECKIN_SEM_RESPOSTA) {
            return R.string.evento_checkin_sem_resposta;
        }
        return R.string.evento_queda;
    }
}
