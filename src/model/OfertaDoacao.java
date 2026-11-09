package model;

import model.enums.StatusOferta;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * OfertaDoacao — entidade principal do CRUD (RF02) e da busca por raio (RF03).
 */
public class OfertaDoacao {

    private UUID id;
    private Doador doador;
    private Endereco endereco;
    private String alimento;
    private float quantidade;
    private float peso;
    private Date dataValidade;
    private String observacoes;
    private StatusOferta status;

    /** Preenchido apenas na busca por raio (não é coluna do banco). */
    private double distanciaKm = -1;

    /** Nova oferta publicada pelo doador (status DISPONIVEL). */
    public OfertaDoacao(Doador doador, Endereco endereco, String alimento, float quantidade,
                        float peso, Date dataValidade, String observacoes) {
        this(UUID.randomUUID(), doador, endereco, alimento, quantidade, peso, dataValidade,
                observacoes, StatusOferta.DISPONIVEL);
    }

    /** Reconstituição a partir do banco de dados. */
    public OfertaDoacao(UUID id, Doador doador, Endereco endereco, String alimento, float quantidade,
                        float peso, Date dataValidade, String observacoes, StatusOferta status) {
        this.id = id;
        this.doador = doador;
        this.endereco = endereco;
        this.alimento = alimento;
        this.quantidade = quantidade;
        this.peso = peso;
        this.dataValidade = dataValidade;
        this.observacoes = observacoes;
        this.status = status;
    }

    /** +verificarValidadeMinima() : boolean — RN01: pelo menos 2 horas de validade. */
    public boolean verificarValidadeMinima() {
        return dataValidade.getTime() - System.currentTimeMillis() >= 2L * 60 * 60 * 1000;
    }

    public UUID getId() { return id; }
    public Doador getDoador() { return doador; }
    public Endereco getEndereco() { return endereco; }
    public String getAlimento() { return alimento; }
    public float getQuantidade() { return quantidade; }
    public float getPeso() { return peso; }
    public Date getDataValidade() { return dataValidade; }
    public String getObservacoes() { return observacoes; }
    public StatusOferta getStatus() { return status; }
    public double getDistanciaKm() { return distanciaKm; }

    public void setAlimento(String alimento) { this.alimento = alimento; }
    public void setQuantidade(float quantidade) { this.quantidade = quantidade; }
    public void setPeso(float peso) { this.peso = peso; }
    public void setDataValidade(Date dataValidade) { this.dataValidade = dataValidade; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public void setStatus(StatusOferta status) { this.status = status; }
    public void setDistanciaKm(double distanciaKm) { this.distanciaKm = distanciaKm; }

    @Override
    public String toString() {
        String validade = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(dataValidade);
        String distancia = distanciaKm < 0 ? "" : String.format(" | %.1f km", distanciaKm);
        return String.format("%s | %s | %.1f un | %.1f kg | validade %s | %s%s",
                id.toString().substring(0, 8), alimento, quantidade, peso, validade, status, distancia);
    }
}
