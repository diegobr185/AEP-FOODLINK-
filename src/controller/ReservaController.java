package controller;

import model.IndicadorImpacto;
import model.Receptor;
import model.Reserva;
import model.Retirada;
import model.Voluntario;
import service.ReservaService;

import java.util.List;
import java.util.UUID;

/** Camada de controle do RF04, RF05 e RF06. */
public class ReservaController {

    private final ReservaService service = new ReservaService();

    public Reserva reservar(Receptor receptor, UUID ofertaId) {
        return service.reservar(receptor, ofertaId);
    }

    public void cancelar(UUID reservaId) {
        service.cancelar(reservaId);
    }

    public List<Reserva> minhasReservas(UUID receptorId) {
        return service.minhasReservas(receptorId);
    }

    public int reservasAtivas(UUID receptorId) {
        return service.reservasAtivas(receptorId);
    }

    public List<Reserva> solicitacoes() {
        return service.solicitacoes();
    }

    public Retirada assumir(Voluntario voluntario, UUID reservaId) {
        return service.assumir(voluntario, reservaId);
    }

    public void registrarColeta(UUID retiradaId) {
        service.registrarColeta(retiradaId);
    }

    public void registrarEntrega(UUID retiradaId) {
        service.registrarEntrega(retiradaId);
    }

    public List<Retirada> minhasRetiradas(UUID voluntarioId) {
        return service.minhasRetiradas(voluntarioId);
    }

    public List<Retirada> historico() {
        return service.historico();
    }

    public IndicadorImpacto resumoImpacto() {
        return service.resumoImpacto();
    }
}
