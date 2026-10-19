package com.mindup.caiu_sos;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Busca o clima atual no OpenWeatherMap para o card da Home.
// A resposta fica guardada no Prefs por 10 minutos, para não chamar a API toda vez que a Home aparece.
public class ClimaApi {

    // Chave da conta no OpenWeatherMap
    public static final String OPENWEATHER_KEY = "bc28a87f81b3c4f336817cbc1228d357";
    private static final String URL_CLIMA = "https://api.openweathermap.org/data/2.5/weather";

    // Etiqueta das mensagens no Logcat
    private static final String TAG = "ClimaApi";
    // Tempo máximo para conectar e para receber a resposta
    private static final int TEMPO_LIMITE_MS = 5000;
    // Por quanto tempo o clima salvo continua valendo
    private static final long VALIDADE_CACHE_MS = 10 * 60 * 1000;

    // A Home recebe o clima por aqui, já na thread da tela
    public interface Callback {
        // condicao = weather[0].main ("Clear", "Clouds", "Rain"...); vento em km/h
        void onSucesso(int temperatura, String cidade, String descricao, String condicao, double ventoKmh);

        void onErro();
    }

    // Entrega o clima: do cache, se tiver menos de 10 minutos; senão, da API (precisa da localização)
    public static void buscar(Context context, Location local, Callback callback) {
        String salvo = Prefs.getClimaJson(context);
        long idade = System.currentTimeMillis() - Prefs.getClimaHora(context);
        if (salvo != null && idade < VALIDADE_CACHE_MS) {
            Log.d(TAG, "Usando o clima salvo há " + idade / 1000 + " s");
            entregar(salvo, callback);
            return;
        }
        if (local == null) {
            Log.w(TAG, "Sem localização: não dá para buscar o clima");
            callback.onErro();
            return;
        }
        buscarNaApi(context, local.getLatitude(), local.getLongitude(), callback);
    }

    // Faz o GET em segundo plano (rede não pode rodar na thread da tela)
    private static void buscarNaApi(Context context, double latitude, double longitude, Callback callback) {
        Context app = context.getApplicationContext();
        Handler threadDaTela = new Handler(Looper.getMainLooper());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            HttpURLConnection conexao = null;
            try {
                // Locale.US: em pt-BR a latitude sairia com vírgula e a URL quebraria
                String endereco = String.format(Locale.US,
                        "%s?lat=%.4f&lon=%.4f&units=metric&lang=pt_br&appid=%s",
                        URL_CLIMA, latitude, longitude, OPENWEATHER_KEY);
                conexao = (HttpURLConnection) new URL(endereco).openConnection();
                conexao.setRequestMethod("GET");
                conexao.setConnectTimeout(TEMPO_LIMITE_MS);
                conexao.setReadTimeout(TEMPO_LIMITE_MS);

                // A resposta é um texto JSON: lê tudo como String
                int codigo = conexao.getResponseCode();
                String resposta = Util.lerTexto(codigo < 400 ? conexao.getInputStream() : conexao.getErrorStream());
                Log.i(TAG, "HTTP " + codigo + ": " + resposta);
                if (codigo != HttpURLConnection.HTTP_OK) {
                    threadDaTela.post(callback::onErro);
                    return;
                }

                // Guarda para os próximos 10 minutos e mostra
                Prefs.setClima(app, resposta, System.currentTimeMillis());
                threadDaTela.post(() -> entregar(resposta, callback));
            } catch (Exception e) {
                Log.e(TAG, "Falha ao buscar o clima", e);
                threadDaTela.post(callback::onErro);
            } finally {
                if (conexao != null) {
                    conexao.disconnect();
                }
            }
        });
        executor.shutdown();
    }

    // Lê do JSON só o que o card usa e avisa a tela
    private static void entregar(String json, Callback callback) {
        try {
            JSONObject clima = new JSONObject(json);
            JSONObject tempo = clima.getJSONArray("weather").getJSONObject(0);
            int temperatura = (int) Math.round(clima.getJSONObject("main").getDouble("temp"));
            String cidade = clima.optString("name");
            String descricao = comInicialMaiuscula(tempo.optString("description"));
            String condicao = tempo.optString("main");
            // wind.speed vem em m/s; vezes 3,6 vira km/h
            JSONObject vento = clima.optJSONObject("wind");
            double ventoKmh = vento != null ? vento.optDouble("speed", 0) * 3.6 : 0;
            callback.onSucesso(temperatura, cidade, descricao, condicao, ventoKmh);
        } catch (Exception e) {
            Log.e(TAG, "JSON do clima inválido", e);
            callback.onErro();
        }
    }

    // "nuvens dispersas" vira "Nuvens dispersas"
    private static String comInicialMaiuscula(String texto) {
        if (texto.isEmpty()) {
            return texto;
        }
        return texto.substring(0, 1).toUpperCase(Locale.forLanguageTag("pt-BR")) + texto.substring(1);
    }
}
