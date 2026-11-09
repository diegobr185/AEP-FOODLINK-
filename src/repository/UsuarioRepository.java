package repository;

import config.ConexaoDB;
import model.Doador;
import model.Receptor;
import model.Usuario;
import model.Voluntario;

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

/** CRUD de usuário na tabela única "usuario" (herança com discriminador "tipo"). */
public class UsuarioRepository {

    private static final String SELECT =
            "SELECT id, tipo, nome, email, documento, senha_hash, data_cadastro, limite_reservas_ativas FROM usuario ";

    /** CREATE */
    public void salvar(Usuario u) {
        String sql = "INSERT INTO usuario (id, tipo, nome, email, documento, senha_hash, data_cadastro, "
                + "limite_reservas_ativas) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, u.getId());
            ps.setString(2, u.getTipo());
            ps.setString(3, u.getNome());
            ps.setString(4, u.getEmail());
            ps.setString(5, u.getDocumento());
            ps.setString(6, u.getSenhaHash());
            ps.setTimestamp(7, new Timestamp(u.getDataCadastro().getTime()));
            if (u instanceof Receptor) {
                ps.setInt(8, ((Receptor) u).getLimiteReservasAtivas());
            } else {
                ps.setNull(8, java.sql.Types.INTEGER);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao salvar usuário: " + e.getMessage(), e);
        }
    }

    /** READ por id */
    public Optional<Usuario> buscarPorId(UUID id) {
        return buscarUm(SELECT + "WHERE id = ?", id);
    }

    /** READ por e-mail (login) */
    public Optional<Usuario> buscarPorEmail(String email) {
        return buscarUm(SELECT + "WHERE email = ?", email);
    }

    /** READ — todos os usuários */
    public List<Usuario> listar() {
        List<Usuario> lista = new ArrayList<>();
        try (Connection con = ConexaoDB.conectar();
             PreparedStatement ps = con.prepareStatement(SELECT + "ORDER BY nome");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao listar usuários: " + e.getMessage(), e);
        }
        return lista;
    }

    /** UPDATE */
    public void atualizar(Usuario u) {
        String sql = "UPDATE usuario SET nome = ?, email = ?, senha_hash = ? WHERE id = ?";
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, u.getNome());
            ps.setString(2, u.getEmail());
            ps.setString(3, u.getSenhaHash());
            ps.setObject(4, u.getId());
            if (ps.executeUpdate() == 0) {
                throw new PersistenciaException("Usuário não encontrado para atualização.");
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao atualizar usuário: " + e.getMessage(), e);
        }
    }

    /** DELETE */
    public void excluir(UUID id) {
        try (Connection con = ConexaoDB.conectar();
             PreparedStatement ps = con.prepareStatement("DELETE FROM usuario WHERE id = ?")) {
            ps.setObject(1, id);
            if (ps.executeUpdate() == 0) {
                throw new PersistenciaException("Usuário não encontrado para exclusão.");
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao excluir usuário: " + e.getMessage(), e);
        }
    }

    private Optional<Usuario> buscarUm(String sql, Object valor) {
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, valor);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao buscar usuário: " + e.getMessage(), e);
        }
    }

    private Usuario mapear(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        String nome = rs.getString("nome");
        String email = rs.getString("email");
        String documento = rs.getString("documento");
        String senha = rs.getString("senha_hash");
        Date data = new Date(rs.getTimestamp("data_cadastro").getTime());

        switch (rs.getString("tipo")) {
            case "DOADOR":     return new Doador(id, nome, email, documento, senha, data);
            case "RECEPTOR":   return new Receptor(id, nome, email, documento, senha, data,
                                                   rs.getInt("limite_reservas_ativas"));
            default:           return new Voluntario(id, nome, email, documento, senha, data);
        }
    }
}
