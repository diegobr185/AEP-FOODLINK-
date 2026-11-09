package model;

import java.util.Date;
import java.util.UUID;

/** Doador — «publica» 1 ◆—— 0..* OfertaDoacao. +cadastrarOferta() | +notificar() */
public class Doador extends Usuario {

    public Doador(String nome, String email, String documento, String senhaHash) {
        super(nome, email, documento, senhaHash);
    }

    public Doador(UUID id, String nome, String email, String documento, String senhaHash, Date dataCadastro) {
        super(id, nome, email, documento, senhaHash, dataCadastro);
    }

    /** +cadastrarOferta(dados) : OfertaDoacao */
    public OfertaDoacao cadastrarOferta(Endereco endereco, String alimento, float quantidade,
                                        float peso, Date dataValidade, String observacoes) {
        return new OfertaDoacao(this, endereco, alimento, quantidade, peso, dataValidade, observacoes);
    }

    @Override
    public void notificar() {
        System.out.println("[Doador] " + getNome() + ", sua oferta foi registrada com sucesso.");
    }

    @Override
    public String getTipo() { return "DOADOR"; }
}
