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

/**
 * RF04 (reserva) + RF05 (retirada/entrega) + RF06 (indicadores).
 * Regras aplicadas: RN01, RN02 (3 reservas ativas) e RN03 (oferta exclusiva).
 */
public class ReservaService {

    private final ReservaRepository reservas = new ReservaRepository();
    private final OfertaRepository ofertas = new OfertaRepository();

    /* ------------------------------ RF04 ------------------------------ */

    /** Reserva uma oferta disponível. */
    public Reserva reservar(Receptor receptor, UUID ofertaId) {
        OfertaDoacao oferta = ofertas.buscarPorId(ofertaId)
                .orElseThrow(() -> new IllegalArgumentException("Oferta não encontrada."));

        if (oferta.getStatus() != StatusOferta.DISPONIVEL) {          // RN03
            throw new IllegalStateException("RN03: oferta indisponível para reserva (status " + oferta.getStatus() + ").");
        }
        if (!oferta.verificarValidadeMinima()) {                      // RN01
            throw new IllegalStateException("RN01: oferta com menos de 2 horas de validade.");
        }
        int ativas = reservas.contarAtivasPorReceptor(receptor.getId());
        if (ativas >= receptor.getLimiteReservasAtivas()) {           // RN02
            throw new IllegalStateException("RN02: o receptor já possui " + ativas
                    + " reservas ativas (limite " + receptor.getLimiteReservasAtivas() + ").");
        }

        Reserva reserva = receptor.reservarOferta(oferta);
        reservas.salvar(reserva);
        ofertas.atualizarStatus(oferta.getId(), StatusOferta.RESERVADA);
        receptor.notificar();
        return reserva;
    }

    /** Cancela uma reserva ativa e devolve a oferta ao estoque. */
    public void cancelar(UUID reservaId) {
        Reserva reserva = buscarPorId(reservaId);
        reserva.cancelar();
        reservas.atualizarStatus(reserva);
        ofertas.atualizarStatus(reserva.getOferta().getId(), StatusOferta.DISPONIVEL);
    }

    /** Aceita o id (completo ou abreviado) da oferta informada no menu. */
    public UUID resolverIdDaOferta(String texto) {
        String t = texto == null ? "" : texto.trim();
        if (t.length() == 36) {
            return UUID.fromString(t);
        }
        return ofertas.idPorPrefixo(t)
                .orElseThrow(() -> new IllegalArgumentException("Oferta não encontrada: " + t));
    }

    /** Aceita o id completo da reserva ou apenas os primeiros caracteres. */
    public UUID resolverIdReserva(String texto) {
        String t = texto == null ? "" : texto.trim();
        if (t.length() == 36) {
            return UUID.fromString(t);
        }
        return reservas.idReservaPorPrefixo(t)
                .orElseThrow(() -> new IllegalArgumentException("Reserva não encontrada: " + t));
    }

    /** Aceita o id completo da retirada ou apenas os primeiros caracteres. */
    public UUID resolverIdRetirada(String texto) {
        String t = texto == null ? "" : texto.trim();
        if (t.length() == 36) {
            return UUID.fromString(t);
        }
        return reservas.idRetiradaPorPrefixo(t)
                .orElseThrow(() -> new IllegalArgumentException("Retirada não encontrada: " + t));
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

    /* ------------------------------ RF05 ------------------------------ */

    /** Solicitações de retirada: reservas ativas que ainda não têm voluntário. */
    public List<Reserva> solicitacoes() {
        return reservas.listarSolicitacoes();
    }

    /** O voluntário assume a reserva (cria a Retirada). */
    public Retirada assumir(Voluntario voluntario, UUID reservaId) {
        Reserva reserva = buscarPorId(reservaId);
        Retirada retirada = voluntario.assumirRetirada(reserva);
        reservas.salvar(retirada);
        reserva.vincularRetirada(retirada);
        voluntario.notificar();
        return retirada;
    }

    /** Registra a coleta (PENDENTE -> EM_ANDAMENTO). */
    public void registrarColeta(UUID retiradaId) {
        Retirada retirada = buscarRetirada(retiradaId);
        retirada.registrarColeta();
        reservas.atualizar(retirada);
    }

    /** Registra a entrega: conclui a reserva/oferta e gera o indicador de impacto. */
    public void registrarEntrega(UUID retiradaId) {
        Retirada retirada = buscarRetirada(retiradaId);
        retirada.registrarEntrega();
        reservas.atualizar(retirada);

        Reserva reserva = retirada.getReserva();
        reservas.atualizarStatus(reserva);
        ofertas.atualizarStatus(reserva.getOferta().getId(), StatusOferta.CONCLUIDA);

        OfertaDoacao oferta = reserva.getOferta();
        if (!reservas.existeIndicador(oferta.getId())) {
            reservas.salvar(new IndicadorImpacto(oferta));
        }
    }

    public List<Retirada> minhasRetiradas(UUID voluntarioId) {
        return reservas.listarPorVoluntario(voluntarioId);
    }

    /* ------------------------------ RF06 ------------------------------ */

    public List<Retirada> historico() {
        return reservas.listarEntregues();
    }

    public IndicadorImpacto resumoImpacto() {
        return reservas.resumo();
    }

    private Retirada buscarRetirada(UUID id) {
        return reservas.buscarRetirada(id)
                .orElseThrow(() -> new IllegalArgumentException("Retirada não encontrada."));
    }
}
