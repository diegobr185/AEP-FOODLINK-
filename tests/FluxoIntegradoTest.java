import config.ConexaoDB;
import model.Doador;
import model.Endereco;
import model.OfertaDoacao;
import model.Receptor;
import model.Reserva;
import model.Retirada;
import model.Voluntario;
import model.enums.StatusRetirada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import repository.PersistenciaException;
import service.OfertaService;
import service.ReservaService;
import service.UsuarioService;
import view.MenuConsole;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes reais com PostgreSQL. Recriam o schema public: exigem opt-in e o banco foodlink_test.
 * Nunca aponte estes testes para dados reais.
 */
@EnabledIfEnvironmentVariable(named = "FOODLINK_TESTES_BANCO", matches = "1")
class FluxoIntegradoTest {

    private static final UUID OFERTA_1 = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID OFERTA_2 = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private final UsuarioService usuarios = new UsuarioService();
    private final OfertaService ofertas = new OfertaService();
    private final ReservaService reservas = new ReservaService();

    @BeforeEach
    void prepararBancoDeTeste() throws Exception {
        try (Connection con = ConexaoDB.conectar(); Statement st = con.createStatement()) {
            if (!"foodlink_test".equals(con.getCatalog())) {
                throw new IllegalStateException("Os testes destrutivos exigem o banco exclusivo foodlink_test.");
            }
            st.execute("DROP SCHEMA IF EXISTS public CASCADE");
            st.execute("CREATE SCHEMA public");
            st.execute(Files.readString(Paths.get("database/foodlink.sql"), StandardCharsets.UTF_8));
        }
    }

    private Doador doador() {
        return (Doador) usuarios.autenticar("padaria@foodlink.com", "1234").orElseThrow();
    }

    private Receptor receptor() {
        return (Receptor) usuarios.autenticar("ong@foodlink.com", "1234").orElseThrow();
    }

    private Voluntario voluntario() {
        return (Voluntario) usuarios.autenticar("ana@foodlink.com", "1234").orElseThrow();
    }

    @Test
    void fluxoCompletoPreservaHistoricoECalculaImpacto() throws Exception {
        Doador doador = doador();
        Receptor receptor = receptor();
        Voluntario voluntario = voluntario();
        assertEquals(2, ofertas.listarDisponiveis().size());
        assertEquals(2, ofertas.buscarPorRaio(-23.6636, -52.6053, 10).size());

        Reserva reserva = reservas.reservar(receptor, OFERTA_1);
        assertNull(reservas.minhasReservas(receptor.getId()).get(0).getStatusRetirada());
        assertThrows(IllegalStateException.class, () -> reservas.reservar(receptor, OFERTA_1));
        Retirada retirada = reservas.assumir(voluntario, reserva.getId());
        assertEquals(StatusRetirada.PENDENTE, reservas.minhasReservas(receptor.getId()).get(0).getStatusRetirada());
        assertThrows(IllegalStateException.class, () -> reservas.registrarEntrega(voluntario, retirada.getId()));
        reservas.registrarColeta(voluntario, retirada.getId());
        assertEquals(StatusRetirada.EM_ANDAMENTO, reservas.minhasReservas(receptor.getId()).get(0).getStatusRetirada());
        assertThrows(IllegalStateException.class, () -> reservas.cancelar(receptor, reserva.getId()));
        reservas.registrarEntrega(voluntario, retirada.getId());
        assertEquals(StatusRetirada.ENTREGUE, reservas.minhasReservas(receptor.getId()).get(0).getStatusRetirada());

        assertEquals("CONCLUIDA", valor("SELECT status FROM reserva WHERE id = ?", reserva.getId()));
        assertEquals("CONCLUIDA", valor("SELECT status FROM oferta_doacao WHERE id = ?", OFERTA_1));
        assertEquals("ENTREGUE", valor("SELECT status FROM retirada WHERE id = ?", retirada.getId()));
        assertEquals(1, reservas.historico().size());
        assertEquals(8.5f, reservas.resumoImpacto().getPesoTotalKg(), 0.001f);
        assertEquals(21.25f, reservas.resumoImpacto().getCo2EvitadoKg(), 0.001f);
        assertThrows(IllegalStateException.class, () -> ofertas.excluir(doador, OFERTA_1));
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(
                "DELETE FROM oferta_doacao WHERE id = ?")) {
            ps.setObject(1, OFERTA_1);
            assertThrows(SQLException.class, ps::executeUpdate);
        }

        // Rodar o SQL de novo não deve apagar a doação concluída nem o indicador.
        executarArquivoSql();
        assertEquals(1, reservas.historico().size());
        assertEquals(21.25f, reservas.resumoImpacto().getCo2EvitadoKg(), 0.001f);
    }

    @Test
    void migracaoDasChavesAntigasPreservaDoacoesEProtegeHistorico() throws Exception {
        Reserva reserva = reservas.reservar(receptor(), OFERTA_1);
        Voluntario voluntario = voluntario();
        Retirada retirada = reservas.assumir(voluntario, reserva.getId());
        reservas.registrarColeta(voluntario, retirada.getId());
        reservas.registrarEntrega(voluntario, retirada.getId());
        criarChavesAntigasComCascata();
        try (Connection con = ConexaoDB.conectar(); Statement st = con.createStatement()) {
            st.execute("DROP INDEX idx_reserva_ativa_por_oferta");
            st.execute("ALTER TABLE oferta_doacao ALTER COLUMN quantidade SET NOT NULL, ALTER COLUMN peso SET NOT NULL");
            st.execute("ALTER TABLE indicador_impacto ALTER COLUMN peso_total_kg SET NOT NULL, ALTER COLUMN co2_evitado_kg SET NOT NULL");
            st.execute("CREATE VIEW vw_historico_doacoes AS SELECT peso FROM oferta_doacao");
        }
        assertEquals(4, quantidade("SELECT COUNT(*) FROM pg_constraint WHERE conname IN "
                + "('oferta_doacao_doador_id_fkey', 'reserva_oferta_id_fkey', "
                + "'retirada_reserva_id_fkey', 'indicador_impacto_oferta_id_fkey') AND confdeltype = 'c'"));
        executarArquivoSql();
        assertEquals(0, quantidade("SELECT COUNT(*) FROM pg_constraint WHERE conname IN "
                + "('oferta_doacao_doador_id_fkey', 'reserva_oferta_id_fkey', "
                + "'retirada_reserva_id_fkey', 'indicador_impacto_oferta_id_fkey') AND confdeltype = 'c'"));
        assertEquals(1, reservas.historico().size());
        assertEquals(21.25f, reservas.resumoImpacto().getCo2EvitadoKg(), 0.001f);
        assertEquals(1, quantidade("SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_reserva_ativa_por_oferta'"));
        assertEquals(4, quantidade("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' "
                + "AND is_nullable = 'YES' AND ((table_name = 'oferta_doacao' AND column_name IN ('quantidade', 'peso')) "
                + "OR (table_name = 'indicador_impacto' AND column_name IN ('peso_total_kg', 'co2_evitado_kg')))"));
        assertThrows(IllegalStateException.class, () -> ofertas.excluir(doador(), OFERTA_1));
    }

    @Test
    void duplicatasAntigasInterrompemMigracaoSemApagarDados() throws Exception {
        try (Connection con = ConexaoDB.conectar(); Statement st = con.createStatement()) {
            st.execute("DROP INDEX idx_reserva_ativa_por_oferta");
            st.execute("ALTER TABLE reserva DROP CONSTRAINT reserva_oferta_id_fkey");
            st.execute("ALTER TABLE reserva ADD CONSTRAINT reserva_oferta_id_fkey "
                    + "FOREIGN KEY (oferta_id) REFERENCES oferta_doacao(id) ON DELETE CASCADE");
            st.execute("INSERT INTO reserva (id, oferta_id, receptor_id, data_reserva, status) "
                    + "VALUES ('a0000000-0000-0000-0000-000000000001', '66666666-6666-6666-6666-666666666666', "
                    + "'22222222-2222-2222-2222-222222222222', CURRENT_TIMESTAMP, 'ATIVA'), "
                    + "('a0000000-0000-0000-0000-000000000002', '66666666-6666-6666-6666-666666666666', "
                    + "'22222222-2222-2222-2222-222222222222', CURRENT_TIMESTAMP, 'ATIVA')");
        }
        SQLException erro = assertThrows(SQLException.class, this::executarArquivoSql);
        assertEquals("23505", erro.getSQLState());
        assertEquals(2, quantidade("SELECT COUNT(*) FROM reserva WHERE status = 'ATIVA'"));
        assertEquals(1, quantidade("SELECT COUNT(*) FROM pg_constraint WHERE conname = 'reserva_oferta_id_fkey' "
                + "AND confdeltype = 'c'"));
    }

    @Test
    void conflitosComDadosDeExemploNaoImpedemReexecucaoDoSql() throws Exception {
        try (Connection con = ConexaoDB.conectar(); Statement st = con.createStatement()) {
            st.execute("DELETE FROM oferta_doacao WHERE doador_id = '11111111-1111-1111-1111-111111111111'");
            st.execute("DELETE FROM usuario WHERE id = '11111111-1111-1111-1111-111111111111'");
            st.execute("INSERT INTO usuario (id, tipo, nome, email, documento, senha_hash, data_cadastro) VALUES "
                    + "('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'DOADOR', 'Conta existente', "
                    + "'padaria@foodlink.com', 'outro-doc', 'hash', CURRENT_TIMESTAMP)");
        }
        executarArquivoSql();
        assertEquals(1, quantidade("SELECT COUNT(*) FROM usuario WHERE email = 'padaria@foodlink.com'"));
        assertEquals(0, quantidade("SELECT COUNT(*) FROM oferta_doacao WHERE id IN "
                + "('66666666-6666-6666-6666-666666666666', '77777777-7777-7777-7777-777777777777')"));
    }

    @Test
    void doadorPodeCriarEditarEExcluirUmaOfertaSemHistorico() throws Exception {
        Doador doador = doador();
        int enderecosAntes = quantidade("SELECT COUNT(*) FROM endereco");
        UUID id = novaOferta(doador, "Arroz");
        assertEquals(3, ofertas.listarDisponiveis().size());
        assertEquals(3, ofertas.listarPorDoador(doador).size());
        OfertaDoacao naLista = doador.getOfertas().stream().filter(o -> o.getId().equals(id)).findFirst().orElseThrow();
        assertSame(doador, naLista.getDoador());
        ofertas.atualizar(doador, id, "Arroz integral", 4, 2, null, "Caixa fechada");
        assertEquals("Arroz integral", ofertas.buscarPorId(id).getAlimento());
        assertEquals(4, ofertas.buscarPorId(id).getQuantidade(), 0.001f);
        ofertas.excluir(doador, id);
        assertEquals(2, ofertas.listarPorDoador(doador).size());
        assertNull(valor("SELECT status FROM oferta_doacao WHERE id = ?", id));
        assertEquals(enderecosAntes, quantidade("SELECT COUNT(*) FROM endereco"));
    }

    @Test
    void aceitaQuantidadeOuPesoESoCalculaImpactoQuandoHaPeso() throws Exception {
        Doador doador = doador();
        Date validade = Date.from(Instant.now().plusSeconds(86400));
        Endereco endereco = new Endereco("Rua Teste", "Maringá", "87000-000", -23.42f, -51.93f);
        assertThrows(IllegalArgumentException.class, () -> ofertas.cadastrar(
                doador, endereco, "Arroz", 0, 0, validade, ""));

        OfertaDoacao quantidade = ofertas.cadastrar(doador, endereco, "Arroz", 5, 0, validade, "");
        OfertaDoacao peso = ofertas.cadastrar(doador,
                new Endereco("Rua Dois", "Maringá", "87000-000", -23.42f, -51.93f),
                "Feijão", 0, 2, validade, "");
        assertNull(valor("SELECT peso FROM oferta_doacao WHERE id = ?", quantidade.getId()));
        assertNull(valor("SELECT quantidade FROM oferta_doacao WHERE id = ?", peso.getId()));
        assertEquals(0, ofertas.buscarPorId(quantidade.getId()).getPeso());
        assertEquals(0, ofertas.buscarPorId(peso.getId()).getQuantidade());

        Receptor receptor = receptor();
        Voluntario voluntario = voluntario();
        Reserva primeira = reservas.reservar(receptor, quantidade.getId());
        Retirada primeiraRetirada = reservas.assumir(voluntario, primeira.getId());
        reservas.registrarColeta(voluntario, primeiraRetirada.getId());
        reservas.registrarEntrega(voluntario, primeiraRetirada.getId());
        assertNull(valor("SELECT co2_evitado_kg FROM indicador_impacto WHERE oferta_id = ?", quantidade.getId()));
        assertEquals(0, reservas.resumoImpacto().getCo2EvitadoKg());

        Reserva segunda = reservas.reservar(receptor, peso.getId());
        Retirada segundaRetirada = reservas.assumir(voluntario, segunda.getId());
        reservas.registrarColeta(voluntario, segundaRetirada.getId());
        reservas.registrarEntrega(voluntario, segundaRetirada.getId());
        assertEquals(2, reservas.historico().size());
        assertEquals(2f, reservas.resumoImpacto().getPesoTotalKg(), 0.001f);
        assertEquals(5f, reservas.resumoImpacto().getCo2EvitadoKg(), 0.001f);
    }

    @Test
    void relatorioMostraAsUnidadesInformadasMesmoSemPeso() throws Exception {
        Doador doador = doador();
        Date validade = Date.from(Instant.now().plusSeconds(86400));
        OfertaDoacao quantidade = ofertas.cadastrar(doador,
                new Endereco("Rua Um", "Maringá", "87000-000", -23.42f, -51.93f),
                "Arroz", 6, 0, validade, "");
        OfertaDoacao peso = ofertas.cadastrar(doador,
                new Endereco("Rua Dois", "Maringá", "87000-000", -23.42f, -51.93f),
                "Feijão", 0, 2, validade, "");
        Receptor receptor = receptor();
        Voluntario voluntario = voluntario();
        for (OfertaDoacao oferta : new OfertaDoacao[] {quantidade, peso}) {
            Reserva reserva = reservas.reservar(receptor, oferta.getId());
            Retirada retirada = reservas.assumir(voluntario, reserva.getId());
            reservas.registrarColeta(voluntario, retirada.getId());
            reservas.registrarEntrega(voluntario, retirada.getId());
        }

        InputStream entradaOriginal = System.in;
        PrintStream saidaOriginal = System.out;
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (PrintStream captura = new PrintStream(saida, true, StandardCharsets.UTF_8.name())) {
            System.setIn(new ByteArrayInputStream("4\n0\n".getBytes(StandardCharsets.UTF_8)));
            System.setOut(captura);
            new MenuConsole().iniciar();
        } finally {
            System.setIn(entradaOriginal);
            System.setOut(saidaOriginal);
        }
        String relatorio = saida.toString(StandardCharsets.UTF_8.name());
        assertTrue(relatorio.contains("Unidades informadas: " + String.format("%.2f", 6.0)));
        assertTrue(relatorio.contains("Peso informado: " + String.format("%.2f", 2.0)));
        assertTrue(relatorio.contains("CO2 estimado: " + String.format("%.2f", 5.0)));
        assertTrue(relatorio.contains("Quantidade parcial: 1 doação(ões) sem quantidade informada."));
        assertTrue(relatorio.contains("Estimativa parcial: 1 doação(ões) sem peso informado."));
    }

    @Test
    void receptorAntigoSemLimiteRegistradoUsaTresReservas() throws Exception {
        try (Connection con = ConexaoDB.conectar(); Statement st = con.createStatement()) {
            st.execute("UPDATE usuario SET limite_reservas_ativas = NULL WHERE email = 'ong@foodlink.com'");
        }
        Receptor receptor = receptor();
        assertEquals(3, receptor.getLimiteReservasAtivas());
        reservas.reservar(receptor, OFERTA_1);
        reservas.reservar(receptor, OFERTA_2);
        reservas.reservar(receptor, novaOferta(doador(), "Arroz"));
        assertEquals(3, reservas.reservasAtivas(receptor.getId()));
        UUID quarta = novaOferta(doador(), "Feijão");
        assertThrows(IllegalStateException.class, () -> reservas.reservar(receptor, quarta));
    }

    @Test
    void cancelarAntesDaColetaLiberaOfertaERemoveRetiradaPendente() throws Exception {
        Receptor receptor = receptor();
        Voluntario voluntario = voluntario();
        Reserva reserva = reservas.reservar(receptor, OFERTA_1);
        Retirada retirada = reservas.assumir(voluntario, reserva.getId());
        reservas.cancelar(receptor, reserva.getId());
        assertEquals("CANCELADA", valor("SELECT status FROM reserva WHERE id = ?", reserva.getId()));
        assertEquals("DISPONIVEL", valor("SELECT status FROM oferta_doacao WHERE id = ?", OFERTA_1));
        assertNull(valor("SELECT status FROM retirada WHERE id = ?", retirada.getId()));
        assertThrows(IllegalStateException.class, () -> reservas.assumir(voluntario, reserva.getId()));
        assertNotNull(reservas.reservar(receptor, OFERTA_1));
    }

    @Test
    void cancelamentoDeOfertaVencidaMantemStatusExpirada() throws Exception {
        Receptor receptor = receptor();
        Reserva reserva = reservas.reservar(receptor, OFERTA_1);
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(
                "UPDATE oferta_doacao SET data_validade = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE id = ?")) {
            ps.setObject(1, OFERTA_1);
            ps.executeUpdate();
        }
        reservas.cancelar(receptor, reserva.getId());
        assertEquals("EXPIRADA", valor("SELECT status FROM oferta_doacao WHERE id = ?", OFERTA_1));
        assertEquals(1, ofertas.listarDisponiveis().size());
    }

    @Test
    void naoIniciaColetaDeOfertaQueVenceuDepoisDaReserva() throws Exception {
        Receptor receptor = receptor();
        Voluntario voluntario = voluntario();
        Reserva semVoluntario = reservas.reservar(receptor, OFERTA_1);
        Reserva comVoluntario = reservas.reservar(receptor, OFERTA_2);
        Retirada retirada = reservas.assumir(voluntario, comVoluntario.getId());
        try (Connection con = ConexaoDB.conectar(); Statement st = con.createStatement()) {
            st.execute("UPDATE oferta_doacao SET data_validade = CURRENT_TIMESTAMP - INTERVAL '1 hour'");
        }
        assertTrue(reservas.solicitacoes().isEmpty());
        assertThrows(IllegalStateException.class, () -> reservas.assumir(voluntario, semVoluntario.getId()));
        assertThrows(IllegalStateException.class, () -> reservas.registrarColeta(voluntario, retirada.getId()));
        reservas.cancelar(receptor, semVoluntario.getId());
        reservas.cancelar(receptor, comVoluntario.getId());
        assertEquals("EXPIRADA", valor("SELECT status FROM oferta_doacao WHERE id = ?", OFERTA_1));
        assertEquals("EXPIRADA", valor("SELECT status FROM oferta_doacao WHERE id = ?", OFERTA_2));
    }

    @Test
    void participantesNaoAlteramRegistrosDeOutrasPessoas() {
        Doador outroDoador = usuarios.cadastrarDoador("Outro", "outro@exemplo.com", "doc-outro", "1234");
        Receptor outroReceptor = usuarios.cadastrarReceptor("Outro", "outro-r@exemplo.com", "doc-receptor", "1234");
        Voluntario outroVoluntario = usuarios.cadastrarVoluntario("Outro", "outro-v@exemplo.com", "doc-voluntario", "1234");
        Receptor receptor = receptor();
        Voluntario voluntario = voluntario();

        assertThrows(SecurityException.class, () -> ofertas.atualizar(outroDoador, OFERTA_1, "Alterado", 0, 0, null, null));
        assertThrows(SecurityException.class, () -> ofertas.excluir(outroDoador, OFERTA_1));
        Reserva reserva = reservas.reservar(receptor, OFERTA_1);
        assertThrows(SecurityException.class, () -> reservas.cancelar(outroReceptor, reserva.getId()));
        Retirada retirada = reservas.assumir(voluntario, reserva.getId());
        assertThrows(SecurityException.class, () -> reservas.registrarColeta(outroVoluntario, retirada.getId()));
        reservas.registrarColeta(voluntario, retirada.getId());
        assertThrows(SecurityException.class, () -> reservas.registrarEntrega(outroVoluntario, retirada.getId()));
    }

    @Test
    void falhaNoIndicadorDesfazTodaEntrega() throws Exception {
        Receptor receptor = receptor();
        Voluntario voluntario = voluntario();
        Reserva reserva = reservas.reservar(receptor, OFERTA_1);
        Retirada retirada = reservas.assumir(voluntario, reserva.getId());
        reservas.registrarColeta(voluntario, retirada.getId());
        try (Connection con = ConexaoDB.conectar(); Statement st = con.createStatement()) {
            st.execute("CREATE FUNCTION falhar_indicador() RETURNS trigger LANGUAGE plpgsql AS $$ "
                    + "BEGIN RAISE EXCEPTION 'falha proposital'; END $$");
            st.execute("CREATE TRIGGER falhar BEFORE INSERT ON indicador_impacto "
                    + "FOR EACH ROW EXECUTE FUNCTION falhar_indicador()");
        }
        assertThrows(PersistenciaException.class, () -> reservas.registrarEntrega(voluntario, retirada.getId()));
        assertEquals("EM_ANDAMENTO", valor("SELECT status FROM retirada WHERE id = ?", retirada.getId()));
        assertEquals("ATIVA", valor("SELECT status FROM reserva WHERE id = ?", reserva.getId()));
        assertEquals("RESERVADA", valor("SELECT status FROM oferta_doacao WHERE id = ?", OFERTA_1));
        assertEquals(0, quantidade("SELECT COUNT(*) FROM indicador_impacto"));
        try (Connection con = ConexaoDB.conectar(); Statement st = con.createStatement()) {
            st.execute("DROP TRIGGER falhar ON indicador_impacto");
        }
        reservas.registrarEntrega(voluntario, retirada.getId());
        assertEquals(1, quantidade("SELECT COUNT(*) FROM indicador_impacto"));
    }

    @Test
    void falhaAoCriarOfertaNaoDeixaEnderecoOrfao() throws Exception {
        Doador doador = doador();
        int antes = quantidade("SELECT COUNT(*) FROM endereco");
        try (Connection con = ConexaoDB.conectar(); Statement st = con.createStatement()) {
            st.execute("CREATE FUNCTION falhar_oferta() RETURNS trigger LANGUAGE plpgsql AS $$ "
                    + "BEGIN RAISE EXCEPTION 'falha proposital'; END $$");
            st.execute("CREATE TRIGGER falhar BEFORE INSERT ON oferta_doacao "
                    + "FOR EACH ROW EXECUTE FUNCTION falhar_oferta()");
        }
        assertThrows(PersistenciaException.class, () -> ofertas.cadastrar(doador,
                new Endereco("Rua Teste", "Maringá", "87000-000", -23.42f, -51.93f),
                "Alimentos", 2, 3, Date.from(Instant.now().plusSeconds(86400)), "teste"));
        assertEquals(antes, quantidade("SELECT COUNT(*) FROM endereco"));
    }

    @Test
    void duasPessoasNaoReservamAMesmaOfertaAoMesmoTempo() throws Exception {
        Receptor primeiro = receptor();
        Receptor segundo = usuarios.cadastrarReceptor("Outra ONG", "outra@exemplo.com", "doc-2", "1234");
        int sucessos = concorrer(() -> reservas.reservar(primeiro, OFERTA_1),
                                 () -> reservas.reservar(segundo, OFERTA_1));
        assertEquals(1, sucessos);
        assertEquals(1, quantidade("SELECT COUNT(*) FROM reserva WHERE status = 'ATIVA'"));
    }

    @Test
    void reservasSimultaneasRespeitamOLimiteDeTres() throws Exception {
        Receptor receptor = receptor();
        Doador doador = doador();
        reservas.reservar(receptor, OFERTA_1);
        reservas.reservar(receptor, OFERTA_2);
        UUID terceira = novaOferta(doador, "Arroz");
        UUID quarta = novaOferta(doador, "Feijão");
        int sucessos = concorrer(() -> reservas.reservar(receptor, terceira),
                                 () -> reservas.reservar(receptor, quarta));
        assertEquals(1, sucessos);
        assertEquals(3, reservas.reservasAtivas(receptor.getId()));
    }

    @Test
    void cancelamentoEColetaSimultaneosNaoDeixamEstadosContraditorios() throws Exception {
        Receptor receptor = receptor();
        Voluntario voluntario = voluntario();
        Reserva reserva = reservas.reservar(receptor, OFERTA_1);
        Retirada retirada = reservas.assumir(voluntario, reserva.getId());
        int sucessos = concorrer(() -> { reservas.cancelar(receptor, reserva.getId()); return null; },
                () -> { reservas.registrarColeta(voluntario, retirada.getId()); return null; });
        assertEquals(1, sucessos);
        String status = valor("SELECT status FROM reserva WHERE id = ?", reserva.getId());
        if ("CANCELADA".equals(status)) {
            assertEquals("DISPONIVEL", valor("SELECT status FROM oferta_doacao WHERE id = ?", OFERTA_1));
            assertNull(valor("SELECT status FROM retirada WHERE id = ?", retirada.getId()));
        } else {
            assertEquals("ATIVA", status);
            assertEquals("RESERVADA", valor("SELECT status FROM oferta_doacao WHERE id = ?", OFERTA_1));
            assertEquals("EM_ANDAMENTO", valor("SELECT status FROM retirada WHERE id = ?", retirada.getId()));
        }
    }

    @Test
    void validadeMinimaEhExigidaNaPublicacaoMasNaoNaReserva() throws Exception {
        Doador doador = doador();
        Endereco endereco = new Endereco("Rua Teste", "Maringá", "87000-000", -23.42f, -51.93f);
        assertThrows(IllegalStateException.class, () -> ofertas.cadastrar(doador, endereco, "Arroz", 1, 1,
                Date.from(Instant.now().plusSeconds(3600)), "teste"));
        UUID id = novaOferta(doador, "Feijão");
        ofertas.atualizar(doador, id, "", 0, 0, Date.from(Instant.now().plusSeconds(3600)), "");
        assertEquals(3, ofertas.listarDisponiveis().size());
        assertEquals(1, ofertas.buscarPorRaio(-23.42, -51.93, 1).size());
        assertNotNull(reservas.reservar(receptor(), id));
    }

    @Test
    void ofertaVencidaNaoApareceENaoPodeSerReservada() throws Exception {
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(
                "UPDATE oferta_doacao SET data_validade = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE id = ?")) {
            ps.setObject(1, OFERTA_1);
            ps.executeUpdate();
        }
        assertEquals(1, ofertas.listarDisponiveis().size());
        assertEquals("EXPIRADA", valor("SELECT status FROM oferta_doacao WHERE id = ?", OFERTA_1));
        assertEquals(1, ofertas.buscarPorRaio(-23.6636, -52.6053, 10).size());
        assertThrows(IllegalStateException.class, () -> reservas.reservar(receptor(), OFERTA_1));
        assertThrows(IllegalArgumentException.class, () -> ofertas.buscarPorRaio(91, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> ofertas.buscarPorRaio(0, 0, Double.NaN));
    }

    @Test
    void cadastroValidaDadosEDocumentoDuplicado() {
        assertThrows(IllegalArgumentException.class, () -> usuarios.cadastrarDoador("Teste", "@", "doc", "1234"));
        assertThrows(IllegalArgumentException.class, () -> usuarios.cadastrarDoador("Teste", "x@exemplo.com",
                "12.345.678/0001-99", "1234"));
        usuarios.cadastrarDoador("Novo Doador", "novo@exemplo.com", "doc-novo", "senha-teste");
        assertTrue(usuarios.autenticar("novo@exemplo.com", "senha-teste").isPresent());
        assertFalse(usuarios.autenticar("novo@exemplo.com", "incorreta").isPresent());
    }

    @Test
    void loginAtualizaHashAntigoSemMudarSenha() throws Exception {
        assertFalse(usuarios.autenticar("ong@foodlink.com", "errada").isPresent());
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        assertFalse(valor("SELECT senha_hash FROM usuario WHERE id = ?", id).startsWith("pbkdf2$"));
        assertTrue(usuarios.autenticar("ong@foodlink.com", "1234").isPresent());
        assertTrue(valor("SELECT senha_hash FROM usuario WHERE id = ?", id).startsWith("pbkdf2$"));
        assertTrue(usuarios.autenticar("ong@foodlink.com", "1234").isPresent());
    }

    private void criarChavesAntigasComCascata() throws SQLException {
        String[][] chaves = {
                {"oferta_doacao", "oferta_doacao_doador_id_fkey", "doador_id", "usuario"},
                {"reserva", "reserva_oferta_id_fkey", "oferta_id", "oferta_doacao"},
                {"retirada", "retirada_reserva_id_fkey", "reserva_id", "reserva"},
                {"indicador_impacto", "indicador_impacto_oferta_id_fkey", "oferta_id", "oferta_doacao"}
        };
        try (Connection con = ConexaoDB.conectar(); Statement st = con.createStatement()) {
            for (String[] chave : chaves) {
                st.execute("ALTER TABLE " + chave[0] + " DROP CONSTRAINT " + chave[1]);
                st.execute("ALTER TABLE " + chave[0] + " ADD CONSTRAINT " + chave[1]
                        + " FOREIGN KEY (" + chave[2] + ") REFERENCES " + chave[3] + "(id) ON DELETE CASCADE");
            }
        }
    }

    private UUID novaOferta(Doador doador, String nome) {
        return ofertas.cadastrar(doador, new Endereco("Rua Teste", "Maringá", "87000-000", -23.42f, -51.93f),
                nome, 1, 1, Date.from(Instant.now().plusSeconds(86400)), "teste").getId();
    }

    private int concorrer(Callable<?> primeira, Callable<?> segunda) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        try {
            Future<Boolean> a = executor.submit(() -> tentar(inicio, primeira));
            Future<Boolean> b = executor.submit(() -> tentar(inicio, segunda));
            inicio.countDown();
            return (a.get(20, TimeUnit.SECONDS) ? 1 : 0) + (b.get(20, TimeUnit.SECONDS) ? 1 : 0);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean tentar(CountDownLatch inicio, Callable<?> operacao) throws Exception {
        inicio.await();
        try {
            operacao.call();
            return true;
        } catch (IllegalStateException | IllegalArgumentException esperado) {
            return false;
        }
    }

    private void executarArquivoSql() throws Exception {
        try (Connection con = ConexaoDB.conectar(); Statement st = con.createStatement()) {
            st.execute(Files.readString(Paths.get("database/foodlink.sql"), StandardCharsets.UTF_8));
        }
    }

    private int quantidade(String sql) throws SQLException {
        try (Connection con = ConexaoDB.conectar(); Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private String valor(String sql, UUID id) throws SQLException {
        try (Connection con = ConexaoDB.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
