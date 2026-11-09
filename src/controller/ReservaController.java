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

    public Reserva reservar(Receptor receptor, String ofertaId) {
        return service.reservar(receptor, service.resolverIdDaOferta(ofertaId));
    }

    public void cancelar(String reservaId) {
        service.cancelar(service.resolverIdReserva(reservaId));
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

    public Retirada assumir(Voluntario voluntario, String reservaId) {
        return service.assumir(voluntario, service.resolverIdReserva(reservaId));
    }

    public void registrarColeta(String retiradaId) {
        service.registrarColeta(service.resolverIdRetirada(retiradaId));
    }

    public void registrarEntrega(String retiradaId) {
        service.registrarEntrega(service.resolverIdRetirada(retiradaId));
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
