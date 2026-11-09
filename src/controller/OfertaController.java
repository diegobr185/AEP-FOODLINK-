package controller;

import model.Doador;
import model.Endereco;
import model.OfertaDoacao;
import service.OfertaService;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Camada de controle do RF02/RF03 e do CRUD de ofertas. */
public class OfertaController {

    private final OfertaService service = new OfertaService();

    public OfertaDoacao cadastrar(Doador doador, Endereco endereco, String alimento,
                                  float quantidade, float peso, Date validade, String observacoes) {
        return service.cadastrar(doador, endereco, alimento, quantidade, peso, validade, observacoes);
    }

    public void editar(UUID id, String alimento, float quantidade, float peso, Date validade, String observacoes) {
        service.atualizar(id, alimento, quantidade, peso, validade, observacoes);
    }

    public void excluir(UUID id) {
        service.excluir(id);
    }

    public List<OfertaDoacao> disponiveis() {
        return service.listarDisponiveis();
    }

    public List<OfertaDoacao> doDoador(UUID doadorId) {
        return service.listarPorDoador(doadorId);
    }

    public List<OfertaDoacao> buscarPorRaio(double latitude, double longitude, double raioKm) {
        return service.buscarPorRaio(latitude, longitude, raioKm);
    }
}
