package com.mindup.caiu_sos;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

// Adapter da lista de contatos: cria os cards do RecyclerView e preenche cada um
public class ContatoAdapter extends RecyclerView.Adapter<ContatoAdapter.ContatoViewHolder> {

    private final ContatosActivity tela;
    private final List<Contato> contatos;

    // Recebe a tela (para avisar dos toques) e a lista de contatos que vai aparecer
    public ContatoAdapter(ContatosActivity tela, List<Contato> contatos) {
        this.tela = tela;
        this.contatos = contatos;
    }

    // Cria um card novo a partir do layout item_contato
    @NonNull
    @Override
    public ContatoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View card = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contato, parent, false);
        return new ContatoViewHolder(card);
    }

    // Preenche o card da posição pedida com os dados do contato
    @Override
    public void onBindViewHolder(@NonNull ContatoViewHolder holder, int position) {
        Contato contato = contatos.get(position);
        holder.avatar.setText(contato.getIniciais());
        holder.nome.setText(contato.getNome());

        // O favorito (quem recebe também pelo WhatsApp) ganha a estrela e o "· WhatsApp" no telefone
        String telefone = Util.formatarTelefone(contato.getTelefone());
        boolean favorito = contato == Util.contatoDoWhatsapp(contatos);
        holder.nome.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, favorito ? R.drawable.ic_estrela : 0, 0);
        holder.telefone.setText(favorito ? tela.getString(R.string.contatos_telefone_whatsapp, telefone) : telefone);

        // Tocar no card abre o formulário para editar o contato
        holder.itemView.setOnClickListener(v -> tela.abrirFormulario(contato));
        // Segurar o card escolhe o contato para o WhatsApp
        holder.itemView.setOnLongClickListener(v -> {
            tela.perguntarFavorito(contato);
            return true;
        });

        // Quem usa leitor de tela não consegue deslizar nem segurar: ganha as ações "Excluir" e
        // "Escolher para o WhatsApp" no TalkBack. O card é reaproveitado, então primeiro tira as
        // ações do contato que estava nele antes.
        if (holder.idAcaoExcluir != View.NO_ID) {
            ViewCompat.removeAccessibilityAction(holder.itemView, holder.idAcaoExcluir);
            ViewCompat.removeAccessibilityAction(holder.itemView, holder.idAcaoFavorito);
        }
        holder.idAcaoExcluir = ViewCompat.addAccessibilityAction(holder.itemView,
                tela.getString(R.string.contatos_excluir), (view, argumentos) -> {
                    tela.excluirContato(contato);
                    return true;
                });
        holder.idAcaoFavorito = ViewCompat.addAccessibilityAction(holder.itemView,
                tela.getString(R.string.contatos_acao_favorito), (view, argumentos) -> {
                    tela.perguntarFavorito(contato);
                    return true;
                });
    }

    // Quantos itens a lista tem
    @Override
    public int getItemCount() {
        return contatos.size();
    }

    // Guarda as views de um card, para não precisar procurar de novo a cada rolagem
    static class ContatoViewHolder extends RecyclerView.ViewHolder {
        TextView avatar;
        TextView nome;
        TextView telefone;
        // Ids das ações "Excluir" e "Escolher para o WhatsApp" do TalkBack que estão neste card
        int idAcaoExcluir = View.NO_ID;
        int idAcaoFavorito = View.NO_ID;

        ContatoViewHolder(View card) {
            super(card);
            avatar = card.findViewById(R.id.avatar);
            nome = card.findViewById(R.id.nome);
            telefone = card.findViewById(R.id.telefone);
        }
    }
}
