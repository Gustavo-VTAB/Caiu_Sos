package com.mindup.caiu_sos;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.Group;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

// Tela de contatos de emergência: lista, cadastro/edição, exclusão por deslize e ligação para o SAMU
public class ContatosActivity extends AppCompatActivity {

    // Extra do Intent: true faz a tela já abrir o formulário de novo contato
    // (vem do "Adicionar" da Home quando não há contatos)
    public static final String EXTRA_ADICIONAR = "com.mindup.caiu_sos.ADICIONAR";

    private static final String NUMERO_SAMU = "192";
    // Mais que o LENGTH_LONG (2,75 s): dá tempo de ler e achar o "Desfazer" com calma
    private static final int DURACAO_DESFAZER_MS = 6000;

    // Views da tela
    private View telaContatos;
    private RecyclerView lista;
    private Group grupoVazio;
    private FloatingActionButton fabAdicionar;
    private MaterialButton botaoSamu;

    // A lista que aparece na tela (a mesma que é salva no Prefs)
    private List<Contato> contatos;
    private ContatoAdapter adapter;
    // Formulário aberto no momento, para fechar se a tela for destruída
    private BottomSheetDialog formulario;

    // Chamado quando a tela é criada
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_contatos);

        // Liga as variáveis às views do layout
        telaContatos = findViewById(R.id.main);
        lista = findViewById(R.id.lista);
        grupoVazio = findViewById(R.id.grupoVazio);
        fabAdicionar = findViewById(R.id.fabAdicionar);
        botaoSamu = findViewById(R.id.botaoSamu);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        MaterialButton botaoAdicionarVazio = findViewById(R.id.botaoAdicionarVazio);
        BottomNavigationView navegacao = findViewById(R.id.navegacao);

        // Deixa espaço para a barra de status no topo (a navegação de baixo já cuida do espaço dela)
        ViewCompat.setOnApplyWindowInsetsListener(telaContatos, (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, 0);
            return insets;
        });

        // Carrega os contatos salvos e monta a lista
        contatos = Prefs.carregarContatos(this);
        adapter = new ContatoAdapter(this, contatos);
        lista.setAdapter(adapter);
        configurarDeslizarParaExcluir();

        // Botões da tela
        toolbar.setNavigationOnClickListener(v -> finish());
        fabAdicionar.setOnClickListener(v -> abrirFormulario(null));
        botaoAdicionarVazio.setOnClickListener(v -> abrirFormulario(null));
        botaoSamu.setOnClickListener(v -> ligarParaSamu());
        Util.configurarNavegacao(this, navegacao, R.id.nav_contatos);

        mostrarEstadoVazio();
        // Veio do "Adicionar" da Home: já abre o formulário (só na primeira vez, não ao girar a tela)
        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_ADICIONAR, false)) {
            abrirFormulario(null);
        }
    }

    // Chamado quando a tela é fechada: fecha o formulário, se estiver aberto
    @Override
    protected void onDestroy() {
        if (formulario != null) {
            formulario.dismiss();
        }
        super.onDestroy();
    }

    // Com a lista vazia mostra a mensagem do meio da tela. Nesse caso o botão
    // "Adicionar contato" da mensagem já faz o papel do FAB, que some.
    private void mostrarEstadoVazio() {
        boolean vazio = contatos.isEmpty();
        grupoVazio.setVisibility(vazio ? View.VISIBLE : View.GONE);
        if (vazio) {
            fabAdicionar.hide();
        } else {
            fabAdicionar.show();
        }
    }

    // ---- Excluir ----

    // Deslizar um card para qualquer lado exclui o contato. Enquanto o card desliza,
    // aparece por trás um fundo vermelho com a lixeira, para deixar claro o que vai acontecer.
    private void configurarDeslizarParaExcluir() {
        Paint fundo = new Paint(Paint.ANTI_ALIAS_FLAG);
        fundo.setColor(ContextCompat.getColor(this, R.color.sos_red));
        Drawable lixeira = AppCompatResources.getDrawable(this, R.drawable.ic_lixeira).mutate();
        lixeira.setTint(ContextCompat.getColor(this, R.color.white));
        float raio = getResources().getDimension(R.dimen.raio_card);
        int margemIcone = getResources().getDimensionPixelSize(R.dimen.margem_tela) * 3 / 2;
        RectF area = new RectF();

        ItemTouchHelper.SimpleCallback deslizar = new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.START | ItemTouchHelper.END) {

            // Arrastar para mudar a ordem não é usado
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder origem,
                                  @NonNull RecyclerView.ViewHolder destino) {
                return false;
            }

            // Chamado quando o card termina de sair da tela
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direcao) {
                int posicao = holder.getBindingAdapterPosition();
                if (posicao != RecyclerView.NO_POSITION) {
                    excluirContato(contatos.get(posicao));
                }
            }

            // Desenha o fundo vermelho e a lixeira atrás do card enquanto ele desliza
            @Override
            public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder holder,
                                    float dX, float dY, int estado, boolean ativo) {
                if (dX != 0) {
                    View card = holder.itemView;
                    area.set(card.getLeft(), card.getTop(), card.getRight(), card.getBottom());
                    canvas.drawRoundRect(area, raio, raio, fundo);

                    // A lixeira fica do lado de onde o card está saindo
                    int tamanho = lixeira.getIntrinsicWidth();
                    int topo = card.getTop() + (card.getHeight() - tamanho) / 2;
                    int esquerda = dX > 0 ? card.getLeft() + margemIcone : card.getRight() - margemIcone - tamanho;
                    lixeira.setBounds(esquerda, topo, esquerda + tamanho, topo + tamanho);
                    lixeira.draw(canvas);
                }
                super.onChildDraw(canvas, rv, holder, dX, dY, estado, ativo);
            }
        };
        new ItemTouchHelper(deslizar).attachToRecyclerView(lista);
    }

    // Tira o contato da lista, salva e mostra o "Desfazer" por alguns segundos.
    // Chamado pelo deslize e pela ação "Excluir" do TalkBack (no ContatoAdapter).
    void excluirContato(Contato contato) {
        int posicao = contatos.indexOf(contato);
        if (posicao < 0) {
            return;
        }
        contatos.remove(posicao);
        adapter.notifyItemRemoved(posicao);
        atualizarEstrela();
        Prefs.salvarContatos(this, contatos);
        mostrarEstadoVazio();

        // O aviso aparece acima do FAB (ou do botão do SAMU, se a lista ficou vazia)
        Snackbar.make(telaContatos, R.string.contatos_removido, DURACAO_DESFAZER_MS)
                .setAnchorView(contatos.isEmpty() ? botaoSamu : fabAdicionar)
                .setAction(R.string.contatos_desfazer, v -> {
                    // Desfazer: devolve o contato no mesmo lugar
                    int volta = Math.min(posicao, contatos.size());
                    contatos.add(volta, contato);
                    adapter.notifyItemInserted(volta);
                    atualizarEstrela();
                    Prefs.salvarContatos(this, contatos);
                    mostrarEstadoVazio();
                })
                .show();
    }

    // ---- Favorito do WhatsApp ----

    // Explica como funciona o WhatsApp e pergunta se este contato passa a receber por ele.
    // Chamado ao segurar um card (e pela ação do TalkBack, no ContatoAdapter).
    void perguntarFavorito(Contato contato) {
        MaterialAlertDialogBuilder dialogo = new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.whatsapp_como_funciona);
        if (contato == Util.contatoDoWhatsapp(contatos)) {
            // Já é o favorito: só explica
            dialogo.setTitle(getString(R.string.favorito_ja_escolhido, contato.getPrimeiroNome()))
                    .setPositiveButton(R.string.favorito_entendi, null);
        } else {
            dialogo.setTitle(getString(R.string.favorito_titulo, contato.getPrimeiroNome()))
                    .setPositiveButton(R.string.favorito_escolher, (d, botao) -> escolherFavorito(contato))
                    .setNegativeButton(R.string.contato_cancelar, null);
        }
        dialogo.show();
    }

    // Marca o contato como favorito (só um na lista) e salva
    private void escolherFavorito(Contato escolhido) {
        for (Contato contato : contatos) {
            contato.setFavorito(contato == escolhido);
        }
        atualizarEstrela();
        Prefs.salvarContatos(this, contatos);
        Snackbar.make(telaContatos, getString(R.string.favorito_escolhido, escolhido.getPrimeiroNome()),
                        Snackbar.LENGTH_SHORT)
                .setAnchorView(fabAdicionar)
                .show();
    }

    // A estrela pode ter mudado de card (outro favorito, o favorito foi excluído...): redesenha todos
    private void atualizarEstrela() {
        adapter.notifyItemRangeChanged(0, contatos.size());
    }

    // ---- Formulário de contato ----

    // Abre o formulário (bottom sheet) para cadastrar um contato novo (existente = null)
    // ou editar um contato da lista. Também é chamado pelo ContatoAdapter ao tocar num card.
    @SuppressLint("InflateParams") // o conteúdo de um diálogo não tem um "pai" para passar ao inflate
    void abrirFormulario(Contato existente) {
        BottomSheetDialog dialogo = new BottomSheetDialog(this);
        View conteudo = getLayoutInflater().inflate(R.layout.sheet_contato, null);
        dialogo.setContentView(conteudo);
        // Abre já inteiro, sem parar na metade
        dialogo.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialogo.getBehavior().setSkipCollapsed(true);
        formulario = dialogo;

        // Views do formulário
        TextView titulo = conteudo.findViewById(R.id.titulo);
        TextInputLayout campoNome = conteudo.findViewById(R.id.campoNome);
        TextInputLayout campoTelefone = conteudo.findViewById(R.id.campoTelefone);
        TextInputEditText inputNome = conteudo.findViewById(R.id.inputNome);
        TextInputEditText inputTelefone = conteudo.findViewById(R.id.inputTelefone);
        MaterialButton botaoCancelar = conteudo.findViewById(R.id.botaoCancelar);
        MaterialButton botaoSalvar = conteudo.findViewById(R.id.botaoSalvar);

        // Preenche o título e, na edição, os campos com os dados atuais
        titulo.setText(existente == null ? R.string.contato_novo : R.string.contato_editar);
        aplicarMascaraTelefone(inputTelefone);
        if (existente != null) {
            inputNome.setText(existente.getNome());
            inputTelefone.setText(existente.getTelefone());
        }
        limparErroAoDigitar(inputNome, campoNome);
        limparErroAoDigitar(inputTelefone, campoTelefone);

        botaoCancelar.setOnClickListener(v -> dialogo.dismiss());

        // Salvar: confere os campos e, se estiver tudo certo, grava e fecha
        botaoSalvar.setOnClickListener(v -> {
            String nome = texto(inputNome).trim().replaceAll("\\s+", " ");
            String telefone = Util.normalizarTelefone(texto(inputTelefone));

            String erroNome = null;
            if (nome.isEmpty()) {
                erroNome = getString(R.string.contato_erro_nome);
            }
            String erroTelefone = null;
            if (!Util.ehCelularValido(telefone)) {
                erroTelefone = getString(R.string.contato_erro_telefone);
            } else if (telefoneEmUso(telefone, existente)) {
                erroTelefone = getString(R.string.contato_erro_duplicado);
            }
            campoNome.setError(erroNome);
            campoTelefone.setError(erroTelefone);

            // Leva o foco (e o TalkBack) ao primeiro campo errado
            if (erroNome != null) {
                inputNome.requestFocus();
                return;
            }
            if (erroTelefone != null) {
                inputTelefone.requestFocus();
                return;
            }
            salvarContato(existente, nome, telefone);
            dialogo.dismiss();
        });

        // O botão "OK" do teclado no campo de telefone funciona como Salvar
        inputTelefone.setOnEditorActionListener((campo, acao, evento) -> {
            if (acao == EditorInfo.IME_ACTION_DONE) {
                botaoSalvar.performClick();
                return true;
            }
            return false;
        });

        // Contato novo: o formulário já abre com o teclado no campo de nome
        if (existente == null) {
            dialogo.setOnShowListener(d -> {
                inputNome.requestFocus();
                if (dialogo.getWindow() != null) {
                    WindowCompat.getInsetsController(dialogo.getWindow(), inputNome).show(WindowInsetsCompat.Type.ime());
                }
            });
        }
        dialogo.show();
    }

    // Grava o contato: adiciona no fim da lista (novo) ou troca os dados (edição)
    private void salvarContato(Contato existente, String nome, String telefone) {
        if (existente == null) {
            contatos.add(new Contato(nome, telefone));
            adapter.notifyItemInserted(contatos.size() - 1);
        } else {
            existente.setNome(nome);
            existente.setTelefone(telefone);
            adapter.notifyItemChanged(contatos.indexOf(existente));
        }
        Prefs.salvarContatos(this, contatos);
        mostrarEstadoVazio();
    }

    // Diz se outro contato já usa este celular (o contato sendo editado pode manter o dele)
    private boolean telefoneEmUso(String telefone, Contato ignorado) {
        for (Contato contato : contatos) {
            if (contato != ignorado && contato.getTelefone().equals(telefone)) {
                return true;
            }
        }
        return false;
    }

    // Formata o celular enquanto a pessoa digita ou cola: "41998765432" vira "(41) 99876-5432"
    private void aplicarMascaraTelefone(TextInputEditText input) {
        input.addTextChangedListener(new TextWatcher() {
            // Evita que a troca de texto feita aqui chame este método de novo
            private boolean formatando = false;

            @Override
            public void beforeTextChanged(CharSequence texto, int inicio, int quantidade, int depois) {
            }

            @Override
            public void onTextChanged(CharSequence texto, int inicio, int antes, int quantidade) {
            }

            // Chamado depois de cada mudança no texto: troca pelo número formatado
            @Override
            public void afterTextChanged(Editable texto) {
                if (formatando) {
                    return;
                }
                String formatado = Util.formatarTelefone(Util.normalizarTelefone(texto.toString()));
                if (formatado.contentEquals(texto)) {
                    return;
                }
                formatando = true;
                texto.replace(0, texto.length(), formatado);
                formatando = false;
            }
        });
    }

    // Some com a mensagem de erro do campo assim que a pessoa começa a corrigir
    private void limparErroAoDigitar(TextInputEditText input, TextInputLayout campo) {
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence texto, int inicio, int quantidade, int depois) {
            }

            @Override
            public void onTextChanged(CharSequence texto, int inicio, int antes, int quantidade) {
            }

            @Override
            public void afterTextChanged(Editable texto) {
                campo.setError(null);
            }
        });
    }

    // Texto digitado num campo (nunca devolve null)
    private String texto(TextInputEditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString();
    }

    // ---- SAMU ----

    // Abre o discador com o 192 preenchido; a pessoa só confirma a ligação (não precisa de permissão)
    private void ligarParaSamu() {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + NUMERO_SAMU)));
        } catch (ActivityNotFoundException e) {
            // Aparelho sem discador (por exemplo, tablet sem chip)
            Snackbar.make(telaContatos, R.string.contatos_discador_indisponivel, Snackbar.LENGTH_SHORT)
                    .setAnchorView(botaoSamu)
                    .show();
        }
    }
}
