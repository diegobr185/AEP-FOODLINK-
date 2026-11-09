package controller;

import model.Doador;
import model.Receptor;
import model.Usuario;
import model.Voluntario;
import service.UsuarioService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Camada de controle do RF01 — recebe as ações da view e aciona o service. */
public class UsuarioController {

    private final UsuarioService service = new UsuarioService();

    public Doador cadastrarDoador(String nome, String email, String documento, String senha) {
        return service.cadastrarDoador(nome, email, documento, senha);
    }

    public Receptor cadastrarReceptor(String nome, String email, String documento, String senha) {
        return service.cadastrarReceptor(nome, email, documento, senha);
    }

    public Voluntario cadastrarVoluntario(String nome, String email, String documento, String senha) {
        return service.cadastrarVoluntario(nome, email, documento, senha);
    }

    public Optional<Usuario> login(String email, String senha) {
        return service.autenticar(email, senha);
    }

    public List<Usuario> listar() {
        return service.listar();
    }

    public void atualizarPerfil(UUID id, String nome, String email, String senha) {
        service.atualizarPerfil(id, nome, email, senha);
    }
}
