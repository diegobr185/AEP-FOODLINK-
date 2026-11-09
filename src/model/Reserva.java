package model;

import model.enums.StatusOferta;
import model.enums.StatusReserva;
import model.enums.StatusRetirada;

import java.util.Date;
import java.util.UUID;

/** Reserva — +cancelar() | +confirmarRetirada(). RF04 com RN02 e RN03. */
public class Reserva {

    private UUID id;
    private Date dataReserva;
    private StatusReserva status;
    private OfertaDoacao oferta;
    private Receptor receptor;
    private Retirada retirada;

    /** Nova reserva (status ATIVA e oferta marcada como RESERVADA). */
    public Reserva(OfertaDoacao oferta, Receptor receptor) {
        this(UUID.randomUUID(), new Date(), StatusReserva.ATIVA, oferta, receptor);
        oferta.setStatus(StatusOferta.RESERVADA);
    }

    /** Reconstituição a partir do banco. */
    public Reserva(UUID id, Date dataReserva, StatusReserva status, OfertaDoacao oferta, Receptor receptor) {
        this.id = id;
        this.dataReserva = dataReserva;
        this.status = status;
        this.oferta = oferta;
        this.receptor = receptor;
    }

    /** +cancelar() — devolve a oferta ao estoque. */
    public void cancelar() {
        if (status != StatusReserva.ATIVA) {
            throw new IllegalStateException("Somente reservas ATIVAS podem ser canceladas.");
        }
        if (retirada != null && retirada.getStatus() != StatusRetirada.PENDENTE) {
            throw new IllegalStateException("Retirada já iniciada: não é possível cancelar a reserva.");
        }
        this.status = StatusReserva.CANCELADA;
        this.oferta.setStatus(StatusOferta.DISPONIVEL);
    }

    /** +confirmarRetirada() — encerra a reserva quando a entrega é registrada. */
    public void confirmarRetirada() {
        if (status != StatusReserva.ATIVA) {
            throw new IllegalStateException("Somente reservas ATIVAS podem ser confirmadas.");
        }
        this.status = StatusReserva.CONCLUIDA;
        this.oferta.setStatus(StatusOferta.CONCLUIDA);
    }

    public void vincularRetirada(Retirada retirada) { this.retirada = retirada; }

    public UUID getId() { return id; }
    public Date getDataReserva() { return dataReserva; }
    public StatusReserva getStatus() { return status; }
    public OfertaDoacao getOferta() { return oferta; }
    public Receptor getReceptor() { return receptor; }
    public Retirada getRetirada() { return retirada; }

    @Override
    public String toString() {
        return String.format("%s | %s | %s | %s | %.1f kg",
                id.toString().substring(0, 8), status, oferta.getAlimento(),
                receptor.getNome(), oferta.getPeso());
    }
}
