package repository;

import config.ConexaoDB;
import model.Doador;
import model.Endereco;
import model.OfertaDoacao;
import model.enums.StatusOferta;

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
 * CRUD da entidade principal (OfertaDoacao) + busca por raio (RF03).
 * O endereço da oferta é gravado/atualizado junto (relação 1:1).
 */
public class OfertaRepository {

    /** Distância em km entre o ponto informado e o endereço (fórmula de Haversine em SQL). */
    private static final String DISTANCIA =
            "(6371 * 2 * ASIN(SQRT(POWER(SIN(RADIANS(? - e.latitude) / 2), 2) "
          + "+ COS(RADIANS(?)) * COS(RADIANS(e.latitude)) * POWER(SIN(RADIANS(? - e.longitude) / 2), 2))))";

    private static final String SELECT =
            "SELECT o.id, o.alimento, o.quantidade, o.peso, o.data_validade, o.observacoes, o.status, "
          + "       d.id AS doador_id, d.nome AS doador_nome, d.email AS doador_email, "
          + "       d.documento AS doador_documento, d.senha_hash AS doador_senha, d.data_cadastro AS doador_data, "
          + "       e.id AS end_id, e.rua, e.cidade, e.cep, e.latitude, e.longitude "
          + "FROM oferta_doacao o "
          + "JOIN usuario d ON d.id = o.doador_id "
          + "JOIN endereco e ON e.id = o.endereco_id ";

    /** CREATE — grava o endereço e depois a oferta. */
    public void salvar(OfertaDoacao o) {
        String sqlEndereco = "INSERT INTO endereco (id, rua, cidade, cep, latitude, longitude) VALUES (?, ?, ?, ?, ?, ?)";
        String sqlOferta = "INSERT INTO oferta_doacao (id, doador_id, endereco_id, alimento, quantidade, peso, "
                + "data_validade, observacoes, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = ConexaoDB.conectar()) {
            try (PreparedStatement ps = con.prepareStatement(sqlEndereco)) {
                Endereco e = o.getEndereco();
                ps.setObject(1, e.getId());
                ps.setString(2, e.getRua());
                ps.setString(3, e.getCidade());
                ps.setString(4, e.getCep());
                ps.setDouble(5, e.getLatitude());
                ps.setDouble(6, e.getLongitude());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(sqlOferta)) {
                ps.setObject(1, o.getId());
                ps.setObject(2, o.getDoador().getId());
                ps.setObject(3, o.getEndereco().getId());
                ps.setString(4, o.getAlimento());
                ps.setDouble(5, o.getQuantidade());
                ps.setDouble(6, o.getPeso());
                ps.setTimestamp(7, new Timestamp(o.getDataValidade().getTime()));
                ps.setString(8, o.getObservacoes());
                ps.setString(9, o.getStatus().name());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao salvar oferta: " + e.getMessage(), e);
        }
    }

    /** UPDATE dos dados da oferta. */
    public void atualizar(OfertaDoacao o) {
        String sql = "UPDATE oferta_doacao SET alimento = ?, quantidade = ?, peso = ?, data_validade = ?, "
                + "observacoes = ?, status = ? WHERE id = ?";
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, o.getAlimento());
            ps.setDouble(2, o.getQuantidade());
            ps.setDouble(3, o.getPeso());
            ps.setTimestamp(4, new Timestamp(o.getDataValidade().getTime()));
            ps.setString(5, o.getObservacoes());
            ps.setString(6, o.getStatus().name());
            ps.setObject(7, o.getId());
            if (ps.executeUpdate() == 0) {
                throw new PersistenciaException("Oferta não encontrada para atualização.");
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao atualizar oferta: " + e.getMessage(), e);
        }
    }

    /** UPDATE apenas do status (usado pela reserva e pela entrega). */
    public void atualizarStatus(UUID ofertaId, StatusOferta status) {
        try (Connection con = ConexaoDB.conectar();
             PreparedStatement ps = con.prepareStatement("UPDATE oferta_doacao SET status = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setObject(2, ofertaId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao atualizar status da oferta: " + e.getMessage(), e);
        }
    }

    /** DELETE */
    public void excluir(UUID id) {
        try (Connection con = ConexaoDB.conectar();
             PreparedStatement ps = con.prepareStatement("DELETE FROM oferta_doacao WHERE id = ?")) {
            ps.setObject(1, id);
            if (ps.executeUpdate() == 0) {
                throw new PersistenciaException("Oferta não encontrada para exclusão.");
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao excluir oferta: " + e.getMessage(), e);
        }
    }

    /** READ por id */
    public Optional<OfertaDoacao> buscarPorId(UUID id) {
        try (Connection con = ConexaoDB.conectar();
             PreparedStatement ps = con.prepareStatement(SELECT + "WHERE o.id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao buscar oferta: " + e.getMessage(), e);
        }
    }

    /** Descobre o id completo a partir dos primeiros caracteres (usado no menu). */
    public Optional<UUID> idPorPrefixo(String prefixo) {
        String sql = "SELECT id FROM oferta_doacao WHERE CAST(id AS VARCHAR(36)) LIKE ? ORDER BY id";
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, prefixo.trim() + "%");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao buscar id da oferta: " + e.getMessage(), e);
        }
    }

    /** READ — ofertas com um status (DISPONIVEL no menu do receptor). */
    public List<OfertaDoacao> listarPorStatus(StatusOferta status) {
        return listar(SELECT + "WHERE o.status = ? ORDER BY o.data_validade", status.name());
    }

    /** READ — ofertas de um doador (menu do doador). */
    public List<OfertaDoacao> listarPorDoador(UUID doadorId) {
        return listar(SELECT + "WHERE o.doador_id = ? ORDER BY o.data_validade", doadorId);
    }

    /** READ — busca por localização e raio de distância (RF03). */
    public List<OfertaDoacao> buscarPorRaio(double latitude, double longitude, double raioKm) {
        String sql = "SELECT * FROM ("
                + "  SELECT o.id, o.alimento, o.quantidade, o.peso, o.data_validade, o.observacoes, o.status, "
                + "         d.id AS doador_id, d.nome AS doador_nome, d.email AS doador_email, "
                + "         d.documento AS doador_documento, d.senha_hash AS doador_senha, "
                + "         d.data_cadastro AS doador_data, "
                + "         e.id AS end_id, e.rua, e.cidade, e.cep, e.latitude, e.longitude, "
                + DISTANCIA + " AS distancia_km "
                + "  FROM oferta_doacao o "
                + "  JOIN usuario d ON d.id = o.doador_id "
                + "  JOIN endereco e ON e.id = o.endereco_id "
                + "  WHERE o.status = ?"
                + ") t WHERE t.distancia_km <= ? ORDER BY t.distancia_km";
        List<OfertaDoacao> lista = new ArrayList<>();
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, latitude);
            ps.setDouble(2, latitude);
            ps.setDouble(3, longitude);
            ps.setString(4, StatusOferta.DISPONIVEL.name());
            ps.setDouble(5, raioKm);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OfertaDoacao o = mapear(rs);
                    o.setDistanciaKm(rs.getDouble("distancia_km"));
                    lista.add(o);
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro na busca por raio: " + e.getMessage(), e);
        }
        return lista;
    }

    private List<OfertaDoacao> listar(String sql, Object parametro) {
        List<OfertaDoacao> lista = new ArrayList<>();
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, parametro);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao listar ofertas: " + e.getMessage(), e);
        }
        return lista;
    }

    private OfertaDoacao mapear(ResultSet rs) throws SQLException {
        Doador doador = new Doador(
                UUID.fromString(rs.getString("doador_id")),
                rs.getString("doador_nome"),
                rs.getString("doador_email"),
                rs.getString("doador_documento"),
                rs.getString("doador_senha"),
                new Date(rs.getTimestamp("doador_data").getTime()));

        Endereco endereco = new Endereco(
                UUID.fromString(rs.getString("end_id")),
                rs.getString("rua"),
                rs.getString("cidade"),
                rs.getString("cep"),
                rs.getFloat("latitude"),
                rs.getFloat("longitude"));

        return new OfertaDoacao(
                UUID.fromString(rs.getString("id")),
                doador,
                endereco,
                rs.getString("alimento"),
                rs.getFloat("quantidade"),
                rs.getFloat("peso"),
                new Date(rs.getTimestamp("data_validade").getTime()),
                rs.getString("observacoes"),
                StatusOferta.valueOf(rs.getString("status")));
    }
}
