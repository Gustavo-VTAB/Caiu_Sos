package com.mindup.caiu_sos;

// Um registro do histórico: queda, pedido de ajuda (pânico) ou check-in, com hora e local
public class Evento {

    // Os tipos de evento que o app registra
    public enum Tipo {
        QUEDA,
        PANICO,
        // A pessoa respondeu "Estou bem" ao check-in de segurança
        CHECKIN,
        // Ninguém respondeu ao check-in e o alerta foi aberto
        CHECKIN_SEM_RESPOSTA
    }

    private Tipo tipo;
    // true quando a pessoa segurou "ESTOU BEM" antes de o alerta ser enviado
    private boolean cancelado;
    // Quando aconteceu, em milissegundos (System.currentTimeMillis())
    private long quando;
    private String endereco;
    private double latitude;
    private double longitude;
    // O que a API respondeu ("Alerta enviado ao servidor" / "Falha: ..."); null se não foi enviado
    private String resultadoServidor;

    // Cria um evento com todos os dados
    public Evento(Tipo tipo, boolean cancelado, long quando, String endereco, double latitude, double longitude) {
        this.tipo = tipo;
        this.cancelado = cancelado;
        this.quando = quando;
        this.endereco = endereco;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Getters
    public Tipo getTipo() {
        return tipo;
    }

    public boolean isCancelado() {
        return cancelado;
    }

    public long getQuando() {
        return quando;
    }

    public String getEndereco() {
        return endereco;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getResultadoServidor() {
        return resultadoServidor;
    }

    // Setters (a tela de alerta completa o evento depois: cancelado, endereço e resposta da API)
    public void setCancelado(boolean cancelado) {
        this.cancelado = cancelado;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public void setResultadoServidor(String resultadoServidor) {
        this.resultadoServidor = resultadoServidor;
    }

    // Diz se o evento tem coordenadas (0,0 = a localização não foi obtida)
    public boolean temLocalizacao() {
        return latitude != 0 || longitude != 0;
    }
}
