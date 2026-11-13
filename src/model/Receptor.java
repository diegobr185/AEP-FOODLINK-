package model;

import java.util.Date;
import java.util.UUID;

/** Usuário que pode reservar ofertas. */
public class Receptor extends Usuario {

    private int limiteReservasAtivas = 3;

    public Receptor(String nome, String email, String documento, String senhaHash) {
        super(nome, email, documento, senhaHash);
    }

    public Receptor(UUID id, String nome, String email, String documento, String senhaHash,
                    Date dataCadastro, int limiteReservasAtivas) {
        super(id, nome, email, documento, senhaHash, dataCadastro);
        this.limiteReservasAtivas = limiteReservasAtivas;
    }

    /** Cria a reserva de uma oferta. */
    public Reserva reservarOferta(OfertaDoacao oferta) {
        if (oferta == null) {
            throw new IllegalArgumentException("Oferta não informada.");
        }
        return new Reserva(oferta, this);
    }

    @Override
    public void notificar() {
        System.out.println("[Receptor] " + getNome() + ", reserva efetuada com sucesso.");
    }

    @Override
    public String getTipo() { return "RECEPTOR"; }

    public int getLimiteReservasAtivas() { return limiteReservasAtivas; }
}
