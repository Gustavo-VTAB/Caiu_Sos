package com.mindup.caiu_sos;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.TimeZone;

// Adapter da lista do histórico: cria os cards do RecyclerView e preenche cada um
public class EventoAdapter extends RecyclerView.Adapter<EventoAdapter.EventoViewHolder> {

    private final HistoricoActivity tela;
    private final List<Evento> eventos;

    // Recebe a tela (para avisar dos toques) e a lista de eventos que vai aparecer
    public EventoAdapter(HistoricoActivity tela, List<Evento> eventos) {
        this.tela = tela;
        this.eventos = eventos;
    }

    // Cria um card novo a partir do layout item_evento
    @NonNull
    @Override
    public EventoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View card = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_evento, parent, false);
        return new EventoViewHolder(card);
    }

    // Preenche o card da posição pedida com os dados do evento
    @Override
    public void onBindViewHolder(@NonNull EventoViewHolder holder, int position) {
        Evento evento = eventos.get(position);
        Context context = holder.itemView.getContext();

        holder.titulo.setText(Util.tituloDoEvento(evento));
        holder.quando.setText(textoQuando(context, evento.getQuando(), System.currentTimeMillis()));
        holder.endereco.setText(evento.getEndereco());

        // Escolhe o ícone e as cores do círculo pelo tipo do evento (icone 0 = escreve "SOS")
        int icone;
        int cor;
        int fundo;
        if (evento.isCancelado()) {
            icone = R.drawable.ic_cancelado;
            cor = R.color.cancel_gray;
            fundo = R.color.neutral_container;
        } else if (evento.getTipo() == Evento.Tipo.PANICO) {
            icone = 0;
            cor = R.color.vermelho_texto;
            fundo = R.color.sos_red_translucido;
        } else if (evento.getTipo() == Evento.Tipo.CHECKIN) {
            icone = R.drawable.ic_escudo_ok;
            cor = R.color.safe_green;
            fundo = R.color.safe_green_container;
        } else {
            icone = R.drawable.ic_emergencia;
            cor = R.color.vermelho_texto;
            fundo = R.color.sos_red_translucido;
        }

        // Pinta o círculo e mostra o ícone (ou o texto "SOS" no botão de pânico)
        int corIcone = ContextCompat.getColor(context, cor);
        ViewCompat.setBackgroundTintList(holder.fundoIcone,
                ColorStateList.valueOf(ContextCompat.getColor(context, fundo)));
        if (icone == 0) {
            holder.textoSos.setVisibility(View.VISIBLE);
            holder.icone.setVisibility(View.GONE);
            holder.textoSos.setTextColor(corIcone);
        } else {
            holder.textoSos.setVisibility(View.GONE);
            holder.icone.setVisibility(View.VISIBLE);
            holder.icone.setImageResource(icone);
            ImageViewCompat.setImageTintList(holder.icone, ColorStateList.valueOf(corIcone));
        }

        // Tocar no card abre os detalhes do evento
        holder.itemView.setOnClickListener(v -> tela.mostrarDetalhes(evento));
    }

    // Quantos itens a lista tem
    @Override
    public int getItemCount() {
        return eventos.size();
    }

    // Monta "Hoje, 14:32", "Ontem, 20:04" ou "18 de outubro, 08:30"
    private String textoQuando(Context context, long quando, long agora) {
        TimeZone fuso = TimeZone.getDefault();
        String hora = Util.hora(quando, fuso);
        int dias = Util.diasAtras(quando, agora, fuso);
        if (dias == 0) {
            return context.getString(R.string.historico_hoje, hora);
        }
        if (dias == 1) {
            return context.getString(R.string.historico_ontem, hora);
        }
        return context.getString(R.string.historico_data, Util.diaEMes(quando, agora, fuso), hora);
    }

    // Guarda as views de um card, para não precisar procurar de novo a cada rolagem
    static class EventoViewHolder extends RecyclerView.ViewHolder {
        View fundoIcone;
        ImageView icone;
        TextView textoSos;
        TextView titulo;
        TextView quando;
        TextView endereco;

        EventoViewHolder(View card) {
            super(card);
            fundoIcone = card.findViewById(R.id.fundoIcone);
            icone = card.findViewById(R.id.icone);
            textoSos = card.findViewById(R.id.textoSos);
            titulo = card.findViewById(R.id.titulo);
            quando = card.findViewById(R.id.quando);
            endereco = card.findViewById(R.id.endereco);
        }
    }
}
