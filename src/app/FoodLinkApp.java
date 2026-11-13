package app;

import config.ConexaoDB;
import view.MenuConsole;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Ponto de entrada do FoodLink.
 * Arquitetura em camadas: View -> Controller -> Service -> Repository -> PostgreSQL.
 */
public class FoodLinkApp {

    public static void main(String[] args) {
        try (Connection con = ConexaoDB.conectar(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT to_regclass('usuario') IS NOT NULL "
                     + "AND to_regclass('endereco') IS NOT NULL AND to_regclass('oferta_doacao') IS NOT NULL "
                     + "AND to_regclass('reserva') IS NOT NULL AND to_regclass('retirada') IS NOT NULL "
                     + "AND to_regclass('indicador_impacto') IS NOT NULL")) {
            if (!rs.next() || !rs.getBoolean(1)) {
                throw new IllegalStateException("O banco ainda não contém todas as tabelas do FoodLink.");
            }
            System.out.println("Conectado ao banco: " + con.getMetaData().getURL());
        } catch (Exception e) {
            System.err.println("Não foi possível conectar ao banco de dados ou conferir suas tabelas.");
            System.err.println("URL: " + ConexaoDB.url());
            System.err.println("Detalhe: " + e.getMessage());
            System.err.println("\nConfira se o PostgreSQL está ligado e execute database/foodlink.sql.");
            System.err.println("A senha fica vazia por padrão; o PostgreSQL precisa permitir esse acesso.");
            System.err.println("Veja no README como ajustar a conexão.");
            System.exit(1);
        }
        new MenuConsole().iniciar();
    }
}
