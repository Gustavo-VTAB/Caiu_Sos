package com.mindup.caiu_sos;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Guarda tudo o que o app precisa lembrar: as configurações, os contatos e o histórico.
// Usa o SharedPreferences do Android; as listas são salvas como texto JSON com o Gson.
// Todos os métodos são estáticos, então é só chamar, por exemplo, Prefs.getTempoParaCancelar(this).
public class Prefs {

    // Nome do arquivo onde o Android guarda as preferências do app
    private static final String ARQUIVO = "caiu_sos_prefs";

    // Chave de cada valor salvo
    private static final String MONITORAMENTO_ATIVO = "monitoramento_ativo";
    private static final String NOME = "nome";
    private static final String SENSIBILIDADE = "sensibilidade";
    private static final String TEMPO_PARA_CANCELAR = "tempo_para_cancelar";
    private static final String CHECKIN_ATIVO = "checkin_ativo";
    private static final String INTERVALO_CHECKIN = "intervalo_checkin";
    private static final String ENVIAR_POR_SMS = "enviar_por_sms";
    private static final String ENVIAR_POR_WHATSAPP = "enviar_por_whatsapp";
    private static final String ENVIAR_PARA_SERVIDOR = "enviar_para_servidor";
    private static final String MODO_NOTURNO_AUTOMATICO = "modo_noturno_automatico";
    private static final String CONTATOS = "contatos";
    private static final String EVENTOS = "eventos";
    private static final String CLIMA_JSON = "clima_json";
    private static final String CLIMA_HORA = "clima_hora";

    // Valores usados enquanto a pessoa não muda nada nas Configurações
    private static final int TEMPO_PARA_CANCELAR_PADRAO = 30;
    private static final int INTERVALO_CHECKIN_PADRAO = 45;

    // Abre o arquivo de preferências do app
    private static SharedPreferences abrir(Context context) {
        return context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE);
    }

    // ---- Monitoramento e usuário ----

    // Diz se a pessoa deixou o monitoramento de quedas ligado (o switch da Home)
    public static boolean isMonitoramentoAtivo(Context context) {
        return abrir(context).getBoolean(MONITORAMENTO_ATIVO, false);
    }

    // Salva se o monitoramento está ligado
    public static void setMonitoramentoAtivo(Context context, boolean ativo) {
        abrir(context).edit().putBoolean(MONITORAMENTO_ATIVO, ativo).apply();
    }

    // Nome da pessoa, que vai na mensagem de alerta e na API ("" se ainda não foi preenchido)
    public static String getNome(Context context) {
        return abrir(context).getString(NOME, "");
    }

    // Salva o nome da pessoa
    public static void setNome(Context context, String nome) {
        abrir(context).edit().putString(NOME, nome).apply();
    }

    // ---- Detecção ----

    // Lê a sensibilidade escolhida (Média se nunca foi mudada)
    public static Sensibilidade getSensibilidade(Context context) {
        String salva = abrir(context).getString(SENSIBILIDADE, Sensibilidade.MEDIA.name());
        try {
            return Sensibilidade.valueOf(salva);
        } catch (IllegalArgumentException e) {
            // Texto salvo que não é mais um valor válido: volta para o padrão
            return Sensibilidade.MEDIA;
        }
    }

    // Salva a sensibilidade escolhida
    public static void setSensibilidade(Context context, Sensibilidade sensibilidade) {
        abrir(context).edit().putString(SENSIBILIDADE, sensibilidade.name()).apply();
    }

    // Segundos da contagem da tela de alerta antes de enviar (10 a 60)
    public static int getTempoParaCancelar(Context context) {
        return abrir(context).getInt(TEMPO_PARA_CANCELAR, TEMPO_PARA_CANCELAR_PADRAO);
    }

    // Salva os segundos da contagem
    public static void setTempoParaCancelar(Context context, int segundos) {
        abrir(context).edit().putInt(TEMPO_PARA_CANCELAR, segundos).apply();
    }

    // ---- Check-in de segurança ----

    // Diz se o check-in periódico está ligado
    public static boolean isCheckinAtivo(Context context) {
        return abrir(context).getBoolean(CHECKIN_ATIVO, false);
    }

    // Liga ou desliga o check-in periódico
    public static void setCheckinAtivo(Context context, boolean ativo) {
        abrir(context).edit().putBoolean(CHECKIN_ATIVO, ativo).apply();
    }

    // Minutos entre um check-in e outro (15 a 120)
    public static int getIntervaloCheckin(Context context) {
        return abrir(context).getInt(INTERVALO_CHECKIN, INTERVALO_CHECKIN_PADRAO);
    }

    // Salva os minutos entre um check-in e outro
    public static void setIntervaloCheckin(Context context, int minutos) {
        abrir(context).edit().putInt(INTERVALO_CHECKIN, minutos).apply();
    }

    // ---- Canais de alerta (a tela de Configurações garante que pelo menos um fica ligado) ----

    // Diz se o alerta vai por SMS
    public static boolean isEnviarPorSms(Context context) {
        return abrir(context).getBoolean(ENVIAR_POR_SMS, true);
    }

    // Liga ou desliga o envio por SMS
    public static void setEnviarPorSms(Context context, boolean enviar) {
        abrir(context).edit().putBoolean(ENVIAR_POR_SMS, enviar).apply();
    }

    // Diz se o alerta vai por WhatsApp
    public static boolean isEnviarPorWhatsapp(Context context) {
        return abrir(context).getBoolean(ENVIAR_POR_WHATSAPP, true);
    }

    // Liga ou desliga o envio por WhatsApp
    public static void setEnviarPorWhatsapp(Context context, boolean enviar) {
        abrir(context).edit().putBoolean(ENVIAR_POR_WHATSAPP, enviar).apply();
    }

    // Diz se o alerta vai para o servidor (API)
    public static boolean isEnviarParaServidor(Context context) {
        return abrir(context).getBoolean(ENVIAR_PARA_SERVIDOR, true);
    }

    // Liga ou desliga o envio para o servidor
    public static void setEnviarParaServidor(Context context, boolean enviar) {
        abrir(context).edit().putBoolean(ENVIAR_PARA_SERVIDOR, enviar).apply();
    }

    // ---- Aparência ----

    // Diz se o tema troca sozinho pelo sensor de luz (etapa 5)
    public static boolean isModoNoturnoAutomatico(Context context) {
        return abrir(context).getBoolean(MODO_NOTURNO_AUTOMATICO, false);
    }

    // Liga ou desliga a troca automática de tema
    public static void setModoNoturnoAutomatico(Context context, boolean automatico) {
        abrir(context).edit().putBoolean(MODO_NOTURNO_AUTOMATICO, automatico).apply();
    }

    // ---- Clima (cache da ClimaApi) ----

    // Última resposta do OpenWeatherMap (null se nunca buscou)
    public static String getClimaJson(Context context) {
        return abrir(context).getString(CLIMA_JSON, null);
    }

    // Quando a última resposta chegou, em milissegundos (0 se nunca buscou)
    public static long getClimaHora(Context context) {
        return abrir(context).getLong(CLIMA_HORA, 0);
    }

    // Guarda a resposta do clima e a hora em que chegou
    public static void setClima(Context context, String json, long hora) {
        abrir(context).edit().putString(CLIMA_JSON, json).putLong(CLIMA_HORA, hora).apply();
    }

    // ---- Contatos de emergência ----

    // Lê a lista de contatos salva (vazia na primeira vez que o app abre)
    public static List<Contato> carregarContatos(Context context) {
        String json = abrir(context).getString(CONTATOS, null);
        if (json == null) {
            return new ArrayList<>();
        }
        // O Gson transforma o texto JSON de volta em objetos Contato
        Contato[] contatos = new Gson().fromJson(json, Contato[].class);
        return new ArrayList<>(Arrays.asList(contatos));
    }

    // Transforma a lista de contatos em JSON e salva
    public static void salvarContatos(Context context, List<Contato> contatos) {
        String json = new Gson().toJson(contatos);
        abrir(context).edit().putString(CONTATOS, json).apply();
    }

    // ---- Histórico de eventos ----

    // Lê o histórico salvo (vazio enquanto nada foi registrado)
    public static List<Evento> carregarEventos(Context context) {
        String json = abrir(context).getString(EVENTOS, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Evento[] eventos = new Gson().fromJson(json, Evento[].class);
        return new ArrayList<>(Arrays.asList(eventos));
    }

    // Transforma o histórico em JSON e salva (chamado pelo MonitorService e pela tela de alerta)
    public static void salvarEventos(Context context, List<Evento> eventos) {
        String json = new Gson().toJson(eventos);
        abrir(context).edit().putString(EVENTOS, json).apply();
    }
}
