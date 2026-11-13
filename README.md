# 🍎 FoodLink

### Plataforma de Redução de Desperdício Alimentar e Logística Solidária

Projeto Bimestral — AEP 4º Semestre · Engenharia de Software · 2026

---

## 📌 Sobre o projeto

O **FoodLink** conecta **doadores**, **receptores** e **voluntários** para reduzir o desperdício de alimentos próprios para consumo e encaminhá-los a organizações sociais.

Fluxo principal:

**Doador → Oferta → Receptor → Reserva → Voluntário → Retirada/Entrega → Registro do impacto**

O sistema é uma aplicação Java de **console**, com persistência em **PostgreSQL** via JDBC, organizada em camadas e coberta por testes automatizados.

Alinhado aos ODS da ONU: **2** (Fome Zero), **12** (Consumo e Produção Responsáveis), **13** (Ação Contra a Mudança Global do Clima) e **17** (Parcerias).

---

## 👥 Perfis de usuário

| Perfil           | O que pode fazer no sistema                                                                              |
| ---------------- | -------------------------------------------------------------------------------------------------------- |
| 🏢 **Doador**     | Publicar, listar, editar e excluir suas ofertas de doação (somente ofertas sem histórico de reserva)     |
| 🤝 **Receptor**   | Buscar ofertas por raio de distância, reservar uma oferta, acompanhar reservas e cancelá-las antes da coleta |
| 🚚 **Voluntário** | Ver solicitações de retirada, assumir uma retirada, registrar coleta e entrega                           |

Qualquer pessoa (sem login) pode ainda consultar as ofertas disponíveis e o relatório de impacto.

### Estados do fluxo

| Entidade    | Estados                                          |
| ----------- | ------------------------------------------------ |
| **Oferta**  | `DISPONIVEL` → `RESERVADA` → `CONCLUIDA` (ou `EXPIRADA`) |
| **Reserva** | `ATIVA` → `CONCLUIDA` (ou `CANCELADA`)           |
| **Retirada**| `PENDENTE` → `EM_ANDAMENTO` → `ENTREGUE`         |

Ofertas vencidas passam a `EXPIRADA` automaticamente, deixam de aparecer nas listas e não podem mais ser reservadas.

---

## 📋 Requisitos funcionais

| Código   | Requisito                                                                                              |
| -------- | ------------------------------------------------------------------------------------------------------ |
| **RF01** | Cadastro e autenticação de usuários (Doador, Receptor e Voluntário)                                    |
| **RF02** | Cadastro de ofertas de doação (alimento, quantidade e/ou peso, validade, endereço e observações)       |
| **RF03** | Consulta de ofertas por localização e raio de distância                                                |
| **RF04** | Reserva de oferta disponível e acompanhamento do status da reserva                                     |
| **RF05** | Voluntário assume retirada e registra coleta e entrega                                                 |
| **RF06** | Histórico das doações e indicadores de alimento reaproveitado e CO₂ evitado                            |

Na oferta, é obrigatório informar **a quantidade, o peso ou os dois**. O relatório de impacto sinaliza quando uma estimativa é parcial por falta de peso ou de quantidade.

## 📐 Regras de negócio

| Código   | Regra                                                                                                          |
| -------- | -------------------------------------------------------------------------------------------------------------- |
| **RN01** | A oferta deve ter pelo menos **2 horas de validade** restantes no momento da publicação                        |
| **RN02** | Um Receptor pode ter no máximo **3 reservas ativas** ao mesmo tempo                                            |
| **RN03** | Uma oferta já reservada não pode ser reservada por outro Receptor (garantido também por índice único no banco) |
| **RN04** | Doações concluídas permanecem registradas (histórico e auditoria); ofertas com histórico não podem ser excluídas |
| **RN05** | O CO₂ evitado é calculado a partir do peso: **peso (kg) × 2,5**. Sem peso informado, não há cálculo de CO₂     |

---

## 🏗️ Arquitetura

Monolito em camadas:

```
View (MenuConsole) → Controller → Service → Repository → PostgreSQL
```

A regra de negócio fica no **Service**, o acesso ao banco (SQL com `PreparedStatement`) fica no **Repository**, e a conexão é centralizada em `config/ConexaoDB`.

Pontos de robustez:

- **Transações:** cada operação que altera várias tabelas (reservar, cancelar, registrar entrega, excluir oferta) roda em uma única transação, com *commit* ou *rollback* (`repository/Transacoes`). Se algo falha no meio, nada é gravado pela metade.
- **Concorrência:** as regras RN02 e RN03 são conferidas novamente no banco com trava de linha (`SELECT ... FOR UPDATE`), então duas pessoas não reservam a mesma oferta ao mesmo tempo e um receptor não passa do limite de reservas simultâneas.
- **Erros de persistência:** falhas de banco são convertidas em `PersistenciaException`, com mensagem clara para o usuário.

### Segurança das senhas

- Senhas novas são guardadas com **PBKDF2 (HMAC-SHA256) com salt** e 310.000 iterações (`security/Senhas`).
- Hashes SHA-256 de versões antigas ainda são aceitos apenas para migração e **são trocados por PBKDF2 no primeiro login** bem-sucedido.
- O arquivo `db.properties`, que pode conter a senha do banco, **não é versionado** (está no `.gitignore`).

### Orientação a Objetos

- **Herança:** `Usuario` (classe abstrata) → `Doador`, `Receptor`, `Voluntario`
- **Polimorfismo:** `notificar()` e `getTipo()` são abstratos em `Usuario` e sobrescritos (`@Override`) em cada perfil
- **Encapsulamento:** atributos privados com getters/setters
- **Enums:** `StatusOferta`, `StatusReserva`, `StatusRetirada`
- **CRUD da entidade principal:** `OfertaDoacao` (criar, listar, editar e excluir)

---

## 💻 Tecnologias

| Tecnologia                         | Uso                                                      |
| ---------------------------------- | -------------------------------------------------------- |
| **Java 11+**                       | Linguagem da aplicação                                   |
| **PostgreSQL 12+**                 | Banco de dados                                           |
| **JDBC** (driver `org.postgresql`) | Acesso ao banco                                          |
| **Maven**                          | Build e empacotamento (`foodlink.jar`)                   |
| **JUnit 5**                        | Testes automatizados (somente no escopo de teste)        |

A busca por raio (RF03) usa a **fórmula de Haversine escrita em SQL** sobre latitude e longitude. Não é necessário PostGIS.

---

## ▶️ Como executar

### Pré-requisitos

- JDK 11 ou superior
- Maven 3.6 ou superior
- PostgreSQL 12 ou superior

### 1. Criar o banco

```bash
# cria o banco vazio
psql -U postgres -c "CREATE DATABASE foodlink;"

# cria tabelas e dados de exemplo
psql -U postgres -d foodlink -f database/foodlink.sql
```

O script pode ser executado de novo quando quiser: ele **não apaga dados**. Se o banco já existir de uma versão anterior, o script também ajusta a estrutura (chaves estrangeiras, colunas e índice de reserva ativa) preservando as doações já registradas.

> Em um banco de versão antiga, rode antes `database/verificar_dados.sql`, depois de fazer backup. É um diagnóstico **somente de leitura**: cada consulta deve retornar zero linhas. Se aparecer alguma, revise o caso antes de aplicar o script principal.

### 2. Configurar a conexão

A conexão é lida nesta ordem: **variáveis de ambiente** → arquivo `db.properties` → valores padrão.

Valores padrão (quando nada é configurado):

```
db.url=jdbc:postgresql://localhost:5432/foodlink
db.user=postgres
db.password=        (vazia)
```

Como a senha padrão é vazia e o `db.properties` não vem no repositório, **crie o arquivo `db.properties` na raiz do projeto** (a mesma pasta de onde você roda o programa) com os seus dados:

```
db.url=jdbc:postgresql://localhost:5432/foodlink
db.user=postgres
db.password=sua_senha
```

Se preferir não criar arquivo, defina as variáveis de ambiente `DB_URL`, `DB_USER` e `DB_PASSWORD`.

Ao iniciar, o programa confere a conexão e se as 6 tabelas existem; se algo estiver errado, mostra a URL usada e o motivo do erro.

### 3. Compilar e rodar

```bash
mvn clean package
java -jar target/foodlink.jar
```

O `mvn clean package` gera o `target/foodlink.jar` já com o driver do PostgreSQL dentro e também executa os testes básicos (que não precisam de banco).

### 🔑 Logins de teste

Todos os usuários de exemplo têm a senha **`1234`**.

| Perfil     | E-mail                 |
| ---------- | ---------------------- |
| Doador     | `padaria@foodlink.com` |
| Receptor   | `ong@foodlink.com`     |
| Voluntário | `ana@foodlink.com`     |

As ofertas de exemplo estão em Cianorte-PR (latitude ≈ -23,66 e longitude ≈ -52,61). Para testar a busca por raio, informe essas coordenadas.

> Use os dados de exemplo apenas em ambiente de teste.

---

## 🧪 Testes

```bash
mvn test
```

| Arquivo                                | O que cobre                                                                                                                                                  | Precisa de banco? |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------- |
| `tests/TestesBasicos.java`             | Hash de senha com salt, compatibilidade com senhas antigas e polimorfismo dos perfis                                                                         | Não               |
| `tests/FluxoIntegradoTest.java`        | Fluxo completo (reserva → coleta → entrega → impacto), regras RN01 a RN05, permissões entre usuários, transações com *rollback*, concorrência e migração do SQL | Sim               |

Os testes de integração ficam **desativados por padrão**, porque **apagam e recriam o schema `public`** antes de cada teste. Use somente um banco vazio chamado `foodlink_test`. Passo a passo (PowerShell, Linux e macOS) em [`tests/README.md`](tests/README.md).

---

## 🧭 Menus do sistema

**Principal:** cadastrar usuário · entrar (login) · ver ofertas disponíveis · relatório de impacto

**Doador:** publicar oferta · minhas ofertas · editar oferta · excluir oferta

**Receptor:** buscar ofertas por raio · reservar oferta · minhas reservas / cancelar

**Voluntário:** solicitações de retirada · assumir retirada · registrar coleta · registrar entrega · minhas retiradas

**Relatório de impacto:** lista as doações entregues (alimento, quantidade, peso, doador → receptor, voluntário e data) e mostra os totais de doações concluídas, unidades, peso e CO₂ estimado.

---

## 📂 Estrutura do projeto

```
AEP-FOODLINK-/
├── pom.xml
├── .gitignore
├── README.md
├── db.properties                 # local, NÃO versionado (você cria; veja "Configurar a conexão")
├── database/
│   ├── foodlink.sql              # script único: 6 tabelas + dados de exemplo (reexecutável)
│   ├── verificar_dados.sql       # diagnóstico somente leitura para bancos de versões antigas
│   └── DER_FOODLINK.png
├── docs/
│   ├── DER_FOODLINK.png
│   ├── diagrama-classes.png
│   └── FoodLink-1ºBimestre-AEP.pdf
├── src/
│   ├── app/          # FoodLinkApp (main)
│   ├── config/       # ConexaoDB
│   ├── controller/   # Usuario, Oferta e Reserva controllers
│   ├── model/        # entidades (+ enums/)
│   ├── repository/   # acesso ao banco (JDBC), Transacoes e PersistenciaException
│   ├── security/     # Senhas (PBKDF2 com salt)
│   ├── service/      # regras de negócio
│   └── view/         # MenuConsole
└── tests/
    ├── README.md
    ├── TestesBasicos.java
    └── FluxoIntegradoTest.java
```

Os testes ficam fora de `src/` para não entrarem no JAR.

---

## 🗄️ Banco de dados

| Tabela              | Função                                                         |
| ------------------- | -------------------------------------------------------------- |
| `usuario`           | Doador, Receptor e Voluntário em uma só tabela (coluna `tipo`) |
| `endereco`          | Endereço com latitude e longitude                              |
| `oferta_doacao`     | Oferta de alimento (entidade principal do CRUD)                |
| `reserva`           | Reserva de uma oferta por um Receptor                          |
| `retirada`          | Coleta e entrega feitas por um Voluntário                      |
| `indicador_impacto` | Peso reaproveitado e CO₂ evitado                               |

O histórico de doações do relatório é montado a partir das retiradas com status `ENTREGUE`. As chaves estrangeiras **não usam exclusão em cascata**, para que o histórico nunca seja apagado por engano. Um índice único parcial (`idx_reserva_ativa_por_oferta`) impede duas reservas ativas para a mesma oferta.

Veja o diagrama em [`docs/DER_FOODLINK.png`](docs/DER_FOODLINK.png).

---

## 📚 Documentação

- **Diagrama de classes:** [`docs/diagrama-classes.png`](docs/diagrama-classes.png)
- **DER:** [`docs/DER_FOODLINK.png`](docs/DER_FOODLINK.png)
- **Entrega do 1º bimestre:** [`docs/FoodLink-1ºBimestre-AEP.pdf`](docs/FoodLink-1%C2%BABimestre-AEP.pdf)
- **Como rodar os testes:** [`tests/README.md`](tests/README.md)
- **Diagnóstico de bancos antigos:** [`database/verificar_dados.sql`](database/verificar_dados.sql)

---

## 🔄 Git

- Branch principal: `main`
- Branches de desenvolvimento: `feature/nome-da-funcionalidade`
- Commits no padrão *Conventional Commits* (`feat:`, `fix:`, `docs:`)

---

## 👨‍💻 Equipe

| Integrante     | Função                        |
| -------------- | ----------------------------- |
| Diego Brito    | Banco de dados e interfaces   |
| Ruan Gabriel   | Modelagem POO                 |
| Arthur Morales | Backend (CRUD e persistência) |

---

## 📄 Projeto acadêmico

**Projeto Bimestral — AEP 4º Semestre** · Curso de Engenharia de Software · 2026

Este projeto tem finalidade acadêmica. O histórico de commits do GitHub registra a participação de cada integrante.
