package service;

import model.IndicadorImpacto;
import model.OfertaDoacao;
import model.Receptor;
import model.Reserva;
import model.Retirada;
import model.Voluntario;
import model.enums.StatusOferta;
import repository.OfertaRepository;
import repository.ReservaRepository;

import java.util.List;
import java.util.UUID;

/** Regras de reserva, retirada, entrega e indicadores. */
public class ReservaService {

    private final ReservaRepository reservas = new ReservaRepository();
    private final OfertaRepository ofertas = new OfertaRepository();

    /** O repositório confere as regras novamente sob trava e grava tudo na mesma transação. */
    public Reserva reservar(Receptor receptor, UUID ofertaId) {
        if (receptor == null) throw new IllegalArgumentException("Informe o receptor.");
        OfertaDoacao oferta = ofertas.buscarPorId(ofertaId)
                .orElseThrow(() -> new IllegalArgumentException("Oferta não encontrada."));
        Reserva reserva = receptor.reservarOferta(oferta);
        reservas.reservar(reserva);
        oferta.setStatus(StatusOferta.RESERVADA);
        receptor.notificar();
        return reserva;
    }

    public void cancelar(Receptor receptor, UUID reservaId) {
        if (receptor == null) throw new IllegalArgumentException("Informe o receptor.");
        reservas.cancelar(reservaId, receptor.getId());
    }

    public Reserva buscarPorId(UUID id) {
        return reservas.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Reserva não encontrada."));
    }

    public List<Reserva> minhasReservas(UUID receptorId) {
        return reservas.listarPorReceptor(receptorId);
    }

    public int reservasAtivas(UUID receptorId) {
        return reservas.contarAtivasPorReceptor(receptorId);
    }

    public List<Reserva> solicitacoes() {
        return reservas.listarSolicitacoes();
    }

    public Retirada assumir(Voluntario voluntario, UUID reservaId) {
        if (voluntario == null) throw new IllegalArgumentException("Informe o voluntário.");
        Reserva reserva = buscarPorId(reservaId);
        Retirada retirada = voluntario.assumirRetirada(reserva);
        reservas.assumir(retirada);
        voluntario.notificar();
        return retirada;
    }

    public void registrarColeta(Voluntario voluntario, UUID retiradaId) {
        if (voluntario == null) throw new IllegalArgumentException("Informe o voluntário.");
        reservas.registrarColeta(retiradaId, voluntario.getId());
    }

    public void registrarEntrega(Voluntario voluntario, UUID retiradaId) {
        if (voluntario == null) throw new IllegalArgumentException("Informe o voluntário.");
        reservas.registrarEntrega(retiradaId, voluntario.getId());
    }

    public List<Retirada> minhasRetiradas(UUID voluntarioId) {
        return reservas.listarPorVoluntario(voluntarioId);
    }

    public List<Retirada> historico() {
        return reservas.listarEntregues();
    }

    public IndicadorImpacto resumoImpacto() {
        return reservas.resumo();
    }
}
