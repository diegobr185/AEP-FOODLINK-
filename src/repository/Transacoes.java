package repository;

import config.ConexaoDB;

import java.sql.Connection;
import java.sql.SQLException;

/** Executa uma operação inteira na mesma conexão, com commit ou rollback. */
final class Transacoes {

    private Transacoes() { }

    @FunctionalInterface
    interface Operacao<T> {
        T executar(Connection conexao) throws SQLException;
    }

    static <T> T executar(String mensagem, Operacao<T> operacao) {
        try (Connection conexao = ConexaoDB.conectar()) {
            conexao.setAutoCommit(false);
            try {
                T resultado = operacao.executar(conexao);
                conexao.commit();
                return resultado;
            } catch (SQLException | RuntimeException erro) {
                try {
                    conexao.rollback();
                } catch (SQLException erroRollback) {
                    erro.addSuppressed(erroRollback);
                }
                throw erro;
            }
        } catch (SQLException erro) {
            throw new PersistenciaException(mensagem + ": " + erro.getMessage(), erro);
        }
    }
}
