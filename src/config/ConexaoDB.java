package config;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Conexão com o PostgreSQL.
 * Ordem de precedência: variáveis de ambiente (DB_URL, DB_USER, DB_PASSWORD)
 * > arquivo src/db.properties > valores padrão locais.
 */
public final class ConexaoDB {

    /** Permite trocar a origem das conexões (os testes usam H2 em memória). */
    public interface Fonte {
        Connection abrir() throws SQLException;
    }

    private static final Properties PROPS = carregar();
    private static Fonte fonte = ConexaoDB::padrao;

    private ConexaoDB() { }

    public static Connection conectar() throws SQLException {
        return fonte.abrir();
    }

    /** Usado pelos testes automatizados. */
    public static void usar(Fonte nova) {
        fonte = nova;
    }

    public static String url() {
        return System.getenv().getOrDefault("DB_URL", PROPS.getProperty("db.url", "jdbc:postgresql://localhost:5432/foodlink"));
    }

    public static String usuario() {
        return System.getenv().getOrDefault("DB_USER", PROPS.getProperty("db.user", "postgres"));
    }

    public static String senha() {
        return System.getenv().getOrDefault("DB_PASSWORD", PROPS.getProperty("db.password", "postgres"));
    }

    private static Connection padrao() throws SQLException {
        return DriverManager.getConnection(url(), usuario(), senha());
    }

    private static Properties carregar() {
        Properties p = new Properties();
        try (InputStream in = ConexaoDB.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception e) {
            System.err.println("Aviso: não foi possível ler db.properties (" + e.getMessage() + ")");
        }
        return p;
    }
}
