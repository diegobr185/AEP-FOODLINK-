package model;

import security.Senhas;

import java.util.Date;
import java.util.UUID;

/** Classe base dos três tipos de usuário. */
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

    /** Verifica senhas atuais e as da versão antiga, para permitir migração. */
    public boolean autenticar(String senha) {
        return Senhas.verificar(senha, senhaHash);
    }

    /** Cada tipo de usuário recebe uma mensagem diferente. */
    public abstract void notificar();

    /** Discriminador usado na tabela única "usuario". */
    public abstract String getTipo();

    /** Gera um hash com salt para um novo cadastro ou uma nova senha. */
    public static String hash(String senha) {
        return Senhas.criar(senha);
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getDocumento() { return documento; }
    public String getSenhaHash() { return senhaHash; }
    public Date getDataCadastro() { return dataCadastro; }

    public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }
}
