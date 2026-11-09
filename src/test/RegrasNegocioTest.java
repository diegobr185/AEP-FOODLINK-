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
import model.enums.StatusRetirada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/** Testes de unidade das regras de negócio e dos pilares de POO (sem banco de dados). */
class RegrasNegocioTest {

    private static Date emHoras(int horas) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.HOUR_OF_DAY, horas);
        return c.getTime();
    }

    private static Endereco endereco() {
        return new Endereco("Rua A, 1", "Cianorte", "87200-000", -23.66f, -52.60f);
    }

    private OfertaDoacao ofertaValida() {
        Doador doador = new Doador("Mercado", "m@x.com", "1", Usuario.hash("1234"));
        return doador.cadastrarOferta(endereco(), "Arroz", 10f, 5f, emHoras(48), "obs");
    }

    @Test
    @DisplayName("RN01 - oferta precisa de no minimo 2 horas de validade")
    void rn01ValidadeMinima() {
        assertTrue(ofertaValida().verificarValidadeMinima());

        Doador d = new Doador("D", "d@x.com", "2", Usuario.hash("1234"));
        OfertaDoacao curta = d.cadastrarOferta(endereco(), "Leite", 1f, 1f, emHoras(1), null);
        assertFalse(curta.verificarValidadeMinima());
    }

    @Test
    @DisplayName("Pilares de POO - classe abstrata, heranca e polimorfismo")
    void pilaresPoo() {
        Usuario doador = new Doador("Doador", "d@x.com", "1", Usuario.hash("1234"));
        Usuario receptor = new Receptor("Receptor", "r@x.com", "2", Usuario.hash("1234"));
        Usuario voluntario = new Voluntario("Voluntario", "v@x.com", "3", Usuario.hash("1234"));

        // herança
        assertTrue(doador instanceof Usuario);
        assertTrue(receptor instanceof Usuario);
        assertTrue(voluntario instanceof Usuario);

        // polimorfismo: mesma chamada, comportamentos diferentes (@Override)
        assertEquals("DOADOR", doador.getTipo());
        assertEquals("RECEPTOR", receptor.getTipo());
        assertEquals("VOLUNTARIO", voluntario.getTipo());
        for (Usuario u : new Usuario[]{doador, receptor, voluntario}) {
            assertDoesNotThrow(u::notificar);
        }
    }

    @Test
    @DisplayName("Autenticacao compara o hash da senha")
    void autenticacaoComHash() {
        Doador d = new Doador("D", "d@x.com", "1", Usuario.hash("1234"));
        assertTrue(d.autenticar("1234"));
        assertFalse(d.autenticar("errada"));
    }

    @Test
    @DisplayName("Reserva e Retirada - transicoes de estado do fluxo")
    void transicoesDeEstado() {
        OfertaDoacao oferta = ofertaValida();
        Receptor receptor = new Receptor("R", "r@x.com", "2", Usuario.hash("1234"));
        Voluntario voluntario = new Voluntario("V", "v@x.com", "3", Usuario.hash("1234"));

        Reserva reserva = receptor.reservarOferta(oferta);
        assertEquals(StatusReserva.ATIVA, reserva.getStatus());
        assertEquals(StatusOferta.RESERVADA, oferta.getStatus());

        Retirada retirada = voluntario.assumirRetirada(reserva);
        assertEquals(StatusRetirada.PENDENTE, retirada.getStatus());
        assertThrows(IllegalStateException.class, retirada::registrarEntrega,
                "nao deve entregar sem registrar coleta");

        retirada.registrarColeta();
        assertEquals(StatusRetirada.EM_ANDAMENTO, retirada.getStatus());

        retirada.registrarEntrega();
        assertEquals(StatusRetirada.ENTREGUE, retirada.getStatus());
        assertEquals(StatusReserva.CONCLUIDA, reserva.getStatus());
        assertEquals(StatusOferta.CONCLUIDA, oferta.getStatus());
    }

    @Test
    @DisplayName("RN05 - indicador de impacto calculado pelo peso (2,5 kg CO2 por kg)")
    void indicadorDeImpacto() {
        OfertaDoacao oferta = ofertaValida(); // 5 kg
        IndicadorImpacto indicador = new IndicadorImpacto(oferta);
        assertEquals(5f, indicador.getPesoTotalKg(), 0.001f);
        assertEquals(12.5f, indicador.getCo2EvitadoKg(), 0.001f);
        assertEquals(10f, IndicadorImpacto.calcularImpacto(4f), 0.001f);
    }
}
