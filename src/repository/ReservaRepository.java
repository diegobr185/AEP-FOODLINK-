package repository;

import config.ConexaoDB;
import model.IndicadorImpacto;
import model.OfertaDoacao;
import model.Receptor;
import model.Reserva;
import model.Retirada;
import model.Usuario;
import model.Voluntario;
import model.enums.StatusReserva;
import model.enums.StatusRetirada;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência da reserva, da retirada e do indicador de impacto. */
public class ReservaRepository {

    private final OfertaRepository ofertas = new OfertaRepository();
    private final UsuarioRepository usuarios = new UsuarioRepository();

    private static final String SELECT_RESERVA =
            "SELECT r.*, t.status AS status_retirada FROM reserva r "
          + "LEFT JOIN retirada t ON t.reserva_id = r.id ";

    /** Reserva e mudança de status da oferta acontecem na mesma transação. */
    public void reservar(Reserva reserva) {
        Transacoes.executar("Erro ao reservar oferta", con -> {
            UUID receptorId = reserva.getReceptor().getId();
            UUID ofertaId = reserva.getOferta().getId();
            int limite;
            // A trava no receptor impede ultrapassar o limite em reservas simultâneas.
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT tipo, COALESCE(limite_reservas_ativas, 3) AS limite FROM usuario WHERE id = ? FOR UPDATE")) {
                ps.setObject(1, receptorId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || !"RECEPTOR".equals(rs.getString("tipo"))) {
                        throw new IllegalArgumentException("Receptor não encontrado.");
                    }
                    limite = Math.min(3, rs.getInt("limite"));
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COUNT(*) FROM reserva WHERE receptor_id = ? AND status = 'ATIVA'")) {
                ps.setObject(1, receptorId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) >= limite) {
                        throw new IllegalStateException("RN02: o receptor atingiu o limite de reservas ativas.");
                    }
                }
            }
            // A trava na oferta impede que duas pessoas reservem a mesma doação.
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT status, data_validade > clock_timestamp() AS valida "
                  + "FROM oferta_doacao WHERE id = ? FOR UPDATE")) {
                ps.setObject(1, ofertaId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Oferta não encontrada.");
                    if ("EXPIRADA".equals(rs.getString("status"))) {
                        throw new IllegalStateException("A oferta está vencida.");
                    }
                    if (!"DISPONIVEL".equals(rs.getString("status"))) {
                        throw new IllegalStateException("RN03: esta oferta não está disponível.");
                    }
                    if (!rs.getBoolean("valida")) {
                        throw new IllegalStateException("A oferta está vencida.");
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO reserva (id, oferta_id, receptor_id, data_reserva, status) VALUES (?, ?, ?, ?, 'ATIVA')")) {
                ps.setObject(1, reserva.getId());
                ps.setObject(2, ofertaId);
                ps.setObject(3, receptorId);
                ps.setTimestamp(4, new Timestamp(reserva.getDataReserva().getTime()));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE oferta_doacao SET status = 'RESERVADA' WHERE id = ? AND status = 'DISPONIVEL'")) {
                ps.setObject(1, ofertaId);
                exigirUmaLinha(ps.executeUpdate(), "A oferta não está disponível.");
            }
            return null;
        });
    }

    /** O próprio receptor pode cancelar antes da coleta. */
    public void cancelar(UUID reservaId, UUID receptorId) {
        Transacoes.executar("Erro ao cancelar reserva", con -> {
            DadosReserva reserva = bloquearReserva(con, reservaId);
            if (!reserva.receptorId.equals(receptorId)) {
                throw new SecurityException("Você só pode cancelar suas próprias reservas.");
            }
            if (!"ATIVA".equals(reserva.status)) {
                throw new IllegalStateException("Somente reservas ativas podem ser canceladas.");
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT id, status FROM retirada WHERE reserva_id = ? FOR UPDATE")) {
                ps.setObject(1, reservaId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        if (!"PENDENTE".equals(rs.getString("status"))) {
                            throw new IllegalStateException("A coleta já começou; não é possível cancelar.");
                        }
                        try (PreparedStatement excluir = con.prepareStatement("DELETE FROM retirada WHERE id = ?")) {
                            excluir.setObject(1, rs.getObject("id"));
                            excluir.executeUpdate();
                        }
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE reserva SET status = 'CANCELADA' WHERE id = ?")) {
                ps.setObject(1, reservaId);
                exigirUmaLinha(ps.executeUpdate(), "Reserva não encontrada.");
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE oferta_doacao SET status = CASE WHEN data_validade <= clock_timestamp() "
                  + "THEN 'EXPIRADA' ELSE 'DISPONIVEL' END WHERE id = ? AND status = 'RESERVADA'")) {
                ps.setObject(1, reserva.ofertaId);
                exigirUmaLinha(ps.executeUpdate(), "A oferta não está reservada.");
            }
            return null;
        });
    }

    /** Uma reserva ativa pode ser assumida por apenas um voluntário. */
    public void assumir(Retirada retirada) {
        Transacoes.executar("Erro ao assumir retirada", con -> {
            DadosReserva reserva = bloquearReserva(con, retirada.getReserva().getId());
            if (!"ATIVA".equals(reserva.status)) {
                throw new IllegalStateException("Somente reservas ativas podem ser assumidas.");
            }
            validarOfertaParaColeta(con, reserva.ofertaId);
            try (PreparedStatement ps = con.prepareStatement("SELECT tipo FROM usuario WHERE id = ?")) {
                ps.setObject(1, retirada.getVoluntario().getId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || !"VOLUNTARIO".equals(rs.getString("tipo"))) {
                        throw new IllegalArgumentException("Voluntário não encontrado.");
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM retirada WHERE reserva_id = ?")) {
                ps.setObject(1, retirada.getReserva().getId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) throw new IllegalStateException("Esta retirada já foi assumida.");
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO retirada (id, reserva_id, voluntario_id, status) VALUES (?, ?, ?, 'PENDENTE')")) {
                ps.setObject(1, retirada.getId());
                ps.setObject(2, retirada.getReserva().getId());
                ps.setObject(3, retirada.getVoluntario().getId());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Apenas o voluntário responsável pode registrar a coleta. */
    public void registrarColeta(UUID retiradaId, UUID voluntarioId) {
        Transacoes.executar("Erro ao registrar coleta", con -> {
            UUID reservaId = reservaDaRetirada(con, retiradaId);
            DadosReserva reserva = bloquearReserva(con, reservaId);
            DadosRetirada retirada = bloquearRetirada(con, retiradaId);
            validarResponsavel(retirada, voluntarioId);
            if (!"ATIVA".equals(reserva.status) || !"PENDENTE".equals(retirada.status)) {
                throw new IllegalStateException("A coleta exige uma retirada pendente e uma reserva ativa.");
            }
            validarOfertaParaColeta(con, reserva.ofertaId);
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE retirada SET status = 'EM_ANDAMENTO', data_coleta = CURRENT_TIMESTAMP WHERE id = ?")) {
                ps.setObject(1, retiradaId);
                exigirUmaLinha(ps.executeUpdate(), "Retirada não encontrada.");
            }
            return null;
        });
    }

    /** Conclui retirada, reserva e oferta e grava o indicador: tudo ou nada. */
    public void registrarEntrega(UUID retiradaId, UUID voluntarioId) {
        Transacoes.executar("Erro ao registrar entrega", con -> {
            UUID reservaId = reservaDaRetirada(con, retiradaId);
            DadosReserva reserva = bloquearReserva(con, reservaId);
            DadosRetirada retirada = bloquearRetirada(con, retiradaId);
            validarResponsavel(retirada, voluntarioId);
            if (!"ATIVA".equals(reserva.status) || !"EM_ANDAMENTO".equals(retirada.status)) {
                throw new IllegalStateException("A entrega exige uma reserva ativa e uma coleta registrada.");
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE retirada SET status = 'ENTREGUE', data_entrega = CURRENT_TIMESTAMP WHERE id = ?")) {
                ps.setObject(1, retiradaId);
                exigirUmaLinha(ps.executeUpdate(), "Retirada não encontrada.");
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE reserva SET status = 'CONCLUIDA' WHERE id = ?")) {
                ps.setObject(1, reservaId);
                exigirUmaLinha(ps.executeUpdate(), "Reserva não encontrada.");
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE oferta_doacao SET status = 'CONCLUIDA' WHERE id = ? AND status = 'RESERVADA'")) {
                ps.setObject(1, reserva.ofertaId);
                exigirUmaLinha(ps.executeUpdate(), "A oferta não está reservada.");
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO indicador_impacto (id, oferta_id, peso_total_kg, co2_evitado_kg, data_calculo) "
                  + "SELECT ?, id, peso, peso * 2.5, CURRENT_TIMESTAMP FROM oferta_doacao WHERE id = ?")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, reserva.ofertaId);
                exigirUmaLinha(ps.executeUpdate(), "Oferta não encontrada para o indicador.");
            }
            return null;
        });
    }

    /** Não inicia a retirada de alimento já vencido; o receptor pode cancelar. */
    private void validarOfertaParaColeta(Connection con, UUID ofertaId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT status, data_validade > clock_timestamp() AS valida FROM oferta_doacao WHERE id = ? FOR UPDATE")) {
            ps.setObject(1, ofertaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !"RESERVADA".equals(rs.getString("status"))) {
                    throw new IllegalStateException("A oferta não está reservada.");
                }
                if (!rs.getBoolean("valida")) {
                    throw new IllegalStateException("A oferta venceu. O receptor pode cancelar a reserva.");
                }
            }
        }
    }

    private UUID reservaDaRetirada(Connection con, UUID retiradaId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT reserva_id FROM retirada WHERE id = ?")) {
            ps.setObject(1, retiradaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Retirada não encontrada.");
                return (UUID) rs.getObject(1);
            }
        }
    }

    private DadosReserva bloquearReserva(Connection con, UUID id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT oferta_id, receptor_id, status FROM reserva WHERE id = ? FOR UPDATE")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Reserva não encontrada.");
                return new DadosReserva((UUID) rs.getObject("oferta_id"),
                        (UUID) rs.getObject("receptor_id"), rs.getString("status"));
            }
        }
    }

    private DadosRetirada bloquearRetirada(Connection con, UUID id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT voluntario_id, status FROM retirada WHERE id = ? FOR UPDATE")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Retirada não encontrada.");
                return new DadosRetirada((UUID) rs.getObject("voluntario_id"), rs.getString("status"));
            }
        }
    }

    private void validarResponsavel(DadosRetirada retirada, UUID voluntarioId) {
        if (!retirada.voluntarioId.equals(voluntarioId)) {
            throw new SecurityException("Você só pode atualizar suas próprias retiradas.");
        }
    }

    private void exigirUmaLinha(int alteradas, String erro) {
        if (alteradas != 1) throw new IllegalStateException(erro);
    }

    private static class DadosReserva {
        final UUID ofertaId;
        final UUID receptorId;
        final String status;
        DadosReserva(UUID ofertaId, UUID receptorId, String status) {
            this.ofertaId = ofertaId;
            this.receptorId = receptorId;
            this.status = status;
        }
    }

    private static class DadosRetirada {
        final UUID voluntarioId;
        final String status;
        DadosRetirada(UUID voluntarioId, String status) {
            this.voluntarioId = voluntarioId;
            this.status = status;
        }
    }

    public Optional<Reserva> buscarPorId(UUID id) {
        return buscarReserva(SELECT_RESERVA + "WHERE r.id = ?", id);
    }

    public int contarAtivasPorReceptor(UUID receptorId) {
        try (Connection con = ConexaoDB.conectar();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM reserva WHERE receptor_id = ? AND status = 'ATIVA'")) {
            ps.setObject(1, receptorId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao contar reservas ativas: " + e.getMessage(), e);
        }
    }

    public List<Reserva> listarPorReceptor(UUID receptorId) {
        return listarReservas(SELECT_RESERVA + "WHERE r.receptor_id = ? ORDER BY r.data_reserva DESC", receptorId);
    }

    public List<Reserva> listarSolicitacoes() {
        String sql = SELECT_RESERVA + "JOIN oferta_doacao o ON o.id = r.oferta_id "
                + "WHERE r.status = 'ATIVA' AND t.id IS NULL AND o.status = 'RESERVADA' "
                + "AND o.data_validade > CURRENT_TIMESTAMP ORDER BY r.data_reserva";
        List<Reserva> lista = new ArrayList<>();
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapearReserva(rs));
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao listar solicitações: " + e.getMessage(), e);
        }
        return lista;
    }

    public List<Retirada> listarPorVoluntario(UUID voluntarioId) {
        return listarRetiradas("SELECT * FROM retirada WHERE voluntario_id = ? ORDER BY id", voluntarioId);
    }

    public List<Retirada> listarEntregues() {
        return listarRetiradas("SELECT * FROM retirada WHERE status = ? ORDER BY data_entrega DESC", "ENTREGUE");
    }

    public IndicadorImpacto resumo() {
        try (Connection con = ConexaoDB.conectar();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COALESCE(SUM(peso_total_kg), 0) AS peso, COALESCE(SUM(co2_evitado_kg), 0) AS co2 "
                   + "FROM indicador_impacto");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return new IndicadorImpacto(rs.getFloat("peso"), rs.getFloat("co2"));
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao calcular indicadores: " + e.getMessage(), e);
        }
    }

    private Optional<Reserva> buscarReserva(String sql, Object valor) {
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, valor);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapearReserva(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao buscar reserva: " + e.getMessage(), e);
        }
    }

    private List<Reserva> listarReservas(String sql, Object valor) {
        List<Reserva> lista = new ArrayList<>();
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, valor);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapearReserva(rs));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao listar reservas: " + e.getMessage(), e);
        }
        return lista;
    }

    private Reserva mapearReserva(ResultSet rs) throws SQLException {
        UUID ofertaId = UUID.fromString(rs.getString("oferta_id"));
        UUID receptorId = UUID.fromString(rs.getString("receptor_id"));
        OfertaDoacao oferta = ofertas.buscarPorId(ofertaId)
                .orElseThrow(() -> new PersistenciaException("Oferta da reserva não encontrada: " + ofertaId));
        Usuario usuario = usuarios.buscarPorId(receptorId)
                .orElseThrow(() -> new PersistenciaException("Receptor não encontrado: " + receptorId));
        Reserva reserva = new Reserva(UUID.fromString(rs.getString("id")),
                new Date(rs.getTimestamp("data_reserva").getTime()),
                StatusReserva.valueOf(rs.getString("status")), oferta, (Receptor) usuario);
        String statusRetirada = rs.getString("status_retirada");
        if (statusRetirada != null) {
            reserva.setStatusRetirada(StatusRetirada.valueOf(statusRetirada));
        }
        return reserva;
    }

    private List<Retirada> listarRetiradas(String sql, Object valor) {
        List<Retirada> lista = new ArrayList<>();
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, valor);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapearRetirada(rs));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao listar retiradas: " + e.getMessage(), e);
        }
        return lista;
    }

    private Retirada mapearRetirada(ResultSet rs) throws SQLException {
        UUID reservaId = UUID.fromString(rs.getString("reserva_id"));
        UUID voluntarioId = UUID.fromString(rs.getString("voluntario_id"));
        Reserva reserva = buscarPorId(reservaId)
                .orElseThrow(() -> new PersistenciaException("Reserva da retirada não encontrada: " + reservaId));
        Usuario usuario = usuarios.buscarPorId(voluntarioId)
                .orElseThrow(() -> new PersistenciaException("Voluntário não encontrado: " + voluntarioId));
        Timestamp coleta = rs.getTimestamp("data_coleta");
        Timestamp entrega = rs.getTimestamp("data_entrega");
        Retirada retirada = new Retirada(UUID.fromString(rs.getString("id")),
                coleta == null ? null : new Date(coleta.getTime()),
                entrega == null ? null : new Date(entrega.getTime()),
                StatusRetirada.valueOf(rs.getString("status")), reserva, (Voluntario) usuario);
        return retirada;
    }
}
