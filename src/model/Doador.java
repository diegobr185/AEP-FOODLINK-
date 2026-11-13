package model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Um doador pode ter várias ofertas. */
public class Doador extends Usuario {

    private final List<OfertaDoacao> ofertas = new ArrayList<>();

    public Doador(String nome, String email, String documento, String senhaHash) {
        super(nome, email, documento, senhaHash);
    }

    public Doador(UUID id, String nome, String email, String documento, String senhaHash, Date dataCadastro) {
        super(id, nome, email, documento, senhaHash, dataCadastro);
    }

    /** Cria uma oferta vinculada a este doador. */
    public OfertaDoacao cadastrarOferta(Endereco endereco, String alimento, float quantidade,
                                        float peso, Date dataValidade, String observacoes) {
        return new OfertaDoacao(this, endereco, alimento, quantidade, peso, dataValidade, observacoes);
    }

    /** Atualiza a lista sempre que o doador consulta suas ofertas no banco. */
    public void carregarOfertas(List<OfertaDoacao> ofertasDoBanco) {
        ofertas.clear();
        ofertas.addAll(ofertasDoBanco);
    }

    public List<OfertaDoacao> getOfertas() {
        return List.copyOf(ofertas);
    }

    @Override
    public void notificar() {
        System.out.println("[Doador] " + getNome() + ", sua oferta foi registrada com sucesso.");
    }

    @Override
    public String getTipo() { return "DOADOR"; }
}
