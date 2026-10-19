package com.mindup.caiu_sos;

import android.content.Context;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Conversa com a API do professor. Existem dois tipos de registro:
//   1 = telemetria normal (o MonitorService manda a cada 30 s)
//   2 = queda ou pedido de ajuda (a tela de alerta manda quando o alerta é enviado)
// Os dois montam o mesmo postData; só o tipo e o nível da queda mudam.
public class SocorroApi {

    // Endereço da API (aceita POST com corpo application/x-www-form-urlencoded)
    public static final String URL_API = "https://ideaberta.com.br/socorro/";

    // Tipos de registro que a API entende
    public static final int TIPO_TELEMETRIA = 1;
    public static final int TIPO_QUEDA = 2;

    // Etiqueta das mensagens no Logcat
    private static final String TAG = "SocorroAPI";
    // Tempo máximo para conectar e para receber a resposta
    private static final int TEMPO_LIMITE_MS = 5000;
    // Gravidade, para transformar o pico do impacto (m/s²) em G
    private static final float GRAVIDADE = 9.81f;

    // Quem chama recebe o resultado por aqui, já na thread da tela (pode mexer nas views)
    public interface Callback {
        void onSucesso(String mensagem);

        void onErro(String mensagem);
    }

    // Manda a telemetria normal (tipo 1). local, temperatura e pressao podem ser null: ficam de fora.
    public static void enviarTelemetria(Context context, Location local, Float temperatura, Float pressao,
                                        Callback callback) {
        enviar(context, TIPO_TELEMETRIA, local, temperatura, pressao, 0, callback);
    }

    // Manda uma queda ou pedido de ajuda (tipo 2). picoImpacto em m/s² (0 no pânico: sem nivel_queda).
    public static void enviarQueda(Context context, Location local, Float temperatura, Float pressao,
                                   float picoImpacto, Callback callback) {
        enviar(context, TIPO_QUEDA, local, temperatura, pressao, picoImpacto, callback);
    }

    // Faz o POST em segundo plano (rede não pode rodar na thread da tela)
    private static void enviar(Context context, int tipo, Location local, Float temperatura, Float pressao,
                               float picoImpacto, Callback callback) {
        // O contexto do app vive mais que uma tela: seguro de usar depois que a tela fechar
        Context app = context.getApplicationContext();
        // Handler da thread da tela, para devolver o resultado lá
        Handler threadDaTela = new Handler(Looper.getMainLooper());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            HttpURLConnection conexao = null;
            try {
                String postData = montarPostData(app, tipo, local, temperatura, pressao, picoImpacto);
                Log.d(TAG, "Enviando tipo " + tipo + ": " + postData);

                conexao = (HttpURLConnection) new URL(URL_API).openConnection();
                conexao.setRequestMethod("POST");
                conexao.setDoOutput(true);
                conexao.setConnectTimeout(TEMPO_LIMITE_MS);
                conexao.setReadTimeout(TEMPO_LIMITE_MS);
                conexao.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                OutputStream saida = conexao.getOutputStream();
                saida.write(postData.getBytes("UTF-8"));
                saida.close();

                // A resposta é um texto JSON: lê tudo como String e mostra no Logcat
                int codigo = conexao.getResponseCode();
                String resposta = Util.lerTexto(codigo < 400 ? conexao.getInputStream() : conexao.getErrorStream());
                Log.i(TAG, "HTTP " + codigo + ": " + resposta);

                // Deu certo quando o HTTP é 2xx e o JSON diz "status":"sucesso"
                JSONObject json = new JSONObject(resposta);
                boolean sucesso = codigo >= 200 && codigo < 300 && "sucesso".equals(json.optString("status"));
                String mensagem = json.optString("mensagem", app.getString(R.string.api_erro_http, codigo));
                // Volta para a thread da tela para avisar quem chamou
                if (sucesso) {
                    threadDaTela.post(() -> callback.onSucesso(mensagem));
                } else {
                    threadDaTela.post(() -> callback.onErro(mensagem));
                }
            } catch (Exception e) {
                // Sem internet ou servidor fora do ar: só registra e segue (SMS e WhatsApp não dependem disto)
                Log.e(TAG, "Falha ao enviar para a API", e);
                String mensagem = descreverErro(app, e);
                threadDaTela.post(() -> callback.onErro(mensagem));
            } finally {
                if (conexao != null) {
                    conexao.disconnect();
                }
            }
        });
        // Não aceita mais tarefas: a thread termina sozinha quando o envio acabar
        executor.shutdown();
    }

    // Monta o corpo no formato campo=valor&campo=valor. Campo sem valor (sensor que o aparelho
    // não tem, localização não obtida) fica de fora.
    private static String montarPostData(Context context, int tipo, Location local, Float temperatura,
                                         Float pressao, float picoImpacto) throws UnsupportedEncodingException {
        // Identifica o aparelho
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String modelo = Build.MANUFACTURER + " " + Build.MODEL;
        String usuario = Prefs.getNome(context);
        int bateria = lerBateria(context);

        String postData = "tipo_registro=" + tipo
                + "&device_id=" + URLEncoder.encode(deviceId, "UTF-8")
                + "&modelo=" + URLEncoder.encode(modelo, "UTF-8");
        if (!usuario.isEmpty()) {
            postData += "&usuario=" + URLEncoder.encode(usuario, "UTF-8");
        }
        if (bateria >= 0) {
            postData += "&bateria=" + bateria;
        }
        if (local != null) {
            postData += "&latitude=" + numero("%.6f", local.getLatitude())
                    + "&longitude=" + numero("%.6f", local.getLongitude());
            // getSpeed() vem em m/s; vezes 3,6 vira km/h
            if (local.hasSpeed()) {
                postData += "&velocidade=" + numero("%.1f", local.getSpeed() * 3.6);
            }
        }
        if (temperatura != null) {
            postData += "&temperatura=" + numero("%.1f", temperatura);
        }
        if (pressao != null) {
            postData += "&pressao=" + numero("%.1f", pressao);
        }
        // Nível da queda em G (1 G = a gravidade da Terra), só no registro de queda
        if (tipo == TIPO_QUEDA && picoImpacto > 0) {
            postData += "&nivel_queda=" + numero("%.2f", picoImpacto / GRAVIDADE);
        }
        return postData;
    }

    // Carga da bateria de 0 a 100 (-1 se o aparelho não informar)
    private static int lerBateria(Context context) {
        BatteryManager bateria = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bateria == null) {
            return -1;
        }
        int nivel = bateria.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return nivel >= 0 && nivel <= 100 ? nivel : -1;
    }

    // Número com ponto decimal (Locale.US): em pt-BR sairia "-25,4296" e a API leria errado
    private static String numero(String formato, double valor) {
        return String.format(Locale.US, formato, valor);
    }

    // Transforma o erro do Java numa frase curta para a tela
    private static String descreverErro(Context context, Exception e) {
        if (e instanceof UnknownHostException || e instanceof ConnectException) {
            return context.getString(R.string.api_sem_internet);
        }
        if (e instanceof SocketTimeoutException) {
            return context.getString(R.string.api_tempo_esgotado);
        }
        if (e instanceof JSONException) {
            return context.getString(R.string.api_resposta_invalida);
        }
        return e.getClass().getSimpleName();
    }
}
