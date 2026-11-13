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

/** Consultas e gravações das ofertas. O endereço é salvo junto com a oferta. */
public class OfertaRepository {

    /** Distância em km (Haversine); LEAST/GREATEST evitam erros de arredondamento. */
    private static final String DISTANCIA =
            "(6371 * 2 * ASIN(SQRT(LEAST(1.0, GREATEST(0.0, "
          + "POWER(SIN(RADIANS(? - e.latitude) / 2), 2) "
          + "+ COS(RADIANS(?)) * COS(RADIANS(e.latitude)) * POWER(SIN(RADIANS(? - e.longitude) / 2), 2))))))";

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
        Transacoes.executar("Erro ao salvar oferta", con -> {
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
                ps.setObject(5, o.getQuantidade() == 0 ? null : o.getQuantidade());
                ps.setObject(6, o.getPeso() == 0 ? null : o.getPeso());
                ps.setTimestamp(7, new Timestamp(o.getDataValidade().getTime()));
                ps.setString(8, o.getObservacoes());
                ps.setString(9, o.getStatus().name());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Só atualiza oferta disponível do próprio doador, inclusive sob concorrência. */
    public void atualizar(OfertaDoacao o) {
        String sql = "UPDATE oferta_doacao SET alimento = ?, quantidade = ?, peso = ?, data_validade = ?, "
                + "observacoes = ? WHERE id = ? AND doador_id = ? AND status = 'DISPONIVEL'";
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, o.getAlimento());
            ps.setObject(2, o.getQuantidade() == 0 ? null : o.getQuantidade());
            ps.setObject(3, o.getPeso() == 0 ? null : o.getPeso());
            ps.setTimestamp(4, new Timestamp(o.getDataValidade().getTime()));
            ps.setString(5, o.getObservacoes());
            ps.setObject(6, o.getId());
            ps.setObject(7, o.getDoador().getId());
            if (ps.executeUpdate() == 0) {
                throw new IllegalStateException("A oferta não está mais disponível para edição.");
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao atualizar oferta: " + e.getMessage(), e);
        }
    }

    /** Não exclui ofertas com reservas: o histórico deve ser preservado. */
    public void excluir(UUID id, UUID doadorId) {
        String sql = "DELETE FROM oferta_doacao o WHERE o.id = ? AND o.doador_id = ? "
                + "AND o.status IN ('DISPONIVEL', 'EXPIRADA') "
                + "AND NOT EXISTS (SELECT 1 FROM reserva r WHERE r.oferta_id = o.id) RETURNING o.endereco_id";
        Transacoes.executar("Erro ao excluir oferta", con -> {
            UUID enderecoId;
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setObject(2, doadorId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("A oferta está reservada, concluída ou possui histórico.");
                    }
                    enderecoId = (UUID) rs.getObject(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM endereco e WHERE e.id = ? "
                  + "AND NOT EXISTS (SELECT 1 FROM oferta_doacao o WHERE o.endereco_id = e.id)")) {
                ps.setObject(1, enderecoId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Atualiza o estado de ofertas vencidas quando são consultadas. */
    private void expirarDisponiveis(UUID id) {
        String sql = "UPDATE oferta_doacao SET status = 'EXPIRADA' "
                + "WHERE status = 'DISPONIVEL' AND data_validade <= CURRENT_TIMESTAMP"
                + (id == null ? "" : " AND id = ?");
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            if (id != null) ps.setObject(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao verificar validade das ofertas: " + e.getMessage(), e);
        }
    }

    /** READ por id */
    public Optional<OfertaDoacao> buscarPorId(UUID id) {
        expirarDisponiveis(id);
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

    /** Após a publicação, ofertas disponíveis aparecem até a data de validade. */
    public List<OfertaDoacao> listarDisponiveis() {
        expirarDisponiveis(null);
        return listar(SELECT + "WHERE o.status = ? AND o.data_validade > CURRENT_TIMESTAMP "
                + "ORDER BY o.data_validade", StatusOferta.DISPONIVEL.name(), null);
    }

    /** READ — ofertas de um doador (menu do doador). */
    public List<OfertaDoacao> listarPorDoador(Doador doador) {
        expirarDisponiveis(null);
        return listar(SELECT + "WHERE o.doador_id = ? ORDER BY o.data_validade", doador.getId(), doador);
    }

    /** READ — busca por localização e raio de distância (RF03). */
    public List<OfertaDoacao> buscarPorRaio(double latitude, double longitude, double raioKm) {
        expirarDisponiveis(null);
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
                + "  WHERE o.status = ? AND o.data_validade > CURRENT_TIMESTAMP"
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

    private List<OfertaDoacao> listar(String sql, Object parametro, Doador doador) {
        List<OfertaDoacao> lista = new ArrayList<>();
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, parametro);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs, doador));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao listar ofertas: " + e.getMessage(), e);
        }
        return lista;
    }

    private OfertaDoacao mapear(ResultSet rs) throws SQLException {
        return mapear(rs, null);
    }

    private OfertaDoacao mapear(ResultSet rs, Doador doador) throws SQLException {
        if (doador == null) {
            doador = new Doador(
                    UUID.fromString(rs.getString("doador_id")),
                    rs.getString("doador_nome"),
                    rs.getString("doador_email"),
                    rs.getString("doador_documento"),
                    rs.getString("doador_senha"),
                    new Date(rs.getTimestamp("doador_data").getTime()));
        }

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
