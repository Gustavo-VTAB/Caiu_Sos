package com.mindup.caiu_sos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.List;

// Serviço em primeiro plano (com a notificação fixa "Monitoramento ativo") que lê os sensores
// mesmo com a tela apagada. Ele não decide nada sozinho: repassa as leituras ao DetectorDeQueda
// e, quando o detector avisa que houve queda, registra no histórico e abre a tela de alerta.
// A cada 30 s também manda a telemetria (localização, bateria, barômetro...) para a API.
public class MonitorService extends Service implements SensorEventListener, DetectorDeQueda.Listener {

    // Para apresentar em sala: true divide os limiares de impacto e de rotação por 2,
    // então jogar o celular no sofá já conta como queda. No uso de verdade, deixe false.
    public static final boolean DEBUG_LIMIARES = true;

    // Etiqueta das mensagens no Logcat (filtre por "CaiuSOS")
    private static final String TAG = "CaiuSOS";
    // Ação do botão "Parar" da notificação
    private static final String ACAO_PARAR = "com.mindup.caiu_sos.PARAR";
    // Ação que o Android manda quando a pessoa apaga a notificação (o Android 14+ deixa apagar)
    private static final String ACAO_NOTIFICACAO_APAGADA = "com.mindup.caiu_sos.NOTIFICACAO_APAGADA";
    // Canais de notificação: um discreto para o monitoramento, outro urgente para a queda
    private static final String CANAL_MONITORAMENTO = "monitoramento";
    private static final String CANAL_QUEDA = "queda";
    private static final int ID_NOTIFICACAO_MONITORAMENTO = 1;
    // A tela de alerta apaga esta notificação quando abre
    static final int ID_NOTIFICACAO_QUEDA = 2;
    // De quanto em quanto tempo a telemetria vai para a API
    private static final long INTERVALO_TELEMETRIA_MS = 30000;
    // De quanto em quanto tempo o GPS manda uma posição nova
    private static final long INTERVALO_LOCALIZACAO_MS = 10000;

    // Últimas leituras do barômetro (hPa) e do termômetro (°C). null = o aparelho não tem o
    // sensor ou o serviço está parado. Estáticas para a tela de alerta mandar junto para a API.
    static Float ultimaPressao;
    static Float ultimaTemperatura;

    // Sensores
    private SensorManager sensorManager;
    private Sensor acelerometro;
    private Sensor giroscopio;
    private Sensor proximidade;
    private Sensor barometro;
    private Sensor termometro;

    private DetectorDeQueda detector;
    private PowerManager.WakeLock wakeLock;
    private FusedLocationProviderClient localizacaoCliente;
    private Location ultimaLocalizacao;
    private final Handler handler = new Handler(Looper.getMainLooper());
    // true depois que os sensores foram ligados (o serviço pode receber vários "start")
    private boolean iniciado = false;
    // true depois que o GPS foi ligado (só dá quando o serviço consegue o tipo "location")
    private boolean localizacaoLigada = false;
    // true quando o serviço está em primeiro plano só com o tipo "health" (sensores, sem GPS)
    private boolean emPrimeiroPlanoSoComSensores = false;

    // Recebe as posições novas do GPS e guarda a última
    private final LocationCallback receberLocalizacao = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult resultado) {
            ultimaLocalizacao = resultado.getLastLocation();
        }
    };

    // Roda a cada 30 s e manda a telemetria para a API
    private final Runnable telemetria = new Runnable() {
        @Override
        public void run() {
            enviarTelemetria();
            handler.postDelayed(this, INTERVALO_TELEMETRIA_MS);
        }
    };

    // Chamado uma vez, quando o serviço é criado: prepara os sensores e o detector
    @Override
    public void onCreate() {
        super.onCreate();
        criarCanais();

        // Pega cada sensor (null se o aparelho não tiver)
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        giroscopio = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        proximidade = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        barometro = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        termometro = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);

        // Sem giroscópio, o detector pula a confirmação pela rotação
        detector = new DetectorDeQueda(this, giroscopio != null);
        localizacaoCliente = LocationServices.getFusedLocationProviderClient(this);
    }

    // Chamado a cada start: ao ligar o switch, ao abrir a Home, quando o Android recria o serviço
    // (intent == null), quando o celular liga (InicioReceiver) e quando a notificação é apagada
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String acao = intent != null ? intent.getAction() : null;

        // Botão "Parar" da notificação
        if (ACAO_PARAR.equals(acao)) {
            Log.i(TAG, "Monitoramento parado pela notificação");
            Prefs.setMonitoramentoAtivo(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }

        // A pessoa apagou a notificação, mas o monitoramento continua: mostra a notificação de novo
        if (ACAO_NOTIFICACAO_APAGADA.equals(acao) && iniciado) {
            Log.i(TAG, "Notificação apagada: mostrando de novo (o monitoramento continua)");
            NotificationManager gerenciador = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            gerenciador.notify(ID_NOTIFICACAO_MONITORAMENTO, criarNotificacaoMonitoramento());
            return START_STICKY;
        }

        // Vira serviço em primeiro plano, com a notificação fixa (sem isso o Android mata o
        // serviço em poucos minutos com a tela apagada)
        boolean comLocalizacao = virarPrimeiroPlano();
        if (!comLocalizacao && !emPrimeiroPlanoSoComSensores) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // A sensibilidade pode ter mudado nas Configurações: lê de novo a cada start
        detector.setSensibilidade(Prefs.getSensibilidade(this));
        detector.setModoDemonstracao(DEBUG_LIMIARES);

        if (!iniciado) {
            iniciado = true;
            ligarSensores();
            manterProcessadorLigado();
            // A primeira telemetria sai depois de 30 s de monitoramento
            handler.postDelayed(telemetria, INTERVALO_TELEMETRIA_MS);
        }
        // O GPS só liga com o tipo "location"; se o serviço voltou sem ele, liga quando a Home abrir
        if (comLocalizacao && !localizacaoLigada) {
            ligarLocalizacao();
        }
        Log.i(TAG, "Monitoramento ativo. Sensibilidade " + Prefs.getSensibilidade(this)
                + ", limiar de impacto " + detector.getLimiarImpacto() + " m/s²"
                + ", limiar de rotação " + detector.getLimiarRotacao() + " rad/s"
                + ", GPS=" + localizacaoLigada + ", DEBUG_LIMIARES=" + DEBUG_LIMIARES);

        // START_STICKY: se o Android matar o serviço, ele é recriado depois (com intent == null)
        return START_STICKY;
    }

    // Coloca o serviço em primeiro plano. Primeiro tenta com "location" (GPS na telemetria).
    // O Android 14+ só deixa usar "location" se o app estava na tela quando o serviço começou;
    // quando o serviço é recriado com o app fechado (ou o celular reinicia), cai para "health",
    // que só usa os sensores: a detecção de queda continua funcionando.
    // Devolve true se conseguiu com "location".
    private boolean virarPrimeiroPlano() {
        Notification notificacao = criarNotificacaoMonitoramento();
        int saude = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH : 0;
        try {
            ServiceCompat.startForeground(this, ID_NOTIFICACAO_MONITORAMENTO, notificacao,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION | saude);
            emPrimeiroPlanoSoComSensores = false;
            return true;
        } catch (Exception e) {
            Log.w(TAG, "O Android não deixou usar a localização agora: " + e.getMessage());
        }
        if (saude == 0) {
            Log.e(TAG, "O Android não deixou iniciar o monitoramento");
            return false;
        }
        try {
            ServiceCompat.startForeground(this, ID_NOTIFICACAO_MONITORAMENTO, notificacao, saude);
            emPrimeiroPlanoSoComSensores = true;
            Log.i(TAG, "Monitorando só com os sensores (o GPS volta quando a Home abrir)");
        } catch (Exception e) {
            Log.e(TAG, "O Android não deixou iniciar o monitoramento", e);
            emPrimeiroPlanoSoComSensores = false;
        }
        return false;
    }

    // Chamado quando o serviço é parado: desliga tudo o que foi ligado
    @Override
    public void onDestroy() {
        sensorManager.unregisterListener(this);
        localizacaoCliente.removeLocationUpdates(receberLocalizacao);
        handler.removeCallbacks(telemetria);
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        ultimaPressao = null;
        ultimaTemperatura = null;
        Log.i(TAG, "Monitoramento desligado");
        super.onDestroy();
    }

    // Este serviço não é "ligado" (bind) por ninguém, só iniciado
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---- Sensores ----

    // Registra os sensores. SENSOR_DELAY_GAME = cerca de 50 leituras por segundo,
    // rápido o bastante para ver os ~400 ms de queda livre.
    private void ligarSensores() {
        if (acelerometro == null) {
            Log.e(TAG, "Este aparelho não tem acelerômetro: não dá para detectar quedas");
        } else {
            sensorManager.registerListener(this, acelerometro, SensorManager.SENSOR_DELAY_GAME);
        }
        if (giroscopio != null) {
            sensorManager.registerListener(this, giroscopio, SensorManager.SENSOR_DELAY_GAME);
        }
        if (proximidade != null) {
            sensorManager.registerListener(this, proximidade, SensorManager.SENSOR_DELAY_GAME);
        }
        // Barômetro e termômetro só vão na telemetria: o ritmo normal já basta
        if (barometro != null) {
            sensorManager.registerListener(this, barometro, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (termometro != null) {
            sensorManager.registerListener(this, termometro, SensorManager.SENSOR_DELAY_NORMAL);
        }
        Log.i(TAG, "Sensores: giroscópio=" + (giroscopio != null) + ", proximidade=" + (proximidade != null)
                + ", barômetro=" + (barometro != null) + ", temperatura=" + (termometro != null));
    }

    // Chamado toda vez que um dos sensores envia uma nova leitura
    @Override
    public void onSensorChanged(SensorEvent event) {
        // event.timestamp vem em nanossegundos desde que o celular ligou: vira milissegundos
        long tempoMs = event.timestamp / 1_000_000L;
        DetectorDeQueda.Estado antes = detector.getEstado();

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                // Aceleração em m/s² nos eixos x, y e z
                detector.novaAceleracao(event.values[0], event.values[1], event.values[2], tempoMs);
                break;
            case Sensor.TYPE_GYROSCOPE:
                // Velocidade de rotação em rad/s em volta de cada eixo
                detector.novaRotacao(event.values[0], event.values[1], event.values[2], tempoMs);
                break;
            case Sensor.TYPE_PROXIMITY:
                // Distância em cm (só tem values[0]). Menor que o alcance máximo = algo encostado: bolso
                boolean foraDoBolso = event.values[0] >= proximidade.getMaximumRange();
                detector.setForaDoBolso(foraDoBolso);
                Log.d(TAG, foraDoBolso ? "Proximidade: fora do bolso (limiar +5)" : "Proximidade: no bolso");
                break;
            case Sensor.TYPE_PRESSURE:
                // Pressão do ar em hPa (só tem values[0]). Fora de 300–1100 hPa é leitura inválida.
                if (event.values[0] >= 300 && event.values[0] <= 1100) {
                    ultimaPressao = event.values[0];
                }
                break;
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                // Temperatura do ambiente em °C (só tem values[0]). Alguns aparelhos (e o emulador)
                // mandam um valor absurdo quando não têm leitura: fora de -40 a 80 °C é ignorado.
                if (event.values[0] >= -40 && event.values[0] <= 80) {
                    ultimaTemperatura = event.values[0];
                }
                break;
        }

        // Mostra no Logcat cada mudança de fase da queda
        if (detector.getEstado() != antes) {
            Log.d(TAG, antes + " → " + detector.getEstado() + ": " + detector.getMotivo());
        }
    }

    // Obrigatório pela interface; a precisão dos sensores não muda nada aqui
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // Mantém o processador ligado com a tela apagada (senão as leituras param de chegar)
    @SuppressLint("WakelockTimeout") // fica ligado enquanto o monitoramento estiver ativo
    private void manterProcessadorLigado() {
        PowerManager energia = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = energia.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CaiuSOS:monitoramento");
        wakeLock.acquire();
    }

    // ---- Localização e telemetria ----

    // Pede ao GPS uma posição nova a cada 10 s (a telemetria usa a última que chegou)
    @SuppressLint("MissingPermission") // conferida logo abaixo
    private void ligarLocalizacao() {
        boolean temPermissao = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!temPermissao) {
            Log.w(TAG, "Sem permissão de localização: telemetria vai sem latitude/longitude");
            return;
        }
        LocationRequest pedido = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVALO_LOCALIZACAO_MS)
                .build();
        localizacaoCliente.requestLocationUpdates(pedido, receberLocalizacao, Looper.getMainLooper());
        localizacaoLigada = true;
    }

    // Manda para a API a localização, a bateria e os sensores do momento (tipo_registro = 1)
    private void enviarTelemetria() {
        if (!Prefs.isEnviarParaServidor(this)) {
            return;
        }
        SocorroApi.enviarTelemetria(this, ultimaLocalizacao, ultimaTemperatura, ultimaPressao,
                new SocorroApi.Callback() {
                    @Override
                    public void onSucesso(String mensagem) {
                        Log.d(TAG, "Telemetria enviada: " + mensagem);
                    }

                    @Override
                    public void onErro(String mensagem) {
                        Log.w(TAG, "Telemetria não enviada: " + mensagem);
                    }
                });
    }

    // ---- Queda ----

    // Chamado pelo DetectorDeQueda quando ele confirma uma queda
    @Override
    public void onQuedaDetectada(float pico) {
        Log.w(TAG, "QUEDA DETECTADA! Pico de " + pico + " m/s²");
        // Se já tem uma contagem correndo (outra queda, pânico ou teste), não abre outra por cima
        if (AlertaActivity.contagemAtiva) {
            Log.i(TAG, "Já tem um alerta em contagem: queda ignorada");
            return;
        }

        // Registra no histórico agora; a tela de alerta completa depois com o endereço e o resultado
        long quando = System.currentTimeMillis();
        List<Evento> eventos = Prefs.carregarEventos(this);
        eventos.add(new Evento(Evento.Tipo.QUEDA, false, quando, getString(R.string.evento_sem_localizacao), 0, 0));
        Prefs.salvarEventos(this, eventos);

        Intent intent = new Intent(this, AlertaActivity.class);
        intent.putExtra(AlertaActivity.EXTRA_MODO, AlertaActivity.MODO_QUEDA);
        intent.putExtra(AlertaActivity.EXTRA_IMPACTO, pico);
        intent.putExtra(AlertaActivity.EXTRA_QUANDO, quando);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Android 10+ não deixa um serviço abrir uma tela quando o app está em segundo plano.
        // A notificação com fullScreenIntent resolve: com a tela apagada ou bloqueada, ela abre
        // o alerta direto; com o celular em uso, aparece no topo da tela.
        mostrarNotificacaoDeQueda(intent);
        // Com o app aberto na tela o Android deixa abrir direto (senão ele só ignora este pedido)
        startActivity(intent);
    }

    // ---- Notificações ----

    // Cria os canais de notificação (obrigatório a partir do Android 8)
    private void criarCanais() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager gerenciador = getSystemService(NotificationManager.class);
        // Monitoramento: importância baixa, sem som (fica parada na barra)
        gerenciador.createNotificationChannel(new NotificationChannel(CANAL_MONITORAMENTO,
                getString(R.string.canal_monitoramento), NotificationManager.IMPORTANCE_LOW));
        // Queda: importância alta, com som, pode abrir em tela cheia
        gerenciador.createNotificationChannel(new NotificationChannel(CANAL_QUEDA,
                getString(R.string.canal_queda), NotificationManager.IMPORTANCE_HIGH));
    }

    // Notificação fixa "Monitoramento ativo" com o botão "Parar"
    private Notification criarNotificacaoMonitoramento() {
        // Tocar na notificação abre a Home
        PendingIntent abrirApp = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        // "Parar" manda a ACAO_PARAR para este mesmo serviço
        Intent parar = new Intent(this, MonitorService.class).setAction(ACAO_PARAR);
        PendingIntent pararServico = PendingIntent.getService(this, 0, parar, PendingIntent.FLAG_IMMUTABLE);
        // Se a pessoa apagar a notificação (ou tocar em "Limpar"), o Android avisa o serviço,
        // que mostra a notificação de novo: o monitoramento só para pelo switch ou pelo "Parar"
        Intent apagada = new Intent(this, MonitorService.class).setAction(ACAO_NOTIFICACAO_APAGADA);
        PendingIntent aoApagar = PendingIntent.getService(this, 1, apagada, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CANAL_MONITORAMENTO)
                .setSmallIcon(R.drawable.ic_escudo_ok)
                .setContentTitle(getString(R.string.status_ativo))
                .setContentText(getString(DEBUG_LIMIARES
                        ? R.string.notificacao_modo_demonstracao
                        : R.string.status_ativo_descricao))
                .setOngoing(true)
                .setContentIntent(abrirApp)
                .setDeleteIntent(aoApagar)
                .addAction(R.drawable.ic_cancelado, getString(R.string.notificacao_parar), pararServico)
                .build();
    }

    // Notificação urgente da queda, que abre a tela de alerta em tela cheia
    private void mostrarNotificacaoDeQueda(Intent abrirAlerta) {
        // Sem permissão de notificação (Android 13+) o Android descartaria a notificação
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Sem permissão de notificação: o alerta só abre se o app estiver na tela");
            return;
        }
        PendingIntent pendente = PendingIntent.getActivity(this, 0, abrirAlerta,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notificacao = new NotificationCompat.Builder(this, CANAL_QUEDA)
                .setSmallIcon(R.drawable.ic_emergencia)
                .setContentTitle(getString(R.string.alerta_titulo_queda))
                .setContentText(getString(R.string.notificacao_queda_texto))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendente, true)
                .setContentIntent(pendente)
                .setAutoCancel(true)
                .build();
        NotificationManager gerenciador = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        gerenciador.notify(ID_NOTIFICACAO_QUEDA, notificacao);
    }
}
