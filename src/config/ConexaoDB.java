package config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Conexão com o PostgreSQL.
 * Ordem de precedência: variáveis de ambiente, db.properties local e valores padrão.
 */
public final class ConexaoDB {

    private static final Properties PROPS = carregar();

    private ConexaoDB() { }

    /** Abre uma conexão nova com o PostgreSQL. */
    public static Connection conectar() throws SQLException {
        return DriverManager.getConnection(url(), usuario(), senha());
    }

    public static String url() {
        return System.getenv().getOrDefault("DB_URL", PROPS.getProperty("db.url", "jdbc:postgresql://localhost:5432/foodlink"));
    }

    public static String usuario() {
        return System.getenv().getOrDefault("DB_USER", PROPS.getProperty("db.user", "postgres"));
    }

    public static String senha() {
        return System.getenv().getOrDefault("DB_PASSWORD", PROPS.getProperty("db.password", ""));
    }

    private static Properties carregar() {
        Properties p = new Properties();
        Path local = Paths.get("db.properties");
        if (Files.exists(local)) {
            try (InputStream in = Files.newInputStream(local)) {
                p.load(in);
            } catch (Exception e) {
                throw new IllegalStateException("Não foi possível ler db.properties local.", e);
            }
        }
        return p;
    }
}
