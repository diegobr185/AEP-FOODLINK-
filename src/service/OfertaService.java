package service;

import model.Doador;
import model.Endereco;
import model.OfertaDoacao;
import model.enums.StatusOferta;
import repository.OfertaRepository;
import repository.UsuarioRepository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Regras de publicação, consulta e manutenção de ofertas. */
public class OfertaService {

    private final OfertaRepository repository = new OfertaRepository();
    private final UsuarioRepository usuarios = new UsuarioRepository();

    /** RN01: uma nova oferta deve ter pelo menos duas horas de validade. */
    public OfertaDoacao cadastrar(Doador doador, Endereco endereco, String alimento,
                                  float quantidade, float peso, Date validade, String observacoes) {
        if (doador == null || !usuarios.buscarPorId(doador.getId()).filter(u -> u instanceof Doador).isPresent()) {
            throw new IllegalArgumentException("Doador não encontrado.");
        }
        validarEndereco(endereco);
        if (alimento == null || alimento.trim().isEmpty() || alimento.trim().length() > 120) {
            throw new IllegalArgumentException("Informe um alimento com até 120 caracteres.");
        }
        if (!Float.isFinite(quantidade) || !Float.isFinite(peso) || quantidade < 0 || peso < 0
                || (quantidade == 0 && peso == 0)) {
            throw new IllegalArgumentException("Informe a quantidade, o peso ou os dois com valores maiores que zero.");
        }
        if (validade == null) {
            throw new IllegalArgumentException("Informe a data de validade.");
        }
        validarObservacoes(observacoes);
        OfertaDoacao oferta = doador.cadastrarOferta(endereco, alimento.trim(), quantidade, peso, validade, observacoes);
        if (!oferta.verificarValidadeMinima()) {
            throw new IllegalStateException("RN01: a oferta precisa de pelo menos 2 horas de validade.");
        }
        repository.salvar(oferta);
        doador.notificar();
        return oferta;
    }

    /** Somente o dono pode editar uma oferta disponível; zero ou vazio mantém o campo. */
    public OfertaDoacao atualizar(Doador doador, UUID id, String alimento, float quantidade, float peso,
                                  Date validade, String observacoes) {
        OfertaDoacao oferta = buscarOfertaDoDoador(doador, id);
        if (oferta.getStatus() != StatusOferta.DISPONIVEL) {
            throw new IllegalStateException("Somente ofertas disponíveis podem ser editadas.");
        }
        if (alimento != null && !alimento.trim().isEmpty()) {
            if (alimento.trim().length() > 120) {
                throw new IllegalArgumentException("Alimento muito longo.");
            }
            oferta.setAlimento(alimento.trim());
        }
        if (!Float.isFinite(quantidade) || !Float.isFinite(peso) || quantidade < 0 || peso < 0) {
            throw new IllegalArgumentException("Quantidade e peso não podem ser negativos ou inválidos.");
        }
        if (quantidade > 0) {
            oferta.setQuantidade(quantidade);
        }
        if (peso > 0) {
            oferta.setPeso(peso);
        }
        if (validade != null) {
            if (validade.getTime() <= System.currentTimeMillis()) {
                throw new IllegalArgumentException("A nova validade deve ser uma data futura.");
            }
            oferta.setDataValidade(validade);
        }
        if (observacoes != null && !observacoes.trim().isEmpty()) {
            validarObservacoes(observacoes);
            oferta.setObservacoes(observacoes);
        }
        repository.atualizar(oferta);
        return oferta;
    }

    /** Ofertas com histórico não podem ser excluídas. */
    public void excluir(Doador doador, UUID id) {
        buscarOfertaDoDoador(doador, id);
        repository.excluir(id, doador.getId());
    }

    private OfertaDoacao buscarOfertaDoDoador(Doador doador, UUID id) {
        if (doador == null) {
            throw new IllegalArgumentException("Informe o doador.");
        }
        OfertaDoacao oferta = buscarPorId(id);
        if (!oferta.getDoador().getId().equals(doador.getId())) {
            throw new SecurityException("Você só pode alterar suas próprias ofertas.");
        }
        return oferta;
    }

    public OfertaDoacao buscarPorId(UUID id) {
        return repository.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Oferta não encontrada."));
    }

    public List<OfertaDoacao> listarDisponiveis() {
        return repository.listarDisponiveis();
    }

    public List<OfertaDoacao> listarPorDoador(Doador doador) {
        doador.carregarOfertas(repository.listarPorDoador(doador));
        return doador.getOfertas();
    }

    public List<OfertaDoacao> buscarPorRaio(double latitude, double longitude, double raioKm) {
        validarCoordenadas(latitude, longitude);
        if (!Double.isFinite(raioKm) || raioKm <= 0) {
            throw new IllegalArgumentException("O raio deve ser um número maior que zero.");
        }
        return repository.buscarPorRaio(latitude, longitude, raioKm);
    }

    private void validarEndereco(Endereco endereco) {
        if (endereco == null || vazio(endereco.getRua()) || vazio(endereco.getCidade()) || vazio(endereco.getCep())) {
            throw new IllegalArgumentException("Informe rua, cidade e CEP.");
        }
        if (endereco.getRua().length() > 150 || endereco.getCidade().length() > 100 || endereco.getCep().length() > 10) {
            throw new IllegalArgumentException("Rua, cidade ou CEP ultrapassa o tamanho permitido.");
        }
        validarCoordenadas(endereco.getLatitude(), endereco.getLongitude());
    }

    private void validarCoordenadas(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Latitude deve estar entre -90 e 90 e longitude entre -180 e 180.");
        }
    }

    private void validarObservacoes(String observacoes) {
        if (observacoes != null && observacoes.length() > 255) {
            throw new IllegalArgumentException("Observações devem ter até 255 caracteres.");
        }
    }

    private boolean vazio(String texto) {
        return texto == null || texto.trim().isEmpty();
    }
}
