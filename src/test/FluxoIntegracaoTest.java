import config.ConexaoDB;
import model.Doador;
import model.Endereco;
import model.IndicadorImpacto;
import model.OfertaDoacao;
import model.Receptor;
import model.Reserva;
import model.Retirada;
import model.Usuario;
import model.Voluntario;
import model.enums.StatusOferta;
import model.enums.StatusReserva;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import service.OfertaService;
import service.ReservaService;
import service.UsuarioService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integração: executa o MESMO script do projeto (database/foodlink.sql)
 * e valida RF01 a RF06 com persistência real. Usa H2 em modo PostgreSQL para rodar
 * em qualquer máquina; em produção o SGBD é o PostgreSQL.
 */
class FluxoIntegracaoTest {

    private static final String URL =
            "jdbc:h2:mem:foodlink;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private final UsuarioService usuarioService = new UsuarioService();
    private final OfertaService ofertaService = new OfertaService();
    private final ReservaService reservaService = new ReservaService();

    @BeforeAll
    static void criarEsquema() throws Exception {
        ConexaoDB.usar(() -> DriverManager.getConnection(URL, "sa", ""));
        String script = new String(Files.readAllBytes(Path.of("database", "foodlink.sql")), StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        for (String linha : script.split("\n")) {
            if (!linha.trim().startsWith("--")) {
                sb.append(linha).append('\n');
            }
        }
        try (Connection con = ConexaoDB.conectar(); Statement st = con.createStatement()) {
            for (String comando : sb.toString().split(";")) {
                if (!comando.trim().isEmpty()) {
                    st.execute(comando);
                }
            }
        }
    }

    @BeforeEach
    void limparDados() throws Exception {
        try (Connection con = ConexaoDB.conectar(); Statement st = con.createStatement()) {
            st.execute("DELETE FROM indicador_impacto");
            st.execute("DELETE FROM retirada");
            st.execute("DELETE FROM reserva");
            st.execute("DELETE FROM oferta_doacao");
            st.execute("DELETE FROM usuario");
            st.execute("DELETE FROM endereco");
        }
    }

    private static Date emDias(int dias) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, dias);
        return c.getTime();
    }

    private Endereco endereco() {
        return new Endereco("Rua das Flores, 100", "Cianorte", "87200-000", -23.6636f, -52.6053f);
    }

    @Test
    @DisplayName("RF01 - cadastro, login e manutencao dos tres tipos de usuario")
    void rf01Usuarios() {
        Doador doador = usuarioService.cadastrarDoador("Padaria", "padaria@x.com", "111", "1234");
        Receptor receptor = usuarioService.cadastrarReceptor("ONG", "ong@x.com", "222", "1234");
        usuarioService.cadastrarVoluntario("Ana", "ana@x.com", "333", "1234");

        assertEquals(3, usuarioService.listar().size());
        assertTrue(usuarioService.autenticar("padaria@x.com", "1234").isPresent());
        assertFalse(usuarioService.autenticar("padaria@x.com", "errada").isPresent());
        assertTrue(usuarioService.autenticar("ong@x.com", "1234").get() instanceof Receptor);

        // UPDATE e DELETE
        usuarioService.atualizarPerfil(receptor.getId(), "ONG Mesa Cheia", null, null);
        assertEquals("ONG Mesa Cheia", usuarioService.buscarPorId(receptor.getId()).getNome());
        usuarioService.excluir(receptor.getId());
        assertThrows(IllegalArgumentException.class, () -> usuarioService.buscarPorId(receptor.getId()));
        assertNotNull(doador.getId());
    }

    @Test
    @DisplayName("RF02 - doador cadastra oferta e ela e persistida")
    void rf02CadastroDeOferta() {
        Doador doador = usuarioService.cadastrarDoador("Padaria", "p@x.com", "111", "1234");
        OfertaDoacao oferta = ofertaService.cadastrar(doador, endereco(), "Pães", 20f, 8.5f, emDias(2), "Até 18h");

        OfertaDoacao persistida = ofertaService.buscarPorId(oferta.getId());
        assertEquals("Pães", persistida.getAlimento());
        assertEquals(8.5f, persistida.getPeso(), 0.001f);
        assertEquals(StatusOferta.DISPONIVEL, persistida.getStatus());
        assertEquals("Padaria", persistida.getDoador().getNome());
    }

    @Test
    @DisplayName("RN01 - oferta com menos de 2h de validade e recusada")
    void rn01NaPublicacao() {
        Doador doador = usuarioService.cadastrarDoador("Padaria", "p@x.com", "111", "1234");
        Date umaHora = new Date(System.currentTimeMillis() + 60L * 60 * 1000);
        assertThrows(IllegalStateException.class,
                () -> ofertaService.cadastrar(doador, endereco(), "Iogurte", 5f, 2f, umaHora, null));
    }

    @Test
    @DisplayName("RF03 - consulta de ofertas por localizacao e raio")
    void rf03BuscaPorRaio() {
        Doador doador = usuarioService.cadastrarDoador("Padaria", "p@x.com", "111", "1234");
        ofertaService.cadastrar(doador, endereco(), "Pães", 20f, 8.5f, emDias(2), null);
        Endereco longe = new Endereco("Rodovia, km 100", "Maringá", "87000-000", -23.4205f, -51.9331f);
        ofertaService.cadastrar(doador, longe, "Frutas", 10f, 12f, emDias(3), null);

        List<OfertaDoacao> perto = ofertaService.buscarPorRaio(-23.6636, -52.6053, 10);
        assertEquals(1, perto.size());
        assertEquals("Pães", perto.get(0).getAlimento());
        assertTrue(perto.get(0).getDistanciaKm() >= 0);

        assertEquals(2, ofertaService.buscarPorRaio(-23.6636, -52.6053, 200).size());
    }

    @Test
    @DisplayName("RF04 + RN02 + RN03 - reserva, exclusividade e limite de 3 ativas")
    void rf04Reservas() {
        Doador doador = usuarioService.cadastrarDoador("Padaria", "p@x.com", "111", "1234");
        Receptor receptor = usuarioService.cadastrarReceptor("ONG", "ong@x.com", "222", "1234");
        Receptor outro = usuarioService.cadastrarReceptor("Lar", "lar@x.com", "444", "1234");

        OfertaDoacao oferta = ofertaService.cadastrar(doador, endereco(), "Pães", 20f, 8.5f, emDias(2), null);
        Reserva reserva = reservaService.reservar(receptor, oferta.getId());

        assertEquals(1, reservaService.reservasAtivas(receptor.getId()));
        assertEquals(StatusOferta.RESERVADA, ofertaService.buscarPorId(oferta.getId()).getStatus());

        // RN03 - outro receptor nao pode reservar a mesma oferta
        assertThrows(IllegalStateException.class, () -> reservaService.reservar(outro, oferta.getId()));

        // RN02 - limite de 3 reservas ativas
        for (int i = 0; i < 2; i++) {
            OfertaDoacao o = ofertaService.cadastrar(doador, endereco(), "Oferta " + i, 1f, 1f, emDias(2), null);
            reservaService.reservar(receptor, o.getId());
        }
        OfertaDoacao excedente = ofertaService.cadastrar(doador, endereco(), "Excedente", 1f, 1f, emDias(2), null);
        assertThrows(IllegalStateException.class, () -> reservaService.reservar(receptor, excedente.getId()));

        // cancelar devolve a cota e libera a oferta
        reservaService.cancelar(reserva.getId());
        assertEquals(2, reservaService.reservasAtivas(receptor.getId()));
        assertEquals(StatusOferta.DISPONIVEL, ofertaService.buscarPorId(oferta.getId()).getStatus());
        assertDoesNotThrow(() -> reservaService.reservar(receptor, excedente.getId()));
    }

    @Test
    @DisplayName("RF05 + RF06 - retirada, entrega, historico e indicador de impacto")
    void rf05eRf06FluxoCompleto() {
        Doador doador = usuarioService.cadastrarDoador("Padaria", "p@x.com", "111", "1234");
        Receptor receptor = usuarioService.cadastrarReceptor("ONG", "ong@x.com", "222", "1234");
        Voluntario voluntario = usuarioService.cadastrarVoluntario("Ana", "ana@x.com", "333", "1234");

        OfertaDoacao oferta = ofertaService.cadastrar(doador, endereco(), "Pães", 20f, 8.5f, emDias(2), null);
        Reserva reserva = reservaService.reservar(receptor, oferta.getId());

        // RF05 - solicitacoes e assuncao da retirada
        assertEquals(1, reservaService.solicitacoes().size());
        Retirada retirada = reservaService.assumir(voluntario, reserva.getId());
        assertEquals(0, reservaService.solicitacoes().size(), "reserva assumida nao aparece mais");

        reservaService.registrarColeta(retirada.getId());
        reservaService.registrarEntrega(retirada.getId());

        assertEquals(StatusOferta.CONCLUIDA, ofertaService.buscarPorId(oferta.getId()).getStatus());
        assertEquals(StatusReserva.CONCLUIDA, reservaService.buscarPorId(reserva.getId()).getStatus());

        // RF06 - historico e indicadores (8,5 kg x 2,5 = 21,25 kg de CO2)
        assertEquals(1, reservaService.historico().size());
        IndicadorImpacto resumo = reservaService.resumoImpacto();
        assertEquals(8.5f, resumo.getPesoTotalKg(), 0.01f);
        assertEquals(21.25f, resumo.getCo2EvitadoKg(), 0.01f);
        assertEquals(1, reservaService.minhasRetiradas(voluntario.getId()).size());
    }

    @Test
    @DisplayName("CRUD completo da entidade principal (OfertaDoacao)")
    void crudOferta() {
        Doador doador = usuarioService.cadastrarDoador("Padaria", "p@x.com", "111", "1234");
        OfertaDoacao oferta = ofertaService.cadastrar(doador, endereco(), "Pães", 20f, 8.5f, emDias(2), null);

        // READ
        assertEquals(1, ofertaService.listarDisponiveis().size());
        assertEquals(1, ofertaService.listarPorDoador(doador.getId()).size());

        // UPDATE
        ofertaService.atualizar(oferta.getId(), "Pães integrais", 25f, 9f, emDias(3), "Atualizado");
        OfertaDoacao atualizada = ofertaService.buscarPorId(oferta.getId());
        assertEquals("Pães integrais", atualizada.getAlimento());
        assertEquals(25f, atualizada.getQuantidade(), 0.001f);
        assertEquals("Atualizado", atualizada.getObservacoes());

        // DELETE
        ofertaService.excluir(oferta.getId());
        assertTrue(ofertaService.listarDisponiveis().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ofertaService.buscarPorId(oferta.getId()));
    }
}
