package service;

import model.Doador;
import model.Endereco;
import model.OfertaDoacao;
import model.enums.StatusOferta;
import repository.OfertaRepository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/** RF02 (publicação), RF03 (busca por raio) e CRUD da OfertaDoacao. RN01 validada aqui. */
public class OfertaService {

    private final OfertaRepository repository = new OfertaRepository();

    /** CREATE — RN01: a oferta precisa de pelo menos 2 horas de validade. */
    public OfertaDoacao cadastrar(Doador doador, Endereco endereco, String alimento,
                                  float quantidade, float peso, Date validade, String observacoes) {
        if (doador == null) {
            throw new IllegalArgumentException("Informe o doador.");
        }
        if (alimento == null || alimento.trim().isEmpty()) {
            throw new IllegalArgumentException("Informe o alimento.");
        }
        if (quantidade <= 0 || peso <= 0) {
            throw new IllegalArgumentException("Quantidade e peso devem ser maiores que zero.");
        }
        if (validade == null) {
            throw new IllegalArgumentException("Informe a data de validade.");
        }
        OfertaDoacao oferta = doador.cadastrarOferta(endereco, alimento.trim(), quantidade, peso, validade, observacoes);
        if (!oferta.verificarValidadeMinima()) {
            throw new IllegalStateException("RN01: a oferta precisa de pelo menos 2 horas de validade.");
        }
        repository.salvar(oferta);
        doador.notificar();
        return oferta;
    }

    /** UPDATE — campos zerados/vazios são mantidos. */
    public OfertaDoacao atualizar(UUID id, String alimento, float quantidade, float peso,
                                  Date validade, String observacoes) {
        OfertaDoacao oferta = buscarPorId(id);
        if (oferta.getStatus() == StatusOferta.CONCLUIDA) {
            throw new IllegalStateException("Oferta já concluída não pode ser editada.");
        }
        if (alimento != null && !alimento.trim().isEmpty()) {
            oferta.setAlimento(alimento.trim());
        }
        if (quantidade > 0) {
            oferta.setQuantidade(quantidade);
        }
        if (peso > 0) {
            oferta.setPeso(peso);
        }
        if (validade != null) {
            oferta.setDataValidade(validade);
        }
        if (observacoes != null && !observacoes.trim().isEmpty()) {
            oferta.setObservacoes(observacoes);
        }
        repository.atualizar(oferta);
        return oferta;
    }

    /** DELETE */
    public void excluir(UUID id) {
        OfertaDoacao oferta = buscarPorId(id);
        if (oferta.getStatus() == StatusOferta.RESERVADA) {
            throw new IllegalStateException("Oferta reservada não pode ser excluída.");
        }
        repository.excluir(id);
    }

    /** Aceita o id completo ou apenas os primeiros caracteres (ex.: "f1a9c865"). */
    public UUID resolverId(String texto) {
        String t = texto == null ? "" : texto.trim();
        if (t.length() == 36) {
            return UUID.fromString(t);
        }
        return repository.idPorPrefixo(t)
                .orElseThrow(() -> new IllegalArgumentException("Oferta não encontrada: " + t));
    }

    public OfertaDoacao buscarPorId(UUID id) {
        return repository.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Oferta não encontrada."));
    }

    public List<OfertaDoacao> listarDisponiveis() {
        return repository.listarPorStatus(StatusOferta.DISPONIVEL);
    }

    public List<OfertaDoacao> listarPorDoador(UUID doadorId) {
        return repository.listarPorDoador(doadorId);
    }

    /** RF03 — ofertas disponíveis dentro do raio informado (km). */
    public List<OfertaDoacao> buscarPorRaio(double latitude, double longitude, double raioKm) {
        if (raioKm <= 0) {
            throw new IllegalArgumentException("O raio deve ser maior que zero.");
        }
        return repository.buscarPorRaio(latitude, longitude, raioKm);
    }
}
