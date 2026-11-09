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

/**
 * Persistência da Reserva (RF04), da Retirada (RF05) e do IndicadorImpacto (RF06).
 * As três tabelas participam do mesmo fluxo, por isso ficam neste repositório.
 */
public class ReservaRepository {

    private final OfertaRepository ofertas = new OfertaRepository();
    private final UsuarioRepository usuarios = new UsuarioRepository();

    /* ----------------------------- RESERVA ----------------------------- */

    /** CREATE */
    public void salvar(Reserva r) {
        String sql = "INSERT INTO reserva (id, oferta_id, receptor_id, data_reserva, status) VALUES (?, ?, ?, ?, ?)";
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, r.getId());
            ps.setObject(2, r.getOferta().getId());
            ps.setObject(3, r.getReceptor().getId());
            ps.setTimestamp(4, new Timestamp(r.getDataReserva().getTime()));
            ps.setString(5, r.getStatus().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao salvar reserva: " + e.getMessage(), e);
        }
    }

    /** UPDATE do status (cancelar / concluir). */
    public void atualizarStatus(Reserva r) {
        try (Connection con = ConexaoDB.conectar();
             PreparedStatement ps = con.prepareStatement("UPDATE reserva SET status = ? WHERE id = ?")) {
            ps.setString(1, r.getStatus().name());
            ps.setObject(2, r.getId());
            if (ps.executeUpdate() == 0) {
                throw new PersistenciaException("Reserva não encontrada para atualização.");
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao atualizar reserva: " + e.getMessage(), e);
        }
    }

    /** READ por id */
    public Optional<Reserva> buscarPorId(UUID id) {
        return buscarReserva("SELECT * FROM reserva WHERE id = ?", id);
    }

    /** RN02 — quantidade de reservas ATIVAS do receptor. */
    public int contarAtivasPorReceptor(UUID receptorId) {
        try (Connection con = ConexaoDB.conectar();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM reserva WHERE receptor_id = ? AND status = ?")) {
            ps.setObject(1, receptorId);
            ps.setString(2, StatusReserva.ATIVA.name());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao contar reservas ativas: " + e.getMessage(), e);
        }
    }

    /** READ — reservas de um receptor. */
    public List<Reserva> listarPorReceptor(UUID receptorId) {
        return listarReservas("SELECT * FROM reserva WHERE receptor_id = ? ORDER BY data_reserva DESC", receptorId);
    }

    /** READ — reservas ATIVAS ainda sem voluntário (solicitações de retirada, RF05). */
    public List<Reserva> listarSolicitacoes() {
        String sql = "SELECT r.* FROM reserva r LEFT JOIN retirada t ON t.reserva_id = r.id "
                + "WHERE r.status = ? AND t.id IS NULL ORDER BY r.data_reserva";
        List<Reserva> lista = new ArrayList<>();
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, StatusReserva.ATIVA.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearReserva(rs));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao listar solicitações: " + e.getMessage(), e);
        }
        return lista;
    }

    /* ----------------------------- RETIRADA ----------------------------- */

    /** CREATE */
    public void salvar(Retirada t) {
        String sql = "INSERT INTO retirada (id, reserva_id, voluntario_id, data_coleta, data_entrega, status) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, t.getId());
            ps.setObject(2, t.getReserva().getId());
            ps.setObject(3, t.getVoluntario().getId());
            ps.setTimestamp(4, t.getDataColeta() == null ? null : new Timestamp(t.getDataColeta().getTime()));
            ps.setTimestamp(5, t.getDataEntrega() == null ? null : new Timestamp(t.getDataEntrega().getTime()));
            ps.setString(6, t.getStatus().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao salvar retirada: " + e.getMessage(), e);
        }
    }

    /** UPDATE (coleta / entrega). */
    public void atualizar(Retirada t) {
        String sql = "UPDATE retirada SET data_coleta = ?, data_entrega = ?, status = ? WHERE id = ?";
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, t.getDataColeta() == null ? null : new Timestamp(t.getDataColeta().getTime()));
            ps.setTimestamp(2, t.getDataEntrega() == null ? null : new Timestamp(t.getDataEntrega().getTime()));
            ps.setString(3, t.getStatus().name());
            ps.setObject(4, t.getId());
            if (ps.executeUpdate() == 0) {
                throw new PersistenciaException("Retirada não encontrada para atualização.");
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao atualizar retirada: " + e.getMessage(), e);
        }
    }

    /** READ por id */
    public Optional<Retirada> buscarRetirada(UUID id) {
        return buscarRetiradaPor("SELECT * FROM retirada WHERE id = ?", id);
    }

    /** READ — retiradas de um voluntário. */
    public List<Retirada> listarPorVoluntario(UUID voluntarioId) {
        return listarRetiradas("SELECT * FROM retirada WHERE voluntario_id = ? ORDER BY id", voluntarioId);
    }

    /** READ — entregas concluídas (histórico do RF06). */
    public List<Retirada> listarEntregues() {
        List<Retirada> lista = new ArrayList<>();
        try (Connection con = ConexaoDB.conectar();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT * FROM retirada WHERE status = ? ORDER BY data_entrega DESC")) {
            ps.setString(1, StatusRetirada.ENTREGUE.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearRetirada(rs));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao listar entregas: " + e.getMessage(), e);
        }
        return lista;
    }

    /* ---------------------------- INDICADOR ---------------------------- */

    /** CREATE — um indicador por oferta concluída (multiplicidade 1:1). */
    public void salvar(IndicadorImpacto i) {
        String sql = "INSERT INTO indicador_impacto (id, oferta_id, peso_total_kg, co2_evitado_kg, data_calculo) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, i.getId());
            ps.setObject(2, i.getOferta().getId());
            ps.setDouble(3, i.getPesoTotalKg());
            ps.setDouble(4, i.getCo2EvitadoKg());
            ps.setTimestamp(5, new Timestamp(i.getDataCalculo().getTime()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao salvar indicador: " + e.getMessage(), e);
        }
    }

    /** Já existe indicador para esta oferta? */
    public boolean existeIndicador(UUID ofertaId) {
        try (Connection con = ConexaoDB.conectar();
             PreparedStatement ps = con.prepareStatement("SELECT 1 FROM indicador_impacto WHERE oferta_id = ?")) {
            ps.setObject(1, ofertaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao consultar indicador: " + e.getMessage(), e);
        }
    }

    /** Totais de peso reaproveitado e CO2 evitado (RF06 / RN05). */
    public IndicadorImpacto resumo() {
        try (Connection con = ConexaoDB.conectar();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COALESCE(SUM(peso_total_kg), 0) AS peso, COALESCE(SUM(co2_evitado_kg), 0) AS co2 "
                   + "FROM indicador_impacto");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return new IndicadorImpacto(null, rs.getFloat("peso"), rs.getFloat("co2"), null, null);
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao calcular indicadores: " + e.getMessage(), e);
        }
    }

    /* --------------------------- mapeamento --------------------------- */

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
                while (rs.next()) {
                    lista.add(mapearReserva(rs));
                }
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
        Usuario u = usuarios.buscarPorId(receptorId)
                .orElseThrow(() -> new PersistenciaException("Receptor da reserva não encontrado: " + receptorId));

        return new Reserva(UUID.fromString(rs.getString("id")),
                new Date(rs.getTimestamp("data_reserva").getTime()),
                StatusReserva.valueOf(rs.getString("status")),
                oferta, (Receptor) u);
    }

    private Optional<Retirada> buscarRetiradaPor(String sql, Object valor) {
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, valor);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapearRetirada(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao buscar retirada: " + e.getMessage(), e);
        }
    }

    private List<Retirada> listarRetiradas(String sql, Object valor) {
        List<Retirada> lista = new ArrayList<>();
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, valor);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearRetirada(rs));
                }
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
        Usuario u = usuarios.buscarPorId(voluntarioId)
                .orElseThrow(() -> new PersistenciaException("Voluntário não encontrado: " + voluntarioId));

        Timestamp coleta = rs.getTimestamp("data_coleta");
        Timestamp entrega = rs.getTimestamp("data_entrega");
        Retirada retirada = new Retirada(UUID.fromString(rs.getString("id")),
                coleta == null ? null : new Date(coleta.getTime()),
                entrega == null ? null : new Date(entrega.getTime()),
                StatusRetirada.valueOf(rs.getString("status")),
                reserva, (Voluntario) u);
        reserva.vincularRetirada(retirada);
        return retirada;
    }
}
