import model.Doador;
import model.Receptor;
import model.Usuario;
import model.Voluntario;
import org.junit.jupiter.api.Test;
import security.Senhas;

import static org.junit.jupiter.api.Assertions.*;

class TestesBasicos {

    @Test
    void senhaNovaUsaSaltEValidaCorretamente() {
        String primeiro = Senhas.criar("senha de teste");
        String segundo = Senhas.criar("senha de teste");
        assertTrue(primeiro.startsWith("pbkdf2$"));
        assertNotEquals(primeiro, segundo);
        assertTrue(Senhas.verificar("senha de teste", primeiro));
        assertFalse(Senhas.verificar("outra senha", primeiro));
        assertFalse(Senhas.verificar("senha", "pbkdf2$invalido"));
    }

    @Test
    void senhasAntigasContinuamFuncionandoParaMigracao() {
        String legado = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4";
        assertTrue(Senhas.ehLegada(legado));
        assertTrue(Senhas.verificar("1234", legado));
        assertFalse(Senhas.verificar("errada", legado));
        Doador doador = new Doador("Teste", "teste@exemplo.com", "doc", legado);
        assertTrue(doador.autenticar("1234"));
    }

    @Test
    void cadaTipoDeUsuarioTemSuaPropriaImplementacao() {
        Usuario doador = new Doador("Nome", "a@exemplo.com", "1", "hash");
        Usuario receptor = new Receptor("Nome", "b@exemplo.com", "2", "hash");
        Usuario voluntario = new Voluntario("Nome", "c@exemplo.com", "3", "hash");
        assertEquals("DOADOR", doador.getTipo());
        assertEquals("RECEPTOR", receptor.getTipo());
        assertEquals("VOLUNTARIO", voluntario.getTipo());
    }
}

