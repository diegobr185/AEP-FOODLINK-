package view;

import controller.OfertaController;
import controller.ReservaController;
import controller.UsuarioController;
import model.Doador;
import model.Endereco;
import model.IndicadorImpacto;
import model.OfertaDoacao;
import model.Receptor;
import model.Reserva;
import model.Retirada;
import model.Usuario;
import model.Voluntario;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;

/** Interface de console (camada de visão). */
public class MenuConsole {

    private final Scanner in = new Scanner(System.in);
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    private final UsuarioController usuarios = new UsuarioController();
    private final OfertaController ofertas = new OfertaController();
    private final ReservaController reservas = new ReservaController();

    public void iniciar() {
        System.out.println("\n=========================================");
        System.out.println("        FOODLINK - AEP 2026");
        System.out.println("  Menos desperdício, mais solidariedade");
        System.out.println("=========================================");
        while (true) {
            System.out.println("\n--- MENU PRINCIPAL ---");
            System.out.println("1) Cadastrar usuário");
            System.out.println("2) Entrar (login)");
            System.out.println("3) Ver ofertas disponíveis");
            System.out.println("4) Relatório de impacto");
            System.out.println("0) Sair");
            String opcao = ler("Opção: ");
            if (opcao == null || "0".equals(opcao)) {
                break;
            }
            try {
                switch (opcao) {
                    case "1": cadastrarUsuario(); break;
                    case "2": login(); break;
                    case "3": listar(ofertas.disponiveis()); break;
                    case "4": relatorio(); break;
                    default: System.out.println("Opção inválida.");
                }
            } catch (RuntimeException e) {
                System.out.println("ERRO: " + e.getMessage());
            }
        }
        System.out.println("Até logo!");
    }

    /* ---------------------------- RF01 ---------------------------- */

    private void cadastrarUsuario() {
        System.out.println("\nTipo: 1) Doador  2) Receptor  3) Voluntário");
        String tipo = ler("Tipo: ");
        String nome = ler("Nome: ");
        String email = ler("E-mail: ");
        String documento = ler("Documento: ");
        String senha = ler("Senha: ");
        Usuario novo;
        switch (tipo) {
            case "1": novo = usuarios.cadastrarDoador(nome, email, documento, senha); break;
            case "2": novo = usuarios.cadastrarReceptor(nome, email, documento, senha); break;
            case "3": novo = usuarios.cadastrarVoluntario(nome, email, documento, senha); break;
            default: System.out.println("Tipo inválido."); return;
        }
        System.out.println("Usuário cadastrado: " + novo.getNome() + " (" + novo.getTipo() + ")");
    }

    private void login() {
        Optional<Usuario> encontrado = usuarios.login(ler("E-mail: "), ler("Senha: "));
        if (!encontrado.isPresent()) {
            System.out.println("Credenciais inválidas.");
            return;
        }
        Usuario logado = encontrado.get();
        System.out.println("Bem-vindo(a), " + logado.getNome() + "!");
        if (logado instanceof Doador) {
            menuDoador((Doador) logado);
        } else if (logado instanceof Receptor) {
            menuReceptor((Receptor) logado);
        } else {
            menuVoluntario((Voluntario) logado);
        }
    }

    /* ------------------------- RF02 / CRUD ------------------------- */

    private void menuDoador(Doador doador) {
        while (true) {
            System.out.println("\n--- DOADOR (" + doador.getNome() + ") ---");
            System.out.println("1) Publicar oferta");
            System.out.println("2) Minhas ofertas");
            System.out.println("3) Editar oferta");
            System.out.println("4) Excluir oferta");
            System.out.println("0) Sair");
            String opcao = ler("Opção: ");
            if (opcao == null || "0".equals(opcao)) {
                return;
            }
            try {
                switch (opcao) {
                    case "1": publicar(doador); break;
                    case "2": listar(ofertas.doDoador(doador)); break;
                    case "3": editarOferta(doador); break;
                    case "4": ofertas.excluir(doador, ler("Id da oferta: ")); System.out.println("Oferta excluída."); break;
                    default: System.out.println("Opção inválida.");
                }
            } catch (RuntimeException e) {
                System.out.println("ERRO: " + e.getMessage());
            }
        }
    }

    private void publicar(Doador doador) {
        System.out.println("\n--- NOVA OFERTA ---");
        String alimento = ler("Alimento: ");
        float quantidade = lerFloat("Quantidade (unidades, 0 se não informada): ");
        float peso = lerFloat("Peso (kg, 0 se não informado): ");
        Date validade = lerData("Validade (dd/MM/yyyy HH:mm, Enter = 3 dias): ");
        String obs = ler("Observações: ");
        Endereco endereco = lerEndereco();
        OfertaDoacao oferta = ofertas.cadastrar(doador, endereco, alimento, quantidade, peso, validade, obs);
        System.out.println("Oferta publicada: " + oferta);
    }

    private void editarOferta(Doador doador) {
        String id = ler("Id da oferta: ");
        String alimento = ler("Novo alimento (Enter mantém): ");
        float quantidade = lerFloat("Nova quantidade (0 mantém): ");
        float peso = lerFloat("Novo peso (0 mantém): ");
        String validadeTxt = ler("Nova validade dd/MM/yyyy HH:mm (Enter mantém): ");
        Date validade = (validadeTxt == null || validadeTxt.isEmpty()) ? null : parseData(validadeTxt);
        ofertas.editar(doador, id, alimento, quantidade, peso, validade, ler("Novas observações: "));
        System.out.println("Oferta atualizada.");
    }

    /* ------------------------ RF03 / RF04 ------------------------ */

    private void menuReceptor(Receptor receptor) {
        while (true) {
            System.out.println("\n--- RECEPTOR (" + receptor.getNome() + ") ---");
            System.out.println("1) Buscar ofertas por raio");
            System.out.println("2) Reservar oferta");
            System.out.println("3) Minhas reservas / cancelar");
            System.out.println("0) Sair");
            String opcao = ler("Opção: ");
            if (opcao == null || "0".equals(opcao)) {
                return;
            }
            try {
                switch (opcao) {
                    case "1": buscarPorRaio(); break;
                    case "2": reservar(receptor); break;
                    case "3": minhasReservas(receptor); break;
                    default: System.out.println("Opção inválida.");
                }
            } catch (RuntimeException e) {
                System.out.println("ERRO: " + e.getMessage());
            }
        }
    }

    private void buscarPorRaio() {
        double latitude = lerFloat("Sua latitude: ");
        double longitude = lerFloat("Sua longitude: ");
        double raio = lerFloat("Raio (km): ");
        listar(ofertas.buscarPorRaio(latitude, longitude, raio));
    }

    private void reservar(Receptor receptor) {
        Reserva reserva = reservas.reservar(receptor, ler("Id da oferta: "));
        System.out.println("Reserva criada: " + reserva.getId()
                + " | reservas ativas: " + reservas.reservasAtivas(receptor.getId()) + "/"
                + receptor.getLimiteReservasAtivas());
    }

    private void minhasReservas(Receptor receptor) {
        List<Reserva> lista = reservas.minhasReservas(receptor.getId());
        if (lista.isEmpty()) {
            System.out.println("Nenhuma reserva.");
            return;
        }
        lista.forEach(r -> System.out.println(r + " | " + fmt.format(r.getDataReserva())));
        String cancelar = ler("Cancelar reserva (id) ou Enter para voltar: ");
        if (cancelar != null && !cancelar.isEmpty()) {
            reservas.cancelar(receptor, cancelar);
            System.out.println("Reserva cancelada.");
        }
    }

    /* ---------------------------- RF05 ---------------------------- */

    private void menuVoluntario(Voluntario voluntario) {
        while (true) {
            System.out.println("\n--- VOLUNTÁRIO (" + voluntario.getNome() + ") ---");
            System.out.println("1) Solicitações de retirada");
            System.out.println("2) Assumir retirada");
            System.out.println("3) Registrar coleta");
            System.out.println("4) Registrar entrega");
            System.out.println("5) Minhas retiradas");
            System.out.println("0) Sair");
            String opcao = ler("Opção: ");
            if (opcao == null || "0".equals(opcao)) {
                return;
            }
            try {
                switch (opcao) {
                    case "1": listarSolicitacoes(); break;
                    case "2": assumir(voluntario); break;
                    case "3": reservas.registrarColeta(voluntario, ler("Id da retirada: ")); System.out.println("Coleta registrada."); break;
                    case "4": reservas.registrarEntrega(voluntario, ler("Id da retirada: "));
                              System.out.println("Entrega registrada. Reserva concluída."); break;
                    case "5": minhasRetiradas(voluntario); break;
                    default: System.out.println("Opção inválida.");
                }
            } catch (RuntimeException e) {
                System.out.println("ERRO: " + e.getMessage());
            }
        }
    }

    private void listarSolicitacoes() {
        List<Reserva> lista = reservas.solicitacoes();
        if (lista.isEmpty()) {
            System.out.println("Nenhuma solicitação de retirada disponível.");
        }
        lista.forEach(r -> System.out.println(r + " | " + fmt.format(r.getDataReserva())));
    }

    private void assumir(Voluntario voluntario) {
        Retirada retirada = reservas.assumir(voluntario, ler("Id da reserva: "));
        System.out.println("Retirada assumida. Id da retirada: " + retirada.getId());
    }

    private void minhasRetiradas(Voluntario voluntario) {
        List<Retirada> lista = reservas.minhasRetiradas(voluntario.getId());
        if (lista.isEmpty()) {
            System.out.println("Nenhuma retirada.");
        }
        for (Retirada t : lista) {
            System.out.printf("%s | %-12s | %-18s | coleta: %s | entrega: %s%n",
                    t.getId(), t.getStatus(), t.getReserva().getOferta().getAlimento(),
                    t.getDataColeta() == null ? "-" : fmt.format(t.getDataColeta()),
                    t.getDataEntrega() == null ? "-" : fmt.format(t.getDataEntrega()));
        }
    }

    /* ---------------------------- RF06 ---------------------------- */

    private void relatorio() {
        List<Retirada> historico = reservas.historico();
        IndicadorImpacto resumo = reservas.resumoImpacto();

        System.out.println("\n--- RELATÓRIO DE IMPACTO ---");
        if (historico.isEmpty()) {
            System.out.println("Nenhuma doação concluída ainda.");
        }
        int semPeso = 0;
        int semQuantidade = 0;
        double unidades = 0;
        for (Retirada t : historico) {
            Reserva r = t.getReserva();
            float quantidade = r.getOferta().getQuantidade();
            float peso = r.getOferta().getPeso();
            if (quantidade == 0) semQuantidade++;
            else unidades += quantidade;
            if (peso == 0) semPeso++;
            String quantidadeTexto = quantidade == 0 ? "quantidade não informada" : String.format("%.1f un", quantidade);
            String pesoTexto = peso == 0 ? "peso não informado" : String.format("%.1f kg", peso);
            System.out.printf("%-18s | %s | %s | %s -> %s | voluntário: %s | entrega: %s%n",
                    r.getOferta().getAlimento(), quantidadeTexto, pesoTexto,
                    r.getOferta().getDoador().getNome(), r.getReceptor().getNome(),
                    t.getVoluntario().getNome(), fmt.format(t.getDataEntrega()));
        }
        System.out.printf("%nDoações concluídas: %d | Unidades informadas: %.2f | Peso informado: %.2f kg | CO2 estimado: %.2f kg%n",
                historico.size(), unidades, resumo.getPesoTotalKg(), resumo.getCo2EvitadoKg());
        if (semQuantidade > 0) {
            System.out.println("Quantidade parcial: " + semQuantidade + " doação(ões) sem quantidade informada.");
        }
        if (semPeso > 0) {
            System.out.println("Estimativa parcial: " + semPeso + " doação(ões) sem peso informado.");
        }
    }

    /* ---------------------------- utilitários ---------------------------- */

    private void listar(List<OfertaDoacao> lista) {
        if (lista.isEmpty()) {
            System.out.println("Nenhuma oferta encontrada.");
            return;
        }
        for (OfertaDoacao oferta : lista) {
            System.out.println(oferta + " | " + oferta.getEndereco() + " | Doador: " + oferta.getDoador().getNome());
            if (oferta.getObservacoes() != null && !oferta.getObservacoes().trim().isEmpty()) {
                System.out.println("  Observações: " + oferta.getObservacoes());
            }
        }
    }

    private Endereco lerEndereco() {
        System.out.println("--- Endereço da retirada ---");
        String rua = ler("Rua: ");
        String cidade = ler("Cidade: ");
        String cep = ler("CEP: ");
        float latitude = lerFloat("Latitude: ");
        float longitude = lerFloat("Longitude: ");
        return new Endereco(rua, cidade, cep, latitude, longitude);
    }

    private String ler(String mensagem) {
        System.out.print(mensagem);
        if (!in.hasNextLine()) {
            return null;
        }
        return in.nextLine().trim();
    }

    private float lerFloat(String mensagem) {
        String valor = ler(mensagem);
        if (valor == null || valor.isEmpty()) {
            throw new IllegalArgumentException("Informe um número.");
        }
        try {
            return Float.parseFloat(valor.replace(",", "."));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Número inválido: " + valor);
        }
    }

    private Date lerData(String mensagem) {
        String valor = ler(mensagem);
        if (valor == null || valor.isEmpty()) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_MONTH, 3);
            return c.getTime();
        }
        return parseData(valor);
    }

    private Date parseData(String valor) {
        for (String formato : new String[] {"dd/MM/yyyy HH:mm", "dd/MM/yyyy"}) {
            SimpleDateFormat parser = new SimpleDateFormat(formato, Locale.US);
            parser.setLenient(false);
            ParsePosition posicao = new ParsePosition(0);
            Date data = parser.parse(valor, posicao);
            if (data != null && posicao.getIndex() == valor.length()) return data;
        }
        throw new IllegalArgumentException("Data inválida. Use dd/MM/yyyy HH:mm.");
    }
}
