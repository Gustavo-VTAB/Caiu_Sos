package com.mindup.caiu_sos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Group;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Tela de alerta: contagem regressiva com vibração. Se a pessoa não segurar
// "ESTOU BEM" até o fim, o alerta é enviado por SMS, WhatsApp e para a API.
// Implementa OnTouchListener para acompanhar o dedo no botão "ESTOU BEM".
public class AlertaActivity extends AppCompatActivity implements View.OnTouchListener {

    // Extra do Intent que diz por que a tela abriu: queda, pedido de ajuda (pânico) ou teste
    public static final String EXTRA_MODO = "com.mindup.caiu_sos.MODO";
    // Pico do impacto em m/s² (só na queda) e a hora da queda (para achar o evento que o
    // MonitorService já registrou no histórico)
    public static final String EXTRA_IMPACTO = "com.mindup.caiu_sos.IMPACTO";
    public static final String EXTRA_QUANDO = "com.mindup.caiu_sos.QUANDO";
    public static final String MODO_QUEDA = "queda";
    public static final String MODO_PANICO = "panico";
    public static final String MODO_TESTE = "teste";

    // true só enquanto a contagem está correndo (o MonitorService não abre outra por cima).
    // Depois do envio a tela de resultado pode ficar aberta atrás de outras (ou com o celular
    // bloqueado), e uma queda nova tem que abrir um alerta novo.
    static boolean contagemAtiva = false;
    // Hora da última queda que já abriu esta tela. A mesma queda chega duas vezes (o serviço abre
    // direto e também pela notificação de tela cheia): a segunda é fechada na hora.
    private static long ultimaQuedaMostrada = 0;

    // Etiqueta das mensagens no Logcat
    private static final String TAG = "CaiuSOS";
    // Tempo segurando "ESTOU BEM" para cancelar (evita cancelar sem querer, no bolso)
    private static final long TEMPO_SEGURAR_CANCELAR_MS = 1500;
    // De quanto em quanto tempo a contagem é atualizada na tela
    private static final long INTERVALO_TIQUE_MS = 50;
    // Vibração: espera 0, vibra 0,8 s, para 0,4 s... e repete
    private static final long[] PADRAO_VIBRACAO = {0, 800, 400};
    // A última localização conhecida só vale se tiver menos de 2 minutos
    private static final long IDADE_MAXIMA_LOCALIZACAO_MS = 2 * 60 * 1000;
    // Se a contagem zerar antes de a localização chegar, espera por ela no máximo 15 s
    private static final long ESPERA_LOCALIZACAO_MS = 15000;
    // Se o alerta saiu sem localização, o GPS continua procurando por até 3 minutos
    // (de 5 em 5 s); quando achar, vai um segundo SMS só com o mapa
    private static final long PROCURA_LOCALIZACAO_MS = 3 * 60 * 1000;
    private static final long INTERVALO_PROCURA_MS = 5000;

    // Chaves para guardar o estado se a tela for recriada
    private static final String ESTADO_FIM_DA_CONTAGEM = "fim_da_contagem";
    private static final String ESTADO_ENVIADO = "enviado";
    private static final String ESTADO_QUANDO = "quando";
    private static final String ESTADO_TITULO = "titulo";
    private static final String ESTADO_SUBTITULO = "subtitulo";

    // Views da tela
    private View telaAlerta;
    private TextView tituloAlerta;
    private TextView subtituloAlerta;
    private CircularProgressIndicator progressoContagem;
    private TextView textoContagem;
    private Group grupoContagem;
    private ImageView iconeEnviado;
    private MaterialButton botaoEnviarAgora;
    private View botaoEstouBem;
    private View preenchimentoEstouBem;
    private TextView textoEstouBem;
    private TextView textoSegureCancelar;
    private TextView textoLocalizacao;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Vibrator vibrador;

    private String modo;
    private long duracaoMs;
    // Hora em que a contagem acaba, em SystemClock.elapsedRealtime()
    private long fimDaContagem;
    private int segundosMostrados = -1;
    // true depois que o alerta foi enviado ou cancelado
    private boolean encerrado = false;
    private boolean enviado = false;
    private boolean cancelado = false;

    // Controle do "segurar para cancelar"
    private boolean segurandoEstouBem = false;
    private long inicioToqueEstouBem;

    // Dados do alerta
    private float impacto;
    private long quando;
    private FusedLocationProviderClient localizacaoCliente;
    // A melhor localização que deu para obter (null se nenhuma)
    private Location localizacao;
    private boolean localizacaoTerminou = false;
    private boolean esperandoLocalizacao = false;
    // true depois que o envio pelos canais começou (garante que só acontece uma vez)
    private boolean disparado = false;
    // true enquanto o GPS continua procurando depois de um alerta que saiu sem localização
    private boolean procurandoLocalizacao = false;

    // Resultado de cada canal automático, uma linha na tela (null = canal desligado)
    private String resultadoSms;
    private String resultadoLocalizacaoAtrasada;
    private String resultadoServidor;
    private boolean servidorPendente = false;
    private int canaisOk = 0;
    // Endereço que vai para o histórico (coordenadas até o Geocoder achar a rua)
    private String enderecoEvento;
    // true quando esta tela é a segunda cópia da mesma queda e vai fechar sem mostrar nada
    private boolean duplicada = false;

    // WhatsApp: o contato favorito, a mensagem (guardada para o botão abrir de novo) e a linha da tela
    private Contato contatoWhatsapp;
    private String mensagemWhatsapp;
    private String resultadoWhatsapp;

    // Roda a cada 50 ms e atualiza a contagem. Usa o relógio, e não animação, para
    // seguir certa mesmo com o app em segundo plano ou com "Remover animações" ligado.
    private final Runnable tique = new Runnable() {
        @Override
        public void run() {
            long restante = fimDaContagem - SystemClock.elapsedRealtime();
            if (restante <= 0 && segurandoEstouBem) {
                // Quem começou a cancelar no último segundo pode terminar o gesto
                mostrarContagem(0);
                handler.postDelayed(this, INTERVALO_TIQUE_MS);
            } else if (restante <= 0) {
                enviarAlerta();
            } else {
                mostrarContagem(restante);
                handler.postDelayed(this, INTERVALO_TIQUE_MS);
            }
        }
    };

    // Roda a cada quadro enquanto o dedo está em "ESTOU BEM" e vai enchendo o botão
    private final Runnable atualizarEstouBem = new Runnable() {
        @Override
        public void run() {
            if (!segurandoEstouBem) {
                return;
            }
            long passou = SystemClock.uptimeMillis() - inicioToqueEstouBem;
            float progresso = Math.min(1f, passou / (float) TEMPO_SEGURAR_CANCELAR_MS);
            preenchimentoEstouBem.setScaleX(progresso);

            if (progresso < 1f) {
                botaoEstouBem.postOnAnimation(this);
            } else {
                // Segurou o tempo todo: vibra de leve e cancela o alerta
                segurandoEstouBem = false;
                botaoEstouBem.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                cancelarAlerta();
            }
        }
    };

    // A contagem zerou, mas a localização não chegou em 15 s: envia sem ela
    private final Runnable desistirDaLocalizacao = new Runnable() {
        @Override
        public void run() {
            Log.w(TAG, "A localização não chegou a tempo: enviando sem ela");
            dispararEnvio();
        }
    };

    // Pede a localização de novo (roda a cada 5 s enquanto o GPS procura depois do alerta)
    private final Runnable tentarLocalizacaoDeNovo = new Runnable() {
        @Override
        public void run() {
            pedirLocalizacaoAtrasada();
        }
    };

    // Passaram os 3 minutos de procura e o GPS não achou nada: desiste
    private final Runnable desistirDaProcura = new Runnable() {
        @Override
        public void run() {
            Log.w(TAG, "GPS não achou a localização em 3 minutos: desistindo");
            pararProcuraDeLocalizacao();
        }
    };

    // Chamado quando a tela é criada
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Esta queda já abriu uma tela de alerta: fecha esta cópia antes de mostrar qualquer coisa
        long quedaRecebida = getIntent().getLongExtra(EXTRA_QUANDO, 0);
        if (savedInstanceState == null && quedaRecebida != 0 && quedaRecebida == ultimaQuedaMostrada) {
            Log.i(TAG, "Esta queda já foi mostrada: fechando a tela repetida");
            duplicada = true;
            finish();
            return;
        }
        ultimaQuedaMostrada = quedaRecebida;

        // Fundo sempre vermelho: ícones das barras sempre claros, mesmo no modo claro do sistema
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT));
        mostrarSobreTelaDeBloqueio();
        setContentView(R.layout.activity_alerta);

        // Liga as variáveis às views do layout
        telaAlerta = findViewById(R.id.main);
        tituloAlerta = findViewById(R.id.tituloAlerta);
        subtituloAlerta = findViewById(R.id.subtituloAlerta);
        progressoContagem = findViewById(R.id.progressoContagem);
        textoContagem = findViewById(R.id.textoContagem);
        grupoContagem = findViewById(R.id.grupoContagem);
        iconeEnviado = findViewById(R.id.iconeEnviado);
        botaoEnviarAgora = findViewById(R.id.botaoEnviarAgora);
        botaoEstouBem = findViewById(R.id.botaoEstouBem);
        preenchimentoEstouBem = findViewById(R.id.preenchimentoEstouBem);
        textoEstouBem = findViewById(R.id.textoEstouBem);
        textoSegureCancelar = findViewById(R.id.textoSegureCancelar);
        textoLocalizacao = findViewById(R.id.textoLocalizacao);

        // Tela cheia: deixa espaço para as barras do sistema em cima e embaixo
        ViewCompat.setOnApplyWindowInsetsListener(telaAlerta, (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        // A tela abriu: a notificação "Queda detectada" do MonitorService não é mais necessária
        NotificationManagerCompat.from(this).cancel(MonitorService.ID_NOTIFICACAO_QUEDA);

        // Lê o que veio no Intent: o modo (queda, se não veio nada), o impacto e a hora
        modo = getIntent().getStringExtra(EXTRA_MODO);
        if (modo == null) {
            modo = MODO_QUEDA;
        }
        impacto = getIntent().getFloatExtra(EXTRA_IMPACTO, 0f);
        if (savedInstanceState != null) {
            quando = savedInstanceState.getLong(ESTADO_QUANDO);
        } else {
            quando = getIntent().getLongExtra(EXTRA_QUANDO, System.currentTimeMillis());
        }
        duracaoMs = Prefs.getTempoParaCancelar(this) * 1000L;
        vibrador = pegarVibrador();

        configurarTextos();
        configurarBotoes();
        configurarVoltar();

        if (savedInstanceState != null && savedInstanceState.getBoolean(ESTADO_ENVIADO)) {
            // A tela foi recriada depois do envio: só mostra o resultado de novo
            encerrado = true;
            enviado = true;
            disparado = true;
            prepararTelaFinal();
            tituloAlerta.setText(savedInstanceState.getCharSequence(ESTADO_TITULO));
            subtituloAlerta.setText(savedInstanceState.getCharSequence(ESTADO_SUBTITULO));
            textoLocalizacao.setVisibility(View.INVISIBLE);
        } else {
            // Começa a contagem (ou continua de onde parou, se a tela foi recriada)
            if (savedInstanceState != null) {
                fimDaContagem = savedInstanceState.getLong(ESTADO_FIM_DA_CONTAGEM);
            } else {
                fimDaContagem = SystemClock.elapsedRealtime() + duracaoMs;
            }
            Log.i(TAG, "Tela de alerta aberta: modo " + modo + ", impacto " + impacto + " m/s²");
            contagemAtiva = true;
            iniciarVibracao();
            buscarLocalizacao();
            tique.run();
        }
    }

    // Guarda a contagem e o resultado antes de a tela ser recriada
    @Override
    protected void onSaveInstanceState(Bundle estado) {
        super.onSaveInstanceState(estado);
        estado.putLong(ESTADO_FIM_DA_CONTAGEM, fimDaContagem);
        estado.putBoolean(ESTADO_ENVIADO, enviado);
        estado.putLong(ESTADO_QUANDO, quando);
        estado.putCharSequence(ESTADO_TITULO, tituloAlerta.getText());
        estado.putCharSequence(ESTADO_SUBTITULO, subtituloAlerta.getText());
    }

    // Chamado quando a tela é fechada: para a contagem e a vibração
    @Override
    protected void onDestroy() {
        // A cópia repetida não ligou nada (e não pode marcar a tela de verdade como fechada)
        if (duplicada) {
            super.onDestroy();
            return;
        }
        handler.removeCallbacks(tique);
        handler.removeCallbacks(desistirDaLocalizacao);
        pararVibracao();
        // Fechou enquanto esperava a localização: o alerta não pode se perder, envia sem ela
        if (esperandoLocalizacao) {
            dispararEnvio();
        }
        pararProcuraDeLocalizacao();
        // Fechou no meio da contagem. (Se já tinha acabado, contagemAtiva já é false, e pode ser
        // true de outro alerta que abriu depois: aí não mexe.)
        if (!encerrado) {
            contagemAtiva = false;
        }
        super.onDestroy();
    }

    // Faz a tela aparecer por cima da tela de bloqueio, acender o display e ficar acesa
    @SuppressWarnings("deprecation") // as flags são o único jeito nas APIs 24–26
    private void mostrarSobreTelaDeBloqueio() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // Diz se a tela foi aberta pelo "Testar alerta" (não envia nada)
    private boolean ehTeste() {
        return MODO_TESTE.equals(modo);
    }

    // Escolhe o título e o subtítulo conforme o modo
    private void configurarTextos() {
        int titulo;
        switch (modo) {
            case MODO_PANICO:
                titulo = R.string.alerta_titulo_panico;
                break;
            case MODO_TESTE:
                titulo = R.string.alerta_titulo_teste;
                break;
            default:
                titulo = R.string.alerta_titulo_queda;
        }
        tituloAlerta.setText(titulo);
        subtituloAlerta.setText(ehTeste() ? R.string.alerta_simulando_em : R.string.alerta_enviando_em);
        ViewCompat.setAccessibilityHeading(tituloAlerta, true);
    }

    // Prepara "Enviar alerta agora" e o "ESTOU BEM" (segurar 1,5 s para cancelar)
    private void configurarBotoes() {
        botaoEnviarAgora.setOnClickListener(v -> enviarAlerta());

        // Recorta o preenchimento verde nos cantos arredondados do botão
        botaoEstouBem.setClipToOutline(true);
        botaoEstouBem.setOnTouchListener(this);

        // Com o TalkBack não dá para cronometrar o dedo: o "tocar duas vezes e segurar"
        // do leitor de tela já cancela direto
        ViewCompat.replaceAccessibilityAction(botaoEstouBem, AccessibilityActionCompat.ACTION_LONG_CLICK,
                getString(R.string.alerta_acao_cancelar), (view, argumentos) -> {
                    cancelarAlerta();
                    return true;
                });

        // Só chega aqui quem tocou e soltou antes de 1,5 s
        botaoEstouBem.setOnClickListener(v -> mostrarComoCancelar());
    }

    // Chamado toda vez que o dedo encosta, mexe ou sai do "ESTOU BEM"
    @SuppressLint("ClickableViewAccessibility") // performClick é chamado ao soltar antes do tempo
    @Override
    public boolean onTouch(View v, MotionEvent evento) {
        switch (evento.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Dedo encostou: começa a contar e a encher o botão
                v.getParent().requestDisallowInterceptTouchEvent(true);
                v.drawableHotspotChanged(evento.getX(), evento.getY());
                v.setPressed(true);
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                inicioToqueEstouBem = SystemClock.uptimeMillis();
                segurandoEstouBem = true;
                preenchimentoEstouBem.animate().cancel();
                v.postOnAnimation(atualizarEstouBem);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // Dedo saiu: se ainda não deu o tempo, o preenchimento volta e nada é cancelado
                v.getParent().requestDisallowInterceptTouchEvent(false);
                v.setPressed(false);
                if (segurandoEstouBem) {
                    segurandoEstouBem = false;
                    v.removeCallbacks(atualizarEstouBem);
                    preenchimentoEstouBem.animate().scaleX(0f).setDuration(200).start();
                    if (evento.getActionMasked() == MotionEvent.ACTION_UP) {
                        // Vira um clique comum, que mostra a dica "segure"
                        v.performClick();
                    }
                }
                return true;

            default:
                return true;
        }
    }

    // "Voltar" não cancela: seria fácil demais cancelar sem querer
    private void configurarVoltar() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (encerrado) {
                    finish();
                } else {
                    mostrarComoCancelar();
                }
            }
        });
    }

    // Mostra a dica "segure ESTOU BEM para cancelar"
    private void mostrarComoCancelar() {
        Snackbar.make(telaAlerta, R.string.alerta_como_cancelar, Snackbar.LENGTH_SHORT)
                .setAnchorView(botaoEstouBem)
                .show();
    }

    // Atualiza o anel e o número de segundos que faltam
    private void mostrarContagem(long restanteMs) {
        int max = progressoContagem.getMax();
        progressoContagem.setProgress((int) (restanteMs * max / duracaoMs));

        // Só troca o texto quando o número muda (o tique roda 20 vezes por segundo)
        int segundos = (int) Math.ceil(restanteMs / 1000.0);
        if (segundos != segundosMostrados) {
            segundosMostrados = segundos;
            textoContagem.setText(String.format(Locale.getDefault(), "%d", segundos));
        }
    }

    // A pessoa segurou "ESTOU BEM": para tudo, registra no histórico e fecha a tela
    private void cancelarAlerta() {
        if (encerrado) {
            return;
        }
        encerrado = true;
        contagemAtiva = false;
        cancelado = true;
        handler.removeCallbacks(tique);
        pararVibracao();
        Log.i(TAG, "Alerta cancelado pela pessoa");
        if (!ehTeste()) {
            registrarNoHistorico();
        }
        Toast.makeText(this, R.string.alerta_cancelado, Toast.LENGTH_SHORT).show();
        finish();
    }

    // A contagem zerou (ou a pessoa tocou em "Enviar agora"): envia o alerta
    private void enviarAlerta() {
        if (encerrado) {
            return;
        }
        encerrado = true;
        contagemAtiva = false;
        enviado = true;
        handler.removeCallbacks(tique);
        pararVibracao();
        prepararTelaFinal();

        // "Testar alerta" para aqui: não manda SMS, WhatsApp nem API
        if (ehTeste()) {
            tituloAlerta.setText(R.string.alerta_teste_concluido);
            subtituloAlerta.setText(R.string.alerta_teste_concluido_descricao);
            return;
        }

        tituloAlerta.setText(R.string.alerta_enviando);
        if (localizacaoTerminou) {
            dispararEnvio();
        } else {
            // Espera a localização mais um pouco, para o link do mapa ir junto
            esperandoLocalizacao = true;
            subtituloAlerta.setText(R.string.alerta_aguardando_localizacao);
            handler.postDelayed(desistirDaLocalizacao, ESPERA_LOCALIZACAO_MS);
        }
    }

    // Troca a tela para o modo "resultado": some a contagem e "ESTOU BEM" vira "FECHAR"
    private void prepararTelaFinal() {
        progressoContagem.setProgress(progressoContagem.getMax());
        grupoContagem.setVisibility(View.GONE);
        iconeEnviado.setVisibility(View.VISIBLE);
        botaoEnviarAgora.setVisibility(View.INVISIBLE);

        // Tira o "segurar para cancelar": agora o botão é um botão comum
        segurandoEstouBem = false;
        botaoEstouBem.removeCallbacks(atualizarEstouBem);
        botaoEstouBem.setPressed(false);
        botaoEstouBem.setOnTouchListener(null);
        ViewCompat.replaceAccessibilityAction(botaoEstouBem, AccessibilityActionCompat.ACTION_LONG_CLICK, null, null);
        preenchimentoEstouBem.animate().cancel();
        preenchimentoEstouBem.setScaleX(0f);

        // "ESTOU BEM" vira "FECHAR" com toque simples
        textoEstouBem.setText(R.string.alerta_fechar);
        textoSegureCancelar.setVisibility(View.GONE);
        botaoEstouBem.setContentDescription(getString(R.string.alerta_fechar));
        botaoEstouBem.setOnClickListener(v -> finish());
    }

    // ---- Localização ----

    // Pede a localização assim que a tela abre, para ela já estar pronta quando a contagem zerar.
    // Primeiro a última conhecida (é instantânea); se não tiver ou for velha, pede uma leitura nova.
    @SuppressLint("MissingPermission") // conferida logo no começo
    private void buscarLocalizacao() {
        if (!temPermissaoDeLocalizacao()) {
            Log.w(TAG, "Sem permissão de localização");
            terminarLocalizacao(null);
            return;
        }
        mostrarLocalizacao(R.string.alerta_localizacao_buscando, R.drawable.ic_localizacao, Color.WHITE);
        localizacaoCliente = LocationServices.getFusedLocationProviderClient(this);
        localizacaoCliente.getLastLocation()
                .addOnSuccessListener(ultima -> {
                    boolean recente = ultima != null
                            && System.currentTimeMillis() - ultima.getTime() < IDADE_MAXIMA_LOCALIZACAO_MS;
                    if (recente) {
                        terminarLocalizacao(ultima);
                    } else {
                        pedirLocalizacaoAtual(ultima);
                    }
                })
                .addOnFailureListener(erro -> pedirLocalizacaoAtual(null));
    }

    // Pede uma leitura nova ao GPS. Se não vier, fica com a última conhecida, mesmo antiga.
    @SuppressLint("MissingPermission") // conferida em buscarLocalizacao()
    private void pedirLocalizacaoAtual(Location antiga) {
        localizacaoCliente.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(atual -> terminarLocalizacao(atual != null ? atual : antiga))
                .addOnFailureListener(erro -> terminarLocalizacao(antiga));
    }

    // Diz se a pessoa deu a permissão de localização (precisa ou aproximada)
    private boolean temPermissaoDeLocalizacao() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // A busca terminou (com ou sem localização): mostra no rodapé e, se a contagem já zerou, envia
    private void terminarLocalizacao(Location local) {
        if (localizacaoTerminou || isDestroyed()) {
            return;
        }
        localizacaoTerminou = true;
        // O alerta já saiu sem localização (o GPS passou dos 15 s de espera): vai num segundo SMS
        if (disparado) {
            enviarLocalizacaoAtrasada(local);
            return;
        }
        localizacao = local;
        if (local != null) {
            Log.i(TAG, "Localização obtida: " + local.getLatitude() + ", " + local.getLongitude());
            mostrarLocalizacao(R.string.alerta_localizacao_obtida, R.drawable.ic_check_circulo,
                    ContextCompat.getColor(this, R.color.safe_green_claro));
        } else {
            mostrarLocalizacao(R.string.alerta_localizacao_indisponivel, R.drawable.ic_aviso,
                    ContextCompat.getColor(this, R.color.warning_amber));
        }
        if (esperandoLocalizacao) {
            dispararEnvio();
        }
    }

    // Troca o texto, o ícone e a cor da linha de localização do rodapé
    private void mostrarLocalizacao(int texto, int icone, int cor) {
        textoLocalizacao.setText(texto);
        textoLocalizacao.setCompoundDrawablesRelativeWithIntrinsicBounds(icone, 0, 0, 0);
        TextViewCompat.setCompoundDrawableTintList(textoLocalizacao, ColorStateList.valueOf(cor));
    }

    // ---- Envio do alerta ----

    // Manda o alerta por todos os canais ligados nas Configurações (só uma vez)
    private void dispararEnvio() {
        if (disparado) {
            return;
        }
        disparado = true;
        esperandoLocalizacao = false;
        handler.removeCallbacks(desistirDaLocalizacao);

        String mensagem = montarMensagem();
        List<Contato> contatos = Prefs.carregarContatos(this);
        Log.i(TAG, "Enviando alerta: " + mensagem);

        // 1. SMS para cada contato
        if (contatos.isEmpty()) {
            resultadoSms = getString(R.string.alerta_sem_contatos);
        } else if (Prefs.isEnviarPorSms(this)) {
            int enviados = enviarSms(mensagem, contatos);
            if (enviados > 0) {
                canaisOk++;
            }
            resultadoSms = textoDoSms(enviados);
        }

        // Saiu sem localização: o GPS continua procurando para mandar o mapa num segundo SMS
        if (localizacao == null) {
            continuarProcurandoLocalizacao();
        }

        // 2. API do professor (tipo 2). Roda em segundo plano; o resultado chega pelo callback.
        if (Prefs.isEnviarParaServidor(this)) {
            servidorPendente = true;
            resultadoServidor = getString(R.string.alerta_servidor_enviando);
            SocorroApi.enviarQueda(this, localizacao, MonitorService.ultimaTemperatura,
                    MonitorService.ultimaPressao, impacto, new SocorroApi.Callback() {
                        @Override
                        public void onSucesso(String resposta) {
                            servidorPendente = false;
                            canaisOk++;
                            resultadoServidor = getString(R.string.alerta_servidor_ok);
                            mostrarResultado();
                            salvarEvento();
                        }

                        @Override
                        public void onErro(String erro) {
                            servidorPendente = false;
                            resultadoServidor = getString(R.string.alerta_servidor_falha, erro);
                            mostrarResultado();
                            salvarEvento();
                        }
                    });
        }

        // 3. Histórico (com o endereço, que chega depois)
        registrarNoHistorico();

        // 4. WhatsApp para o contato favorito. Nenhum app consegue enviar pelo WhatsApp sozinho:
        // o máximo é abrir a conversa com a mensagem pronta, e a pessoa toca em enviar.
        // Por isso vai para um contato só (um toque) e o SMS, que é automático, vai para todos.
        contatoWhatsapp = Util.contatoDoWhatsapp(contatos);
        if (Prefs.isEnviarPorWhatsapp(this) && contatoWhatsapp != null) {
            mensagemWhatsapp = mensagem;
            resultadoWhatsapp = getString(R.string.alerta_whatsapp_resultado, contatoWhatsapp.getPrimeiroNome());
            // O botão fica na tela para abrir de novo (se a pessoa voltou sem enviar)
            botaoEnviarAgora.setText(getString(R.string.alerta_whatsapp_para, contatoWhatsapp.getPrimeiroNome()));
            botaoEnviarAgora.setPaintFlags(botaoEnviarAgora.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            botaoEnviarAgora.setOnClickListener(v -> abrirWhatsapp());
            botaoEnviarAgora.setVisibility(View.VISIBLE);
        }
        mostrarResultado();
        // Já abre a conversa uma vez, sem a pessoa precisar achar o botão
        if (resultadoWhatsapp != null) {
            abrirWhatsapp();
        }
    }

    // Monta o texto do SMS/WhatsApp: quem é, o que aconteceu e o link do mapa
    private String montarMensagem() {
        String onde = getString(R.string.mensagem_sem_localizacao);
        if (localizacao != null) {
            onde = Util.linkDoMapa(localizacao.getLatitude(), localizacao.getLongitude());
        }
        int texto = MODO_PANICO.equals(modo) ? R.string.mensagem_panico : R.string.mensagem_queda;
        return getString(texto, nomeNaMensagem(), onde);
    }

    // Nome que aparece na mensagem ("Um contato seu" se a pessoa não preencheu nas Configurações)
    private String nomeNaMensagem() {
        String nome = Prefs.getNome(this);
        return nome.isEmpty() ? getString(R.string.mensagem_nome_padrao) : nome;
    }

    // Manda o SMS para cada contato. Devolve quantos foram enviados (-1 = sem permissão de SMS).
    // Cada SMS sai do chip da pessoa e é cobrado conforme o plano dela.
    @SuppressWarnings("deprecation") // SmsManager.getDefault() é o único jeito nas APIs 24–30
    private int enviarSms(String mensagem, List<Contato> contatos) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "SMS: sem permissão");
            return -1;
        }
        SmsManager sms;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sms = getSystemService(SmsManager.class);
        } else {
            sms = SmsManager.getDefault();
        }
        if (sms == null) {
            return 0;
        }

        // Sem acento a mensagem cabe em 1 SMS (160 letras); com acento seriam 2 (70 letras cada)
        ArrayList<String> partes = sms.divideMessage(Util.semAcentos(mensagem));
        int enviados = 0;
        for (Contato contato : contatos) {
            try {
                // +55 na frente: o número salvo tem só o DDD e o celular
                sms.sendMultipartTextMessage("+55" + contato.getTelefone(), null, partes, null, null);
                enviados++;
                Log.i(TAG, "SMS enviado para " + contato.getNome() + " (" + partes.size() + " SMS)");
            } catch (Exception e) {
                Log.e(TAG, "Falha no SMS para " + contato.getNome(), e);
            }
        }
        return enviados;
    }

    // Linha da tela para o resultado do SMS
    private String textoDoSms(int enviados) {
        if (enviados < 0) {
            return getString(R.string.alerta_sms_sem_permissao);
        }
        if (enviados == 0) {
            return getString(R.string.alerta_sms_falhou);
        }
        return getResources().getQuantityString(R.plurals.alerta_sms_enviado, enviados, enviados);
    }

    // ---- Localização que chega depois do alerta ----

    // O alerta saiu sem localização: continua pedindo ao GPS (de 5 em 5 s, por até 3 minutos)
    private void continuarProcurandoLocalizacao() {
        if (procurandoLocalizacao || !temPermissaoDeLocalizacao()) {
            return;
        }
        if (localizacaoCliente == null) {
            localizacaoCliente = LocationServices.getFusedLocationProviderClient(this);
        }
        procurandoLocalizacao = true;
        handler.postDelayed(desistirDaProcura, PROCURA_LOCALIZACAO_MS);
        Log.i(TAG, "Alerta saiu sem localização: o GPS continua procurando por até 3 minutos");
        pedirLocalizacaoAtrasada();
    }

    // Pede uma leitura nova ao GPS. Se não vier (sem sinal ou localização desligada no celular),
    // tenta de novo em 5 s. Cada pedido é novo, então funciona mesmo se a pessoa ligar a
    // localização no meio do caminho.
    @SuppressLint("MissingPermission") // conferida em continuarProcurandoLocalizacao()
    private void pedirLocalizacaoAtrasada() {
        if (!procurandoLocalizacao) {
            return;
        }
        localizacaoCliente.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(local -> {
                    if (local != null) {
                        enviarLocalizacaoAtrasada(local);
                    } else if (procurandoLocalizacao) {
                        handler.postDelayed(tentarLocalizacaoDeNovo, INTERVALO_PROCURA_MS);
                    }
                })
                .addOnFailureListener(erro -> {
                    if (procurandoLocalizacao) {
                        handler.postDelayed(tentarLocalizacaoDeNovo, INTERVALO_PROCURA_MS);
                    }
                });
    }

    // Para de procurar (achou, desistiu ou a tela fechou)
    private void pararProcuraDeLocalizacao() {
        procurandoLocalizacao = false;
        handler.removeCallbacks(tentarLocalizacaoDeNovo);
        handler.removeCallbacks(desistirDaProcura);
    }

    // A localização chegou depois que o alerta saiu sem ela: manda um segundo SMS só com o mapa
    private void enviarLocalizacaoAtrasada(Location local) {
        if (local == null || localizacao != null || isDestroyed()) {
            return;
        }
        pararProcuraDeLocalizacao();
        localizacao = local;
        Log.i(TAG, "Localização chegou depois do alerta: " + local.getLatitude() + ", " + local.getLongitude());
        mostrarLocalizacao(R.string.alerta_localizacao_obtida, R.drawable.ic_check_circulo,
                ContextCompat.getColor(this, R.color.safe_green_claro));

        List<Contato> contatos = Prefs.carregarContatos(this);
        if (Prefs.isEnviarPorSms(this) && !contatos.isEmpty()) {
            String mensagem = getString(R.string.mensagem_localizacao_atrasada, nomeNaMensagem(),
                    Util.linkDoMapa(local.getLatitude(), local.getLongitude()));
            if (enviarSms(mensagem, contatos) > 0) {
                resultadoLocalizacaoAtrasada = getString(R.string.alerta_localizacao_enviada);
            }
        }
        // O botão do WhatsApp passa a mandar a mensagem já com o mapa
        mensagemWhatsapp = montarMensagem();
        // Atualiza o histórico com as coordenadas e o endereço
        registrarNoHistorico();
        mostrarResultado();
    }

    // Abre o WhatsApp na conversa do contato favorito. Com o celular bloqueado, pede antes o
    // desbloqueio (digital/senha): o WhatsApp não aparece por cima da tela de bloqueio, só o alerta.
    private void abrirWhatsapp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            KeyguardManager bloqueio = getSystemService(KeyguardManager.class);
            if (bloqueio != null && bloqueio.isKeyguardLocked()) {
                bloqueio.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                    // Chamado quando a pessoa desbloqueia (se ela desistir, o botão continua na tela)
                    @Override
                    public void onDismissSucceeded() {
                        abrirConversaNoWhatsapp();
                    }
                });
                return;
            }
        }
        abrirConversaNoWhatsapp();
    }

    // Usa o link oficial "clique para conversar" (wa.me/55 + DDD + número): o WhatsApp abre direto
    // na conversa, com a mensagem e o mapa já escritos. Falta só a pessoa tocar em enviar.
    private void abrirConversaNoWhatsapp() {
        // Uri.encode troca espaços e acentos por %20, %C3%A7...: o texto vai inteiro no link
        Uri link = Uri.parse("https://wa.me/55" + contatoWhatsapp.getTelefone()
                + "?text=" + Uri.encode(mensagemWhatsapp));
        Intent abrir = new Intent(Intent.ACTION_VIEW, link);
        // Tenta o WhatsApp normal e, se não tiver, o WhatsApp Business (sem isso abriria o navegador)
        for (String pacote : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
            abrir.setPackage(pacote);
            try {
                startActivity(abrir);
                Log.i(TAG, "WhatsApp aberto na conversa com " + contatoWhatsapp.getNome());
                // Aparece por cima do WhatsApp, lembrando o que falta fazer
                Toast.makeText(this, getString(R.string.alerta_whatsapp_toque_enviar,
                        contatoWhatsapp.getPrimeiroNome()), Toast.LENGTH_LONG).show();
                return;
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, pacote + " não está instalado");
            }
        }
        Snackbar.make(telaAlerta, R.string.alerta_whatsapp_falhou, Snackbar.LENGTH_SHORT)
                .setAnchorView(botaoEstouBem)
                .show();
    }

    // Mostra uma linha por canal e o título conforme deu certo ou não
    private void mostrarResultado() {
        StringBuilder linhas = new StringBuilder();
        for (String linha : new String[]{resultadoSms, resultadoLocalizacaoAtrasada, resultadoServidor,
                resultadoWhatsapp}) {
            if (linha != null) {
                if (linhas.length() > 0) {
                    linhas.append('\n');
                }
                linhas.append(linha);
            }
        }
        subtituloAlerta.setText(linhas);

        if (canaisOk > 0) {
            tituloAlerta.setText(R.string.alerta_enviado);
        } else if (servidorPendente) {
            tituloAlerta.setText(R.string.alerta_enviando);
        } else if (resultadoWhatsapp != null) {
            // Só o WhatsApp está ligado: depende do toque em enviar
            tituloAlerta.setText(R.string.alerta_whatsapp_titulo);
        } else {
            tituloAlerta.setText(R.string.alerta_falhou);
        }
    }

    // ---- Histórico ----

    // Grava o alerta no histórico já com as coordenadas e, em segundo plano, troca pelo endereço
    private void registrarNoHistorico() {
        if (localizacao == null) {
            enderecoEvento = getString(R.string.evento_sem_localizacao);
        } else {
            enderecoEvento = String.format(Locale.US, "%.5f, %.5f",
                    localizacao.getLatitude(), localizacao.getLongitude());
        }
        salvarEvento();
        if (localizacao == null || !Geocoder.isPresent()) {
            return;
        }

        // O Geocoder consulta a internet: não pode rodar na thread da tela
        Location local = localizacao;
        Context app = getApplicationContext();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            String endereco = buscarEndereco(app, local);
            if (endereco != null) {
                runOnUiThread(() -> {
                    enderecoEvento = endereco;
                    salvarEvento();
                });
            }
        });
        executor.shutdown();
    }

    // Transforma latitude/longitude em "Rua XV de Novembro, 1200 – Centro" (null se não achar)
    @SuppressWarnings("deprecation") // a versão com callback do Geocoder só existe no Android 13+
    private static String buscarEndereco(Context context, Location local) {
        try {
            Geocoder geocoder = new Geocoder(context, Locale.forLanguageTag("pt-BR"));
            List<Address> enderecos = geocoder.getFromLocation(local.getLatitude(), local.getLongitude(), 1);
            if (enderecos == null || enderecos.isEmpty()) {
                return null;
            }
            Address endereco = enderecos.get(0);
            if (endereco.getThoroughfare() == null) {
                // Sem nome de rua (estrada, parque...): usa o endereço completo
                return endereco.getAddressLine(0);
            }
            String texto = endereco.getThoroughfare();
            if (endereco.getSubThoroughfare() != null) {
                texto += ", " + endereco.getSubThoroughfare();
            }
            if (endereco.getSubLocality() != null) {
                texto += " – " + endereco.getSubLocality();
            } else if (endereco.getLocality() != null) {
                texto += " – " + endereco.getLocality();
            }
            return texto;
        } catch (Exception e) {
            Log.w(TAG, "Geocoder não achou o endereço", e);
            return null;
        }
    }

    // Grava (ou atualiza) este alerta no histórico. Se o MonitorService já registrou a queda,
    // atualiza o evento com a mesma hora em vez de criar outro.
    private void salvarEvento() {
        List<Evento> eventos = Prefs.carregarEventos(this);
        Evento evento = null;
        for (Evento existente : eventos) {
            if (existente.getQuando() == quando) {
                evento = existente;
            }
        }
        if (evento == null) {
            Evento.Tipo tipo = MODO_PANICO.equals(modo) ? Evento.Tipo.PANICO : Evento.Tipo.QUEDA;
            evento = new Evento(tipo, cancelado, quando, enderecoEvento, 0, 0);
            eventos.add(evento);
        }
        evento.setCancelado(cancelado);
        evento.setEndereco(enderecoEvento);
        if (localizacao != null) {
            evento.setLatitude(localizacao.getLatitude());
            evento.setLongitude(localizacao.getLongitude());
        }
        // Enquanto a API não respondeu, não grava o "Enviando..."
        evento.setResultadoServidor(servidorPendente ? null : resultadoServidor);
        Prefs.salvarEventos(this, eventos);
    }

    // ---- Vibração ----

    // Pega o vibrador do celular (a forma de pegar mudou no Android 12)
    @SuppressWarnings("deprecation") // VIBRATOR_SERVICE é o único jeito nas APIs 24–30
    private Vibrator pegarVibrador() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager gerenciador = getSystemService(VibratorManager.class);
            return gerenciador.getDefaultVibrator();
        }
        return (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    // Começa a vibrar sem parar. Usa a categoria "alarme" para vibrar mesmo com a
    // vibração de toque desligada nas configurações do celular.
    @SuppressWarnings("deprecation") // as versões antigas de vibrate() são as únicas nas APIs 24–32
    private void iniciarVibracao() {
        if (vibrador == null || !vibrador.hasVibrator()) {
            return;
        }
        // O 0 no fim quer dizer "repetir o padrão desde o início"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrador.vibrate(VibrationEffect.createWaveform(PADRAO_VIBRACAO, 0),
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM));
            return;
        }
        AudioAttributes alarme = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrador.vibrate(VibrationEffect.createWaveform(PADRAO_VIBRACAO, 0), alarme);
        } else {
            vibrador.vibrate(PADRAO_VIBRACAO, 0, alarme);
        }
    }

    // Para a vibração
    private void pararVibracao() {
        if (vibrador != null) {
            vibrador.cancel();
        }
    }
}
