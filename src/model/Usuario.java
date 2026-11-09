package model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.UUID;

/**
 * «abstract» Usuario — base da herança Doador / Receptor / Voluntario.
 * +autenticar(senha) : boolean | +atualizarPerfil() | +notificar() abstrato
 */
public abstract class Usuario {

    private UUID id;
    private String nome;
    private String email;
    private String documento;
    private String senhaHash;
    private Date dataCadastro;

    protected Usuario(String nome, String email, String documento, String senhaHash) {
        this(UUID.randomUUID(), nome, email, documento, senhaHash, new Date());
    }

    /** Reconstituição a partir do banco (preserva id e data de cadastro). */
    protected Usuario(UUID id, String nome, String email, String documento, String senhaHash, Date dataCadastro) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.documento = documento;
        this.senhaHash = senhaHash;
        this.dataCadastro = dataCadastro;
    }

    /** +autenticar(senha) : boolean — compara o hash SHA-256 da senha. */
    public boolean autenticar(String senha) {
        return senha != null && senhaHash != null && senhaHash.equals(hash(senha));
    }

    /** +atualizarPerfil() — as validações ficam no UsuarioService. */
    public void atualizarPerfil() {
        // intencionalmente vazio: método previsto no diagrama
    }

    /** +notificar() — abstrato no diagrama (polimorfismo). */
    public abstract void notificar();

    /** Discriminador usado na tabela única "usuario". */
    public abstract String getTipo();

    /** Hash SHA-256 da senha (usado no cadastro, no login e nos testes). */
    public static String hash(String senha) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(senha.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getDocumento() { return documento; }
    public String getSenhaHash() { return senhaHash; }
    public Date getDataCadastro() { return dataCadastro; }

    public void setNome(String nome) { this.nome = nome; }
    public void setEmail(String email) { this.email = email; }
    public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }

    @Override
    public String toString() {
        return String.format("%s (%s) <%s>", nome, getTipo(), email);
    }
}
