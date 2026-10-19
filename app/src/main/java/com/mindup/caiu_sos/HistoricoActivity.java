package com.mindup.caiu_sos;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Group;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

// Tela de histórico: lista de quedas, pedidos de ajuda e check-ins, com filtros por tipo
public class HistoricoActivity extends AppCompatActivity {

    // O histórico mostra só os últimos 30 dias
    private static final int DIAS_NO_HISTORICO = 30;
    private static final long UM_DIA_MS = 24L * 60 * 60 * 1000;

    // Views da tela
    private RecyclerView lista;
    private ChipGroup filtros;
    private TextView textoRegistros;
    private Group grupoVazio;

    // Eventos que aparecem na tela (já filtrados)
    private final List<Evento> eventos = new ArrayList<>();
    private EventoAdapter adapter;
    // Diálogo de detalhes aberto no momento, para fechar se a tela for destruída
    private AlertDialog detalhes;

    // Chamado quando a tela é criada
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_historico);

        // Liga as variáveis às views do layout
        View telaHistorico = findViewById(R.id.main);
        lista = findViewById(R.id.lista);
        filtros = findViewById(R.id.filtros);
        textoRegistros = findViewById(R.id.textoRegistros);
        grupoVazio = findViewById(R.id.grupoVazio);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        BottomNavigationView navegacao = findViewById(R.id.navegacao);

        // Deixa espaço para a barra de status no topo (a navegação de baixo já cuida do espaço dela)
        ViewCompat.setOnApplyWindowInsetsListener(telaHistorico, (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, 0);
            return insets;
        });

        toolbar.setNavigationOnClickListener(v -> finish());
        adapter = new EventoAdapter(this, eventos);
        lista.setAdapter(adapter);
        // Trocar o chip de filtro recarrega a lista e volta para o topo
        filtros.setOnCheckedStateChangeListener((grupo, marcados) -> mostrarEventos(true));
        Util.configurarNavegacao(this, navegacao, R.id.nav_historico);
    }

    // Chamado sempre que a tela volta a aparecer: recalcula "Hoje"/"Ontem" e pega eventos novos
    @Override
    protected void onResume() {
        super.onResume();
        mostrarEventos(false);
    }

    // Chamado quando a tela é fechada: fecha o diálogo de detalhes, se estiver aberto
    @Override
    protected void onDestroy() {
        if (detalhes != null) {
            detalhes.dismiss();
        }
        super.onDestroy();
    }

    // Lê o histórico salvo, aplica o filtro e atualiza a lista, o contador e o estado vazio
    @SuppressLint("NotifyDataSetChanged") // a lista inteira pode mudar de uma vez
    private void mostrarEventos(boolean voltarAoTopo) {
        List<Evento> filtrados = filtrarEventos(Prefs.carregarEventos(this), tipoSelecionado(),
                System.currentTimeMillis());
        eventos.clear();
        eventos.addAll(filtrados);
        adapter.notifyDataSetChanged();

        // Ao trocar de filtro, volta para o evento mais recente
        if (voltarAoTopo) {
            lista.scrollToPosition(0);
        }
        textoRegistros.setText(getResources()
                .getQuantityString(R.plurals.historico_registros, eventos.size(), eventos.size()));
        grupoVazio.setVisibility(eventos.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // Tipo escolhido nos chips de filtro (null = Todos)
    private Evento.Tipo tipoSelecionado() {
        int chip = filtros.getCheckedChipId();
        if (chip == R.id.chipQuedas) {
            return Evento.Tipo.QUEDA;
        }
        if (chip == R.id.chipPanico) {
            return Evento.Tipo.PANICO;
        }
        if (chip == R.id.chipCheckins) {
            return Evento.Tipo.CHECKIN;
        }
        return null;
    }

    // Guarda só os eventos dos últimos 30 dias do tipo escolhido, do mais novo para o mais antigo.
    // Uma queda cancelada continua sendo uma queda, então aparece no filtro "Quedas".
    // O filtro "Check-ins" mostra também os check-ins sem resposta.
    private List<Evento> filtrarEventos(List<Evento> todos, Evento.Tipo tipo, long agora) {
        long limite = agora - DIAS_NO_HISTORICO * UM_DIA_MS;
        List<Evento> resultado = new ArrayList<>();
        for (Evento evento : todos) {
            boolean recente = evento.getQuando() >= limite;
            boolean doTipo = tipo == null || evento.getTipo() == tipo
                    || (tipo == Evento.Tipo.CHECKIN && evento.getTipo() == Evento.Tipo.CHECKIN_SEM_RESPOSTA);
            if (recente && doTipo) {
                resultado.add(evento);
            }
        }
        // Ordena pela data, do maior (mais novo) para o menor
        Collections.sort(resultado, (a, b) -> Long.compare(b.getQuando(), a.getQuando()));
        return resultado;
    }

    // ---- Detalhes do evento ----

    // Abre o diálogo com data completa, endereço e o botão "Ver no mapa".
    // Chamado pelo EventoAdapter ao tocar num card.
    @SuppressLint("InflateParams") // o conteúdo de um diálogo não tem um "pai" para passar ao inflate
    void mostrarDetalhes(Evento evento) {
        TimeZone fuso = TimeZone.getDefault();
        View conteudo = getLayoutInflater().inflate(R.layout.dialogo_evento, null);
        TextView quando = conteudo.findViewById(R.id.quando);
        TextView endereco = conteudo.findViewById(R.id.endereco);
        TextView situacao = conteudo.findViewById(R.id.situacao);

        quando.setText(getString(R.string.historico_data_completa,
                Util.dataCompleta(evento.getQuando(), fuso), Util.hora(evento.getQuando(), fuso)));
        endereco.setText(evento.getEndereco());
        // Embaixo da situação, o que o servidor respondeu (se o alerta foi para a API)
        String textoSituacao = getString(situacaoDoEvento(evento));
        if (evento.getResultadoServidor() != null) {
            textoSituacao += "\n" + evento.getResultadoServidor();
        }
        situacao.setText(textoSituacao);

        MaterialAlertDialogBuilder dialogo = new MaterialAlertDialogBuilder(this)
                .setTitle(Util.tituloDoEvento(evento))
                .setView(conteudo)
                .setNegativeButton(R.string.evento_fechar, null);
        // "Ver no mapa" só aparece se a localização foi obtida
        if (evento.temLocalizacao()) {
            dialogo.setPositiveButton(R.string.evento_ver_no_mapa, (d, botao) -> abrirMapa(evento));
        }
        detalhes = dialogo.show();
    }

    // Frase que explica o que aconteceu com o alerta (enviado, cancelado ou check-in)
    private int situacaoDoEvento(Evento evento) {
        if (evento.isCancelado()) {
            return R.string.evento_situacao_cancelado;
        }
        if (evento.getTipo() == Evento.Tipo.CHECKIN) {
            return R.string.evento_situacao_checkin;
        }
        return R.string.evento_situacao_enviado;
    }

    // Abre o local no Google Maps: no app, se estiver instalado, senão no navegador
    private void abrirMapa(Evento evento) {
        // Locale.US: em pt-BR o número sairia com vírgula ("-25,4296") e o link quebraria
        String endereco = String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f",
                evento.getLatitude(), evento.getLongitude());
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(endereco)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.evento_mapa_indisponivel, Toast.LENGTH_SHORT).show();
        }
    }
}
