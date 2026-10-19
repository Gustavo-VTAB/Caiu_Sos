package com.mindup.caiu_sos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.constraintlayout.widget.Group;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

// Tela inicial: status do monitoramento, botão SOS, clima e resumo dos contatos.
// Implementa OnTouchListener para saber quando o dedo encosta e sai do botão SOS,
// e SensorEventListener para o sensor de luz (modo noturno automático).
public class MainActivity extends AppCompatActivity implements View.OnTouchListener, SensorEventListener {

    // Etiqueta das mensagens no Logcat
    private static final String TAG = "CaiuSOS";
    // Tempo que o dedo precisa ficar no botão SOS para pedir ajuda
    private static final long TEMPO_SEGURAR_SOS_MS = 3000;
    // Vento acima disto (km/h) gera o aviso de vento forte
    private static final double VENTO_FORTE_KMH = 40;
    // Modo noturno automático: abaixo de 10 lux escurece o tema, acima de 50 lux clareia.
    // Entre os dois fica como está, para o tema não ficar piscando.
    private static final float LUX_NOITE = 10;
    private static final float LUX_DIA = 50;

    // Views da tela
    private View telaPrincipal;
    private MaterialSwitch switchMonitoramento;
    private ImageView iconeStatus;
    private TextView tituloStatus;
    private TextView subtituloStatus;
    private MaterialButton botaoSos;
    private CircularProgressIndicator progressoSos;
    private TextView textoSos;
    private TextView textoSosDica;
    private ImageView iconeClima;
    private TextView textoClimaCarregando;
    private TextView textoTemperatura;
    private TextView textoCidade;
    private ImageView iconeAviso;
    private TextView textoAviso;
    private Group grupoClima;
    private ImageView iconeContatos;
    private TextView tituloContatos;
    private TextView subtituloContatos;
    private MaterialButton botaoContatos;
    private BottomNavigationView navegacao;

    // Sensor de luz
    private SensorManager sensorManager;
    private Sensor sensorLuz;

    // Controle do "segurar para pedir ajuda"
    private boolean segurandoSos = false;
    private long inicioToqueSos;

    // Chamado quando a pessoa (e não o código) mexe no switch do monitoramento
    private final CompoundButton.OnCheckedChangeListener aoMudarSwitch =
            (botao, ligado) -> aoMudarMonitoramento(ligado);

    // Abre as janelas de permissão do Android e recebe a resposta
    private final ActivityResultLauncher<String[]> pedirPermissoes = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), resposta -> {
                if (temPermissoes()) {
                    iniciarMonitoramento();
                } else {
                    permissoesNegadas();
                }
            });

    // Roda a cada quadro da tela enquanto o dedo está no botão SOS e vai enchendo o anel
    private final Runnable atualizarSos = new Runnable() {
        @Override
        public void run() {
            if (!segurandoSos) {
                return;
            }
            // O tempo é medido pelo relógio, e não por animação: com "Remover animações"
            // ligado no Android, uma animação terminaria na hora sem a pessoa segurar
            long passou = SystemClock.uptimeMillis() - inicioToqueSos;
            float progresso = Math.min(1f, passou / (float) TEMPO_SEGURAR_SOS_MS);
            progressoSos.setProgress(Math.round(progresso * progressoSos.getMax()));

            if (progresso < 1f) {
                botaoSos.postOnAnimation(this);
            } else {
                // Completou os 3 segundos: vibra de leve e abre o alerta
                segurandoSos = false;
                botaoSos.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                confirmarSos();
            }
        }
    };

    // Chamado quando a tela é criada
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Liga as variáveis às views do layout
        telaPrincipal = findViewById(R.id.main);
        switchMonitoramento = findViewById(R.id.switchMonitoramento);
        iconeStatus = findViewById(R.id.iconeStatus);
        tituloStatus = findViewById(R.id.tituloStatus);
        subtituloStatus = findViewById(R.id.subtituloStatus);
        botaoSos = findViewById(R.id.botaoSos);
        progressoSos = findViewById(R.id.progressoSos);
        textoSos = findViewById(R.id.textoSos);
        textoSosDica = findViewById(R.id.textoSosDica);
        iconeClima = findViewById(R.id.iconeClima);
        textoClimaCarregando = findViewById(R.id.textoClimaCarregando);
        textoTemperatura = findViewById(R.id.textoTemperatura);
        textoCidade = findViewById(R.id.textoCidade);
        iconeAviso = findViewById(R.id.iconeAviso);
        textoAviso = findViewById(R.id.textoAviso);
        grupoClima = findViewById(R.id.grupoClima);
        iconeContatos = findViewById(R.id.iconeContatos);
        tituloContatos = findViewById(R.id.tituloContatos);
        subtituloContatos = findViewById(R.id.subtituloContatos);
        botaoContatos = findViewById(R.id.botaoContatos);
        navegacao = findViewById(R.id.navegacao);

        // Deixa espaço para a barra de status no topo (a navegação de baixo já cuida do espaço dela)
        ViewCompat.setOnApplyWindowInsetsListener(telaPrincipal, (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, 0);
            return insets;
        });

        // Sensor de luz (null se o aparelho não tiver)
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorLuz = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        configurarToolbar();
        configurarStatus();
        configurarBotaoSos();
        configurarContatos();
        Util.configurarNavegacao(this, navegacao, R.id.nav_inicio);
    }

    // Chamado sempre que a tela volta a aparecer
    @Override
    protected void onResume() {
        super.onResume();
        mostrarContatos(Prefs.carregarContatos(this).size());
        conferirMonitoramento();
        carregarClima();
        // O sensor de luz só fica ligado com a tela aberta e com a opção ligada nas Configurações
        if (Prefs.isModoNoturnoAutomatico(this) && sensorLuz != null) {
            sensorManager.registerListener(this, sensorLuz, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    // Chamado quando a tela sai de vista: desliga o sensor de luz (economiza bateria)
    @Override
    protected void onPause() {
        sensorManager.unregisterListener(this);
        super.onPause();
    }

    // A engrenagem da toolbar abre as Configurações
    private void configurarToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.acao_configuracoes) {
                startActivity(new Intent(this, ConfiguracoesActivity.class));
                return true;
            }
            return false;
        });
    }

    // ---- Status do monitoramento ----

    // Liga o switch e o card de status
    private void configurarStatus() {
        marcarSwitch(Prefs.isMonitoramentoAtivo(this));

        // O card inteiro liga/desliga: alvo de toque maior que o switch
        View cardStatus = findViewById(R.id.cardStatus);
        cardStatus.setOnClickListener(v -> switchMonitoramento.toggle());
    }

    // Muda o switch sem chamar o listener (quando quem muda é o código, não a pessoa)
    private void marcarSwitch(boolean ligado) {
        switchMonitoramento.setOnCheckedChangeListener(null);
        switchMonitoramento.setChecked(ligado);
        switchMonitoramento.setOnCheckedChangeListener(aoMudarSwitch);
        mostrarStatus(ligado);
    }

    // A pessoa ligou ou desligou o switch
    private void aoMudarMonitoramento(boolean ligado) {
        if (!ligado) {
            pararMonitoramento();
            return;
        }
        mostrarStatus(true);
        if (temPermissoes()) {
            iniciarMonitoramento();
        } else {
            explicarPermissoes();
        }
    }

    // Deixa o switch igual ao que está salvo. Se o monitoramento devia estar ligado, garante que o
    // serviço está rodando (ele pode ter sido parado pelo sistema).
    private void conferirMonitoramento() {
        boolean ligado = Prefs.isMonitoramentoAtivo(this);
        if (ligado && !temPermissoes()) {
            // A pessoa tirou alguma permissão nos ajustes do Android
            ligado = false;
            Prefs.setMonitoramentoAtivo(this, false);
        }
        if (ligado) {
            ContextCompat.startForegroundService(this, new Intent(this, MonitorService.class));
        }
        marcarSwitch(ligado);
    }

    // Salva, liga o MonitorService e confere se o alerta em tela cheia está liberado
    private void iniciarMonitoramento() {
        Prefs.setMonitoramentoAtivo(this, true);
        ContextCompat.startForegroundService(this, new Intent(this, MonitorService.class));
        marcarSwitch(true);
        avisarSeTelaCheiaBloqueada();
    }

    // Salva e para o MonitorService
    private void pararMonitoramento() {
        Prefs.setMonitoramentoAtivo(this, false);
        stopService(new Intent(this, MonitorService.class));
        mostrarStatus(false);
    }

    // Troca ícone, cores e textos do card conforme o monitoramento está ligado ou não
    private void mostrarStatus(boolean ligado) {
        int cor = ContextCompat.getColor(this, ligado ? R.color.safe_green : R.color.cancel_gray);
        int fundo = ContextCompat.getColor(this, ligado ? R.color.safe_green_container : R.color.neutral_container);

        iconeStatus.setImageResource(ligado ? R.drawable.ic_escudo_ok : R.drawable.ic_escudo);
        pintarIcone(iconeStatus, cor, fundo);
        // Pinta o pontinho ao lado do título com a mesma cor
        TextViewCompat.setCompoundDrawableTintList(tituloStatus, ColorStateList.valueOf(cor));
        tituloStatus.setText(ligado ? R.string.status_ativo : R.string.status_desligado);
        subtituloStatus.setText(ligado ? R.string.status_ativo_descricao : R.string.status_desligado_descricao);
    }

    // ---- Permissões ----

    // Permissões que o monitoramento precisa. Notificação só existe a partir do Android 13;
    // SMS só é pedido se o canal SMS estiver ligado nas Configurações.
    private String[] permissoesNecessarias() {
        List<String> lista = new ArrayList<>();
        // No Android 12+ a localização precisa ser pedida com as duas juntas
        lista.add(Manifest.permission.ACCESS_FINE_LOCATION);
        lista.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Prefs.isEnviarPorSms(this)) {
            lista.add(Manifest.permission.SEND_SMS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lista.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        return lista.toArray(new String[0]);
    }

    // Diz se já tem tudo o que o monitoramento precisa
    private boolean temPermissoes() {
        // Localização aproximada também serve (a pessoa pode escolher "aproximada" no Android 12+)
        boolean localizacao = temPermissao(Manifest.permission.ACCESS_FINE_LOCATION)
                || temPermissao(Manifest.permission.ACCESS_COARSE_LOCATION);
        boolean sms = !Prefs.isEnviarPorSms(this) || temPermissao(Manifest.permission.SEND_SMS);
        boolean notificacao = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || temPermissao(Manifest.permission.POST_NOTIFICATIONS);
        return localizacao && sms && notificacao;
    }

    // Diz se uma permissão já foi concedida
    private boolean temPermissao(String permissao) {
        return ContextCompat.checkSelfPermission(this, permissao) == PackageManager.PERMISSION_GRANTED;
    }

    // Antes de abrir as janelas do Android, explica para que serve cada permissão
    private void explicarPermissoes() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permissoes_titulo)
                .setMessage(R.string.permissoes_mensagem)
                .setPositiveButton(R.string.permissoes_continuar,
                        (dialogo, botao) -> pedirPermissoes.launch(permissoesNecessarias()))
                .setNegativeButton(R.string.permissoes_agora_nao, (dialogo, botao) -> marcarSwitch(false))
                .setOnCancelListener(dialogo -> marcarSwitch(false))
                .show();
    }

    // A pessoa negou alguma permissão: o switch volta para desligado e explica por quê
    private void permissoesNegadas() {
        marcarSwitch(false);
        Snackbar.make(telaPrincipal, R.string.permissoes_negadas, Snackbar.LENGTH_LONG)
                .setAnchorView(navegacao)
                // Quem marcou "não perguntar de novo" só consegue liberar pelos ajustes do Android
                .setAction(R.string.permissoes_ajustes, v -> startActivity(new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null))))
                .show();
    }

    // Android 14+: o alerta em tela cheia (por cima da tela de bloqueio) pode estar bloqueado
    private void avisarSeTelaCheiaBloqueada() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return;
        }
        NotificationManager notificacoes = getSystemService(NotificationManager.class);
        if (notificacoes.canUseFullScreenIntent()) {
            return;
        }
        Snackbar.make(telaPrincipal, R.string.permissoes_tela_cheia, Snackbar.LENGTH_LONG)
                .setAnchorView(navegacao)
                .setAction(R.string.permissoes_permitir, v -> startActivity(new Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.fromParts("package", getPackageName(), null))))
                .show();
    }

    // ---- Botão SOS ----

    // Prepara o botão SOS: segurar 3 s pede ajuda, toque rápido só mostra uma dica
    private void configurarBotaoSos() {
        botaoSos.setOnTouchListener(this);

        // Com o TalkBack não dá para cronometrar o dedo: o "tocar duas vezes e segurar"
        // do leitor de tela já confirma direto
        ViewCompat.replaceAccessibilityAction(botaoSos, AccessibilityActionCompat.ACTION_LONG_CLICK,
                getString(R.string.sos_acao_acessibilidade), (view, argumentos) -> {
                    confirmarSos();
                    return true;
                });

        // Só chega aqui quem tocou e soltou antes dos 3 s
        botaoSos.setOnClickListener(v ->
                Snackbar.make(telaPrincipal, R.string.sos_toque_curto, Snackbar.LENGTH_SHORT)
                        .setAnchorView(navegacao)
                        .show());
    }

    // Chamado toda vez que o dedo encosta, mexe ou sai do botão SOS
    @SuppressLint("ClickableViewAccessibility") // performClick é chamado ao soltar antes do tempo
    @Override
    public boolean onTouch(View v, MotionEvent evento) {
        switch (evento.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Dedo encostou: impede a rolagem da tela de "roubar" o toque e começa a contar
                v.getParent().requestDisallowInterceptTouchEvent(true);
                v.drawableHotspotChanged(evento.getX(), evento.getY());
                v.setPressed(true);
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                inicioToqueSos = SystemClock.uptimeMillis();
                segurandoSos = true;

                // Mostra o anel, encolhe um pouco o botão e troca os textos
                progressoSos.setProgress(0);
                progressoSos.setVisibility(View.VISIBLE);
                botaoSos.animate().scaleX(0.95f).scaleY(0.95f).setDuration(150).start();
                textoSos.setText(R.string.sos_segurando);
                textoSosDica.setText(R.string.sos_solte_cancelar);

                v.postOnAnimation(atualizarSos);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // Dedo saiu: se ainda não deu o tempo, cancela
                v.getParent().requestDisallowInterceptTouchEvent(false);
                v.setPressed(false);
                if (segurandoSos) {
                    segurandoSos = false;
                    v.removeCallbacks(atualizarSos);
                    resetarBotaoSos();
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

    // Segurou o tempo inteiro: volta o botão ao normal e abre o alerta em modo pânico
    private void confirmarSos() {
        resetarBotaoSos();
        Intent intent = new Intent(this, AlertaActivity.class);
        intent.putExtra(AlertaActivity.EXTRA_MODO, AlertaActivity.MODO_PANICO);
        startActivity(intent);
    }

    // Esconde o anel e volta o botão e os textos ao estado inicial
    private void resetarBotaoSos() {
        progressoSos.setVisibility(View.INVISIBLE);
        botaoSos.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
        textoSos.setText(R.string.sos_segure);
        textoSosDica.setText(R.string.sos_dica);
    }

    // ---- Clima ----

    // Pega a última localização conhecida e pede o clima à ClimaApi (que usa o cache de 10 min)
    @SuppressLint("MissingPermission") // conferida logo abaixo
    private void carregarClima() {
        ClimaApi.Callback mostrar = new ClimaApi.Callback() {
            @Override
            public void onSucesso(int temperatura, String cidade, String descricao, String condicao, double ventoKmh) {
                mostrarClima(temperatura, cidade, descricao, condicao, ventoKmh);
            }

            @Override
            public void onErro() {
                mostrarClimaIndisponivel();
            }
        };

        boolean temLocalizacao = temPermissao(Manifest.permission.ACCESS_FINE_LOCATION)
                || temPermissao(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (!temLocalizacao) {
            // Sem permissão ainda dá para mostrar o clima guardado, se for recente
            ClimaApi.buscar(this, null, mostrar);
            return;
        }
        FusedLocationProviderClient cliente = LocationServices.getFusedLocationProviderClient(this);
        cliente.getLastLocation()
                .addOnSuccessListener(this, local -> {
                    if (local != null) {
                        ClimaApi.buscar(this, local, mostrar);
                    } else {
                        // Logo depois de dar a permissão ainda não existe "última localização":
                        // pede uma leitura só (precisão de bairro já basta para o clima)
                        cliente.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                                .addOnSuccessListener(this, atual -> ClimaApi.buscar(this, atual, mostrar))
                                .addOnFailureListener(this, erro -> ClimaApi.buscar(this, null, mostrar));
                    }
                })
                .addOnFailureListener(this, erro -> ClimaApi.buscar(this, null, mostrar));
    }

    // Preenche o card: ícone, temperatura, cidade com a descrição e a linha de aviso
    private void mostrarClima(int temperatura, String cidade, String descricao, String condicao, double ventoKmh) {
        iconeClima.setImageResource(iconeDoClima(condicao));
        textoTemperatura.setText(getString(R.string.clima_temperatura, temperatura));
        textoCidade.setText(cidade.isEmpty() ? descricao : getString(R.string.clima_cidade_descricao, cidade, descricao));

        // Chuva ou vento forte: aviso em amber. Senão, uma linha verde tranquilizando.
        boolean chuva = "Rain".equals(condicao) || "Thunderstorm".equals(condicao) || "Drizzle".equals(condicao);
        if (chuva || ventoKmh > VENTO_FORTE_KMH) {
            textoAviso.setText(chuva ? R.string.clima_aviso_chuva : R.string.clima_aviso_vento);
            iconeAviso.setImageResource(R.drawable.ic_aviso);
            ImageViewCompat.setImageTintList(iconeAviso,
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.warning_amber)));
        } else {
            textoAviso.setText(R.string.clima_condicoes_boas);
            iconeAviso.setImageResource(R.drawable.ic_check_circulo);
            ImageViewCompat.setImageTintList(iconeAviso,
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.verde_texto)));
        }

        textoClimaCarregando.setVisibility(View.GONE);
        grupoClima.setVisibility(View.VISIBLE);
    }

    // Sem localização ou erro na API: o card mostra só "Clima indisponível"
    private void mostrarClimaIndisponivel() {
        iconeClima.setImageResource(R.drawable.ic_nuvem);
        textoClimaCarregando.setText(R.string.clima_indisponivel);
        textoClimaCarregando.setVisibility(View.VISIBLE);
        grupoClima.setVisibility(View.GONE);
    }

    // Ícone pelo weather[0].main do OpenWeatherMap: sol, chuva ou nuvem
    private int iconeDoClima(String condicao) {
        switch (condicao) {
            case "Clear":
                return R.drawable.ic_sol;
            case "Rain":
            case "Drizzle":
            case "Thunderstorm":
                return R.drawable.ic_chuva;
            default:
                return R.drawable.ic_nuvem;
        }
    }

    // ---- Modo noturno automático (sensor de luz) ----

    // Chamado toda vez que o sensor de luz envia uma nova leitura
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_LIGHT) {
            return;
        }
        // Luz do ambiente em lux (só tem values[0])
        float lux = event.values[0];
        if (lux < LUX_NOITE) {
            trocarTema(AppCompatDelegate.MODE_NIGHT_YES);
        } else if (lux > LUX_DIA) {
            trocarTema(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    // Obrigatório pela interface; a precisão do sensor não muda nada aqui
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // Troca o tema do app inteiro (o Android recria a tela com as cores novas)
    private void trocarTema(int modo) {
        if (AppCompatDelegate.getDefaultNightMode() != modo) {
            Log.d(TAG, "Sensor de luz: tema " + (modo == AppCompatDelegate.MODE_NIGHT_YES ? "escuro" : "claro"));
            AppCompatDelegate.setDefaultNightMode(modo);
        }
    }

    // ---- Contatos ----

    // O card de contatos e o botão dele abrem a tela de Contatos
    private void configurarContatos() {
        View.OnClickListener abrirContatos = v -> {
            Intent intent = new Intent(this, ContatosActivity.class);
            // Sem contatos, o botão vira "Adicionar" e já abre o formulário de novo contato
            intent.putExtra(ContatosActivity.EXTRA_ADICIONAR, Prefs.carregarContatos(this).isEmpty());
            startActivity(intent);
        };
        View cardContatos = findViewById(R.id.cardContatos);
        cardContatos.setOnClickListener(abrirContatos);
        botaoContatos.setOnClickListener(abrirContatos);
    }

    // Mostra quantos contatos existem, ou um aviso em amarelo se não houver nenhum
    private void mostrarContatos(int quantidade) {
        if (quantidade == 0) {
            // Sem contatos ninguém é avisado: ícone em amber para chamar atenção
            tituloContatos.setText(R.string.contatos_nenhum);
            subtituloContatos.setText(R.string.contatos_nenhum_descricao);
            botaoContatos.setText(R.string.contatos_adicionar);
            pintarIcone(iconeContatos,
                    ContextCompat.getColor(this, R.color.warning_amber),
                    ContextCompat.getColor(this, R.color.warning_amber_container));
        } else {
            tituloContatos.setText(getResources()
                    .getQuantityString(R.plurals.contatos_quantidade, quantidade, quantidade));
            subtituloContatos.setText(R.string.contatos_configurados);
            botaoContatos.setText(R.string.contatos_ver_lista);
            pintarIcone(iconeContatos,
                    MaterialColors.getColor(iconeContatos, com.google.android.material.R.attr.colorOnSurfaceVariant),
                    ContextCompat.getColor(this, R.color.neutral_container));
        }
    }

    // Pinta um ícone redondo: a cor do desenho e a cor do círculo de fundo
    private void pintarIcone(ImageView icone, int cor, int fundo) {
        ImageViewCompat.setImageTintList(icone, ColorStateList.valueOf(cor));
        ViewCompat.setBackgroundTintList(icone, ColorStateList.valueOf(fundo));
    }
}
