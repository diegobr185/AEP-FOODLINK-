package service;

import model.Doador;
import model.Receptor;
import model.Usuario;
import model.Voluntario;
import repository.UsuarioRepository;
import security.Senhas;

import java.util.Optional;

/** Cadastro e login dos três tipos de usuário. */
public class UsuarioService {

    private final UsuarioRepository repository = new UsuarioRepository();

    public Doador cadastrarDoador(String nome, String email, String documento, String senha) {
        validar(nome, email, documento, senha);
        Doador d = new Doador(nome.trim(), email.trim(), documento.trim(), Usuario.hash(senha));
        repository.salvar(d);
        return d;
    }

    public Receptor cadastrarReceptor(String nome, String email, String documento, String senha) {
        validar(nome, email, documento, senha);
        Receptor r = new Receptor(nome.trim(), email.trim(), documento.trim(), Usuario.hash(senha));
        repository.salvar(r);
        return r;
    }

    public Voluntario cadastrarVoluntario(String nome, String email, String documento, String senha) {
        validar(nome, email, documento, senha);
        Voluntario v = new Voluntario(nome.trim(), email.trim(), documento.trim(), Usuario.hash(senha));
        repository.salvar(v);
        return v;
    }

    /** Atualiza o hash das contas antigas depois de confirmar a senha. */
    public Optional<Usuario> autenticar(String email, String senha) {
        if (email == null || senha == null) {
            return Optional.empty();
        }
        Optional<Usuario> encontrado = repository.buscarPorEmail(email.trim()).filter(u -> u.autenticar(senha));
        if (!encontrado.isPresent()) {
            return encontrado;
        }

        Usuario usuario = encontrado.get();
        String antigo = usuario.getSenhaHash();
        if (Senhas.ehLegada(antigo)) {
            String novo = Usuario.hash(senha);
            if (repository.atualizarHashAntigo(usuario.getId(), antigo, novo)) {
                usuario.setSenhaHash(novo);
            } else {
                return repository.buscarPorId(usuario.getId()).filter(u -> u.autenticar(senha));
            }
        }
        return encontrado;
    }

    private void validar(String nome, String email, String documento, String senha) {
        if (nome == null || nome.trim().isEmpty() || nome.trim().length() > 120) {
            throw new IllegalArgumentException("Informe um nome com até 120 caracteres.");
        }
        if (email == null || email.trim().length() > 150
                || !email.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        if (documento == null || documento.trim().isEmpty() || documento.trim().length() > 30) {
            throw new IllegalArgumentException("Informe um documento com até 30 caracteres.");
        }
        if (senha == null || senha.trim().isEmpty() || senha.length() < 4) {
            throw new IllegalArgumentException("A senha deve ter pelo menos 4 caracteres.");
        }
        if (repository.buscarPorEmail(email.trim()).isPresent()) {
            throw new IllegalArgumentException("Já existe usuário com este e-mail.");
        }
    }
}
