package model;

import model.enums.StatusReserva;
import model.enums.StatusRetirada;

import java.util.Date;
import java.util.UUID;

/** Reserva feita por um receptor para uma oferta. */
public class Reserva {

    private UUID id;
    private Date dataReserva;
    private StatusReserva status;
    private OfertaDoacao oferta;
    private Receptor receptor;
    private StatusRetirada statusRetirada;

    /** Nova reserva com status ATIVA. */
    public Reserva(OfertaDoacao oferta, Receptor receptor) {
        this(UUID.randomUUID(), new Date(), StatusReserva.ATIVA, oferta, receptor);
    }

    /** Reconstituição a partir do banco. */
    public Reserva(UUID id, Date dataReserva, StatusReserva status, OfertaDoacao oferta, Receptor receptor) {
        this.id = id;
        this.dataReserva = dataReserva;
        this.status = status;
        this.oferta = oferta;
        this.receptor = receptor;
    }

    public UUID getId() { return id; }
    public Date getDataReserva() { return dataReserva; }
    public StatusReserva getStatus() { return status; }
    public OfertaDoacao getOferta() { return oferta; }
    public Receptor getReceptor() { return receptor; }
    public StatusRetirada getStatusRetirada() { return statusRetirada; }
    public void setStatusRetirada(StatusRetirada statusRetirada) { this.statusRetirada = statusRetirada; }

    @Override
    public String toString() {
        String etapa = statusRetirada == null
                ? (status == StatusReserva.ATIVA ? "aguardando voluntário" : "-")
                : statusRetirada.toString();
        String peso = oferta.getPeso() == 0 ? "peso não informado" : String.format("%.1f kg", oferta.getPeso());
        return String.format("%s | %s | %s | %s | %s | retirada: %s",
                id, status, oferta.getAlimento(), receptor.getNome(), peso, etapa);
    }
}
