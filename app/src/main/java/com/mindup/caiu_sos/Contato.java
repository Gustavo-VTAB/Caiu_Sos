package com.mindup.caiu_sos;

import java.util.Locale;

// Um contato de emergência: quem recebe a localização em caso de queda
public class Contato {

    private String nome;
    // Só os dígitos, com DDD: "41998765432"
    private String telefone;
    // true no contato que recebe o alerta também pelo WhatsApp (só um da lista)
    private boolean favorito;

    // Cria um contato com nome e celular
    public Contato(String nome, String telefone) {
        this.nome = nome;
        this.telefone = telefone;
    }

    // Getters e setters
    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public boolean isFavorito() {
        return favorito;
    }

    public void setFavorito(boolean favorito) {
        this.favorito = favorito;
    }

    // Primeiro nome, para textos curtos como o botão do WhatsApp: "Maria Aparecida" vira "Maria"
    public String getPrimeiroNome() {
        return nome.trim().split("\\s+")[0];
    }

    // Iniciais para o avatar: "Maria Aparecida Santos" vira "MS" e "Carlos" vira "C"
    public String getIniciais() {
        String[] palavras = nome.trim().split("\\s+");
        if (palavras[0].isEmpty()) {
            return "";
        }
        // Primeira letra do primeiro nome + primeira letra do último nome
        String iniciais = primeiraLetra(palavras[0]);
        if (palavras.length > 1) {
            iniciais += primeiraLetra(palavras[palavras.length - 1]);
        }
        return iniciais.toUpperCase(Locale.forLanguageTag("pt-BR"));
    }

    // Primeira letra de uma palavra (funciona também com letras acentuadas)
    private static String primeiraLetra(String palavra) {
        return palavra.substring(0, palavra.offsetByCodePoints(0, 1));
    }
}
