package service;

import model.Doador;
import model.Receptor;
import model.Usuario;
import model.Voluntario;
import repository.UsuarioRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** RF01 — cadastro, login e manutenção dos três tipos de usuário. */
public class UsuarioService {

    private final UsuarioRepository repository = new UsuarioRepository();

    public Doador cadastrarDoador(String nome, String email, String documento, String senha) {
        validar(nome, email, documento, senha);
        Doador d = new Doador(nome, email, documento, Usuario.hash(senha));
        repository.salvar(d);
        return d;
    }

    public Receptor cadastrarReceptor(String nome, String email, String documento, String senha) {
        validar(nome, email, documento, senha);
        Receptor r = new Receptor(nome, email, documento, Usuario.hash(senha));
        repository.salvar(r);
        return r;
    }

    public Voluntario cadastrarVoluntario(String nome, String email, String documento, String senha) {
        validar(nome, email, documento, senha);
        Voluntario v = new Voluntario(nome, email, documento, Usuario.hash(senha));
        repository.salvar(v);
        return v;
    }

    /** Login por e-mail e senha. */
    public Optional<Usuario> autenticar(String email, String senha) {
        return repository.buscarPorEmail(email).filter(u -> u.autenticar(senha));
    }

    public List<Usuario> listar() {
        return repository.listar();
    }

    public Usuario buscarPorId(UUID id) {
        return repository.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
    }

    /** UPDATE — nome, e-mail e senha (campos vazios são mantidos). */
    public void atualizarPerfil(UUID id, String nome, String email, String novaSenha) {
        Usuario u = buscarPorId(id);
        if (nome != null && !nome.trim().isEmpty()) {
            u.setNome(nome.trim());
        }
        if (email != null && !email.trim().isEmpty()) {
            u.setEmail(email.trim());
        }
        if (novaSenha != null && !novaSenha.trim().isEmpty()) {
            u.setSenhaHash(Usuario.hash(novaSenha));
        }
        u.atualizarPerfil();
        repository.atualizar(u);
    }

    /** DELETE */
    public void excluir(UUID id) {
        repository.excluir(id);
    }

    private void validar(String nome, String email, String documento, String senha) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Informe o nome.");
        }
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        if (documento == null || documento.trim().isEmpty()) {
            throw new IllegalArgumentException("Informe o documento.");
        }
        if (senha == null || senha.length() < 4) {
            throw new IllegalArgumentException("A senha deve ter pelo menos 4 caracteres.");
        }
        if (repository.buscarPorEmail(email).isPresent()) {
            throw new IllegalArgumentException("Já existe usuário com este e-mail.");
        }
    }
}
