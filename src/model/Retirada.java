package model;

import model.enums.StatusRetirada;

import java.util.Date;
import java.util.UUID;

/** Retirada — +registrarColeta() | +registrarEntrega(). RF05. */
public class Retirada {

    private UUID id;
    private Date dataColeta;
    private Date dataEntrega;
    private StatusRetirada status;
    private Reserva reserva;
    private Voluntario voluntario;

    /** Retirada assumida por um voluntário (status PENDENTE). */
    public Retirada(Reserva reserva, Voluntario voluntario) {
        this(UUID.randomUUID(), null, null, StatusRetirada.PENDENTE, reserva, voluntario);
    }

    /** Reconstituição a partir do banco. */
    public Retirada(UUID id, Date dataColeta, Date dataEntrega, StatusRetirada status,
                    Reserva reserva, Voluntario voluntario) {
        this.id = id;
        this.dataColeta = dataColeta;
        this.dataEntrega = dataEntrega;
        this.status = status;
        this.reserva = reserva;
        this.voluntario = voluntario;
    }

    /** +registrarColeta() — PENDENTE -> EM_ANDAMENTO. */
    public void registrarColeta() {
        if (status != StatusRetirada.PENDENTE) {
            throw new IllegalStateException("Coleta já registrada para esta retirada.");
        }
        this.dataColeta = new Date();
        this.status = StatusRetirada.EM_ANDAMENTO;
    }

    /** +registrarEntrega() — EM_ANDAMENTO -> ENTREGUE e conclui a reserva. */
    public void registrarEntrega() {
        if (status != StatusRetirada.EM_ANDAMENTO) {
            throw new IllegalStateException("Registre a coleta antes de registrar a entrega.");
        }
        this.dataEntrega = new Date();
        this.status = StatusRetirada.ENTREGUE;
        this.reserva.confirmarRetirada();
    }

    public UUID getId() { return id; }
    public Date getDataColeta() { return dataColeta; }
    public Date getDataEntrega() { return dataEntrega; }
    public StatusRetirada getStatus() { return status; }
    public Reserva getReserva() { return reserva; }
    public Voluntario getVoluntario() { return voluntario; }
}
