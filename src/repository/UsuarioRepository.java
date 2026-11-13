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
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/** Cadastro e consulta dos usuários na tabela usuario. */
public class UsuarioRepository {

    private static final String SELECT =
            "SELECT id, tipo, nome, email, documento, senha_hash, data_cadastro, "
          + "COALESCE(limite_reservas_ativas, 3) AS limite_reservas_ativas FROM usuario ";

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
            if ("23505".equals(e.getSQLState())) {
                throw new IllegalArgumentException("Já existe usuário com este e-mail ou documento.", e);
            }
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

    /** Atualiza o hash antigo sem sobrescrever uma senha alterada simultaneamente. */
    public boolean atualizarHashAntigo(UUID id, String antigo, String novo) {
        try (Connection con = ConexaoDB.conectar();
             PreparedStatement ps = con.prepareStatement("UPDATE usuario SET senha_hash = ? WHERE id = ? AND senha_hash = ?")) {
            ps.setString(1, novo);
            ps.setObject(2, id);
            ps.setString(3, antigo);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao atualizar segurança da senha: " + e.getMessage(), e);
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
