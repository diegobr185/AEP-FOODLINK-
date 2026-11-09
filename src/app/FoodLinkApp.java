package app;

import config.ConexaoDB;
import view.MenuConsole;

import java.sql.Connection;

/**
 * Ponto de entrada do FoodLink.
 * Arquitetura em camadas: View -> Controller -> Service -> Repository -> PostgreSQL.
 */
public class FoodLinkApp {

    public static void main(String[] args) {
        try (Connection con = ConexaoDB.conectar()) {
            System.out.println("Conectado ao banco: " + con.getMetaData().getURL());
        } catch (Exception e) {
            System.err.println("Não foi possível conectar ao banco de dados.");
            System.err.println("URL: " + ConexaoDB.url());
            System.err.println("Detalhe: " + e.getMessage());
            System.err.println("\nExecute o script database/foodlink.sql e ajuste src/db.properties"
                    + " (ou as variáveis DB_URL, DB_USER e DB_PASSWORD).");
            System.exit(1);
        }
        new MenuConsole().iniciar();
    }
}
