package model;

import java.util.Date;
import java.util.UUID;

/** Voluntario — «executa» 1 ◆—— 0..* Retirada. +assumirRetirada(reserva) : Retirada | +notificar() */
public class Voluntario extends Usuario {

    public Voluntario(String nome, String email, String documento, String senhaHash) {
        super(nome, email, documento, senhaHash);
    }

    public Voluntario(UUID id, String nome, String email, String documento, String senhaHash, Date dataCadastro) {
        super(id, nome, email, documento, senhaHash, dataCadastro);
    }

    /** +assumirRetirada(reserva) : Retirada */
    public Retirada assumirRetirada(Reserva reserva) {
        if (reserva == null) {
            throw new IllegalArgumentException("Reserva não informada.");
        }
        return new Retirada(reserva, this);
    }

    @Override
    public void notificar() {
        System.out.println("[Voluntario] " + getNome() + ", você tem uma retirada atribuída.");
    }

    @Override
    public String getTipo() { return "VOLUNTARIO"; }
}
