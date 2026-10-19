package com.mindup.caiu_sos;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

// Tela de Configurações: cada mudança é salva na hora no Prefs
public class ConfiguracoesActivity extends AppCompatActivity {

    // Transparência da linha "Intervalo" quando o check-in está desligado
    private static final float OPACIDADE_DESATIVADO = 0.38f;

    // Views da tela
    private View telaConfiguracoes;
    private TextInputEditText inputNome;
    private Slider sliderSensibilidade;
    private TextView valorSensibilidade;
    private Slider sliderTempo;
    private TextView valorTempo;
    private TextView tempoMinimo;
    private TextView tempoMaximo;
    private MaterialSwitch switchCheckin;
    private View linhaIntervalo;
    private Slider sliderIntervalo;
    private TextView valorIntervalo;
    private TextView intervaloMinimo;
    private TextView intervaloMaximo;
    private MaterialSwitch switchSms;
    private MaterialSwitch switchWhatsapp;
    private MaterialSwitch switchServidor;
    private MaterialSwitch switchNoturno;

    // Chamado quando a tela é criada
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_configuracoes);

        // Liga as variáveis às views do layout
        telaConfiguracoes = findViewById(R.id.main);
        inputNome = findViewById(R.id.inputNomeUsuario);
        sliderSensibilidade = findViewById(R.id.sliderSensibilidade);
        valorSensibilidade = findViewById(R.id.valorSensibilidade);
        sliderTempo = findViewById(R.id.sliderTempo);
        valorTempo = findViewById(R.id.valorTempo);
        tempoMinimo = findViewById(R.id.tempoMinimo);
        tempoMaximo = findViewById(R.id.tempoMaximo);
        switchCheckin = findViewById(R.id.switchCheckin);
        linhaIntervalo = findViewById(R.id.linhaIntervalo);
        sliderIntervalo = findViewById(R.id.sliderIntervalo);
        valorIntervalo = findViewById(R.id.valorIntervalo);
        intervaloMinimo = findViewById(R.id.intervaloMinimo);
        intervaloMaximo = findViewById(R.id.intervaloMaximo);
        switchSms = findViewById(R.id.switchSms);
        switchWhatsapp = findViewById(R.id.switchWhatsapp);
        switchServidor = findViewById(R.id.switchServidor);
        switchNoturno = findViewById(R.id.switchNoturno);
        NestedScrollView rolagem = findViewById(R.id.rolagem);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        MaterialButton botaoTestar = findViewById(R.id.botaoTestar);

        // O conteúdo rola por trás da barra de gestos, mas o último item consegue subir acima dela
        ViewCompat.setOnApplyWindowInsetsListener(telaConfiguracoes, (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, 0);
            rolagem.setPadding(0, 0, 0, barras.bottom);
            return insets;
        });

        toolbar.setNavigationOnClickListener(v -> finish());

        // Marca o cabeçalho de cada seção como título, para o TalkBack poder pular entre elas
        int[] secoes = {R.id.secaoDeteccao, R.id.secaoCheckin, R.id.secaoCanais, R.id.secaoAparencia};
        for (int id : secoes) {
            ViewCompat.setAccessibilityHeading(findViewById(id), true);
        }

        inputNome.setText(Prefs.getNome(this));
        configurarDeteccao();
        configurarCheckin();
        configurarCanais();
        configurarAparencia();

        // "Testar alerta" abre a tela de alerta em modo teste (não envia nada)
        botaoTestar.setOnClickListener(v -> {
            Intent intent = new Intent(this, AlertaActivity.class);
            intent.putExtra(AlertaActivity.EXTRA_MODO, AlertaActivity.MODO_TESTE);
            startActivity(intent);
        });
    }

    // Chamado toda vez que a tela aparece: o favorito do WhatsApp pode ter mudado em Contatos
    @Override
    protected void onResume() {
        super.onResume();
        mostrarContatoDoWhatsapp();
    }

    // Chamado quando a tela sai de vista: salva o nome digitado
    @Override
    protected void onPause() {
        super.onPause();
        String nome = inputNome.getText() == null ? "" : inputNome.getText().toString().trim();
        Prefs.setNome(this, nome);
    }

    // ---- Detecção ----

    // Liga os sliders de sensibilidade e de tempo para cancelar
    private void configurarDeteccao() {
        // Sensibilidade: o slider vai de 0 a 2 (posição de cada valor no enum)
        // O texto do formatador também é o que o TalkBack fala ao mexer no slider
        sliderSensibilidade.setLabelFormatter(valor -> getString(nome(Sensibilidade.values()[(int) valor])));
        sliderSensibilidade.setValue(Prefs.getSensibilidade(this).ordinal());
        valorSensibilidade.setText(nome(Prefs.getSensibilidade(this)));
        // Chamado sempre que o slider muda de posição
        sliderSensibilidade.addOnChangeListener((slider, valor, doUsuario) -> {
            Sensibilidade escolhida = Sensibilidade.values()[(int) valor];
            valorSensibilidade.setText(nome(escolhida));
            Prefs.setSensibilidade(this, escolhida);
        });

        // Tempo para cancelar: de 10 a 60 segundos
        sliderTempo.setLabelFormatter(valor -> getString(R.string.config_tempo_valor, (int) valor));
        sliderTempo.setValue(noPasso(sliderTempo, Prefs.getTempoParaCancelar(this)));
        valorTempo.setText(getString(R.string.config_tempo_valor, (int) sliderTempo.getValue()));
        tempoMinimo.setText(getString(R.string.config_segundos_curto, (int) sliderTempo.getValueFrom()));
        tempoMaximo.setText(getString(R.string.config_segundos_curto, (int) sliderTempo.getValueTo()));
        sliderTempo.addOnChangeListener((slider, valor, doUsuario) -> {
            valorTempo.setText(getString(R.string.config_tempo_valor, (int) valor));
            Prefs.setTempoParaCancelar(this, (int) valor);
        });
    }

    // Texto de cada sensibilidade: Baixa, Média ou Alta
    private int nome(Sensibilidade sensibilidade) {
        switch (sensibilidade) {
            case BAIXA:
                return R.string.config_sensibilidade_baixa;
            case ALTA:
                return R.string.config_sensibilidade_alta;
            default:
                return R.string.config_sensibilidade_media;
        }
    }

    // ---- Check-in de segurança ----

    // Liga o switch do check-in e o slider de intervalo
    private void configurarCheckin() {
        switchCheckin.setText(tituloComDescricao(R.string.config_checkin, R.string.config_checkin_descricao));
        switchCheckin.setChecked(Prefs.isCheckinAtivo(this));
        mostrarIntervaloAtivo(Prefs.isCheckinAtivo(this));
        // Etapa 5: ligar/desligar aqui agenda/cancela o CheckInWorker
        switchCheckin.setOnCheckedChangeListener((botao, ligado) -> {
            Prefs.setCheckinAtivo(this, ligado);
            mostrarIntervaloAtivo(ligado);
        });

        // Intervalo: de 15 a 120 minutos
        sliderIntervalo.setLabelFormatter(valor -> getString(R.string.config_intervalo_valor, (int) valor));
        sliderIntervalo.setValue(noPasso(sliderIntervalo, Prefs.getIntervaloCheckin(this)));
        valorIntervalo.setText(getString(R.string.config_intervalo_valor, (int) sliderIntervalo.getValue()));
        intervaloMinimo.setText(getString(R.string.config_minutos_curto, (int) sliderIntervalo.getValueFrom()));
        intervaloMaximo.setText(getString(R.string.config_minutos_curto, (int) sliderIntervalo.getValueTo()));
        sliderIntervalo.addOnChangeListener((slider, valor, doUsuario) -> {
            valorIntervalo.setText(getString(R.string.config_intervalo_valor, (int) valor));
            Prefs.setIntervaloCheckin(this, (int) valor);
        });
    }

    // O intervalo só faz sentido com o check-in ligado: desligado, fica apagado e travado
    private void mostrarIntervaloAtivo(boolean ativo) {
        linhaIntervalo.setAlpha(ativo ? 1f : OPACIDADE_DESATIVADO);
        sliderIntervalo.setEnabled(ativo);
    }

    // ---- Canais de alerta ----

    // Liga os switches de SMS, WhatsApp e servidor
    private void configurarCanais() {
        switchSms.setChecked(Prefs.isEnviarPorSms(this));
        switchWhatsapp.setChecked(Prefs.isEnviarPorWhatsapp(this));
        switchServidor.setChecked(Prefs.isEnviarParaServidor(this));

        // Um só listener para os três switches
        CompoundButton.OnCheckedChangeListener aoMudar = (botao, ligado) -> {
            if (!ligado && !switchSms.isChecked() && !switchWhatsapp.isChecked() && !switchServidor.isChecked()) {
                // Sem nenhum canal, ninguém seria avisado de uma queda: liga de volta
                botao.setChecked(true);
                Snackbar.make(telaConfiguracoes, R.string.config_canal_obrigatorio, Snackbar.LENGTH_SHORT).show();
                return;
            }
            Prefs.setEnviarPorSms(this, switchSms.isChecked());
            Prefs.setEnviarPorWhatsapp(this, switchWhatsapp.isChecked());
            Prefs.setEnviarParaServidor(this, switchServidor.isChecked());
        };
        switchSms.setOnCheckedChangeListener(aoMudar);
        switchWhatsapp.setOnCheckedChangeListener(aoMudar);
        switchServidor.setOnCheckedChangeListener(aoMudar);

        // Quem liga o WhatsApp vê como ele funciona (o clique só acontece com o dedo, não pelo código)
        switchWhatsapp.setOnClickListener(v -> {
            if (switchWhatsapp.isChecked()) {
                explicarWhatsapp();
            }
        });
    }

    // Mostra embaixo de "Enviar por WhatsApp" para quem vai e o que a pessoa precisa fazer
    private void mostrarContatoDoWhatsapp() {
        Contato contato = Util.contatoDoWhatsapp(Prefs.carregarContatos(this));
        String descricao = contato == null
                ? getString(R.string.config_whatsapp_sem_contato)
                : getString(R.string.config_whatsapp_para, contato.getPrimeiroNome());
        switchWhatsapp.setText(tituloComDescricao(getString(R.string.config_whatsapp), descricao));
    }

    // Explica por que o WhatsApp precisa de um toque e deixa trocar o contato favorito
    private void explicarWhatsapp() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.whatsapp_como_funciona_titulo)
                .setMessage(R.string.whatsapp_como_funciona)
                .setPositiveButton(R.string.favorito_entendi, null)
                .setNeutralButton(R.string.contatos_acao_favorito,
                        (dialogo, botao) -> startActivity(new Intent(this, ContatosActivity.class)))
                .show();
    }

    // ---- Aparência ----

    // Liga o switch do modo noturno automático
    private void configurarAparencia() {
        switchNoturno.setText(tituloComDescricao(R.string.config_noturno, R.string.config_noturno_descricao));
        switchNoturno.setChecked(Prefs.isModoNoturnoAutomatico(this));
        // Ligado: o sensor de luz da Home troca o tema. Desligado: volta a seguir o tema do Android.
        switchNoturno.setOnCheckedChangeListener((botao, ligado) -> {
            Prefs.setModoNoturnoAutomatico(this, ligado);
            if (!ligado) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            }
        });
    }

    // ---- Auxiliares ----

    // Monta o texto do switch: título e, na linha de baixo, uma descrição menor e cinza
    private CharSequence tituloComDescricao(int titulo, int descricao) {
        return tituloComDescricao(getString(titulo), getString(descricao));
    }

    // O mesmo, com os textos já prontos (a descrição do WhatsApp leva o nome do contato)
    private CharSequence tituloComDescricao(String titulo, String descricao) {
        SpannableStringBuilder texto = new SpannableStringBuilder(titulo).append('\n');
        int inicio = texto.length();
        texto.append(descricao);
        int fim = texto.length();
        // Só a descrição (do início ao fim dela) fica com 14sp e cinza
        int cinza = MaterialColors.getColor(telaConfiguracoes, com.google.android.material.R.attr.colorOnSurfaceVariant);
        texto.setSpan(new AbsoluteSizeSpan(14, true), inicio, fim, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        texto.setSpan(new ForegroundColorSpan(cinza), inicio, fim, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        texto.setSpan(new TypefaceSpan("sans-serif"), inicio, fim, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return texto;
    }

    // Encaixa um valor salvo na escala do slider (dentro dos limites e no passo certo).
    // O Slider dá erro com valor fora da escala, e a escala pode mudar entre versões do app.
    private float noPasso(Slider slider, float valor) {
        float passo = slider.getStepSize();
        float noPasso = slider.getValueFrom() + Math.round((valor - slider.getValueFrom()) / passo) * passo;
        return Math.max(slider.getValueFrom(), Math.min(slider.getValueTo(), noPasso));
    }
}
