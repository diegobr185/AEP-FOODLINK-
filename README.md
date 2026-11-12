# 🍎 FoodLink

### Plataforma de Redução de Desperdício Alimentar e Logística Solidária

Projeto Bimestral — AEP 4º Semestre · Engenharia de Software · 2026

---

## 📌 Sobre o projeto

O **FoodLink** conecta **doadores**, **receptores** e **voluntários** para reduzir o desperdício de alimentos próprios para consumo e encaminhá-los a organizações sociais.

Fluxo principal:

**Doador → Oferta → Receptor → Reserva → Voluntário → Retirada/Entrega → Registro do impacto**

O sistema é uma aplicação Java de **console**, com persistência em **PostgreSQL** via JDBC, organizada em camadas.

Alinhado aos ODS da ONU: **2** (Fome Zero), **12** (Consumo e Produção Responsáveis), **13** (Ação Contra a Mudança Global do Clima) e **17** (Parcerias).

---

## 👥 Perfis de usuário

| Perfil | O que pode fazer no sistema |
| --- | --- |
| 🏢 **Doador** | Publicar, listar, editar e excluir suas ofertas de doação |
| 🤝 **Receptor** | Buscar ofertas por raio de distância, reservar uma oferta, acompanhar e cancelar reservas |
| 🚚 **Voluntário** | Ver solicitações de retirada, assumir uma retirada, registrar coleta e entrega |

Qualquer pessoa (sem login) pode ainda consultar as ofertas disponíveis e o relatório de impacto.

---

## 📋 Requisitos funcionais

| Código | Requisito |
| --- | --- |
| **RF01** | Cadastro e autenticação de usuários (Doador, Receptor e Voluntário) |
| **RF02** | Cadastro de ofertas de doação (alimento, quantidade, peso, validade, endereço e observações) |
| **RF03** | Consulta de ofertas por localização e raio de distância |
| **RF04** | Reserva de oferta disponível e acompanhamento do status da reserva |
| **RF05** | Voluntário assume retirada e registra coleta e entrega |
| **RF06** | Histórico das doações e indicadores de alimento reaproveitado e CO₂ evitado |

## 📐 Regras de negócio

| Código | Regra |
| --- | --- |
| **RN01** | A oferta deve ter pelo menos **2 horas de validade** restantes no momento da publicação |
| **RN02** | Um Receptor pode ter no máximo **3 reservas ativas** ao mesmo tempo |
| **RN03** | Uma oferta já reservada não pode ser reservada por outro Receptor |
| **RN04** | Doações concluídas permanecem registradas (histórico e auditoria) |
| **RN05** | O CO₂ evitado é calculado a partir do peso: **peso (kg) × 2,5** |

---

## 🏗️ Arquitetura

Monolito em camadas:

```text
View (MenuConsole) → Controller → Service → Repository → PostgreSQL
```

A regra de negócio fica no **Service**, o acesso ao banco (SQL com `PreparedStatement`) fica no **Repository**, e a conexão é centralizada em `config/ConexaoDB`.

### Orientação a Objetos

- **Herança:** `Usuario` (classe abstrata) → `Doador`, `Receptor`, `Voluntario`
- **Polimorfismo:** `notificar()` e `getTipo()` são abstratos em `Usuario` e sobrescritos (`@Override`) em cada perfil
- **Encapsulamento:** atributos privados com getters/setters
- **Enums:** `StatusOferta`, `StatusReserva`, `StatusRetirada`
- **CRUD da entidade principal:** `OfertaDoacao` (criar, listar, editar e excluir)

---

## 💻 Tecnologias

| Tecnologia | Uso |
| --- | --- |
| **Java 11+** | Linguagem da aplicação |
| **PostgreSQL 12+** | Banco de dados |
| **JDBC** (driver `org.postgresql`) | Acesso ao banco |
| **Maven** | Build e empacotamento (`foodlink.jar`) |

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

# cria tabelas, view e dados de exemplo
psql -U postgres -d foodlink -f database/foodlink.sql
```

O script pode ser executado de novo quando quiser: ele apaga e recria as tabelas.

### 2. Configurar a conexão

A conexão é lida nesta ordem: **variáveis de ambiente** → arquivo `src/db.properties` → valores padrão.

Padrão atual (`src/db.properties`):

```properties
db.url=jdbc:postgresql://localhost:5432/foodlink
db.user=postgres
db.password=postgres
```

Para usar outros dados sem editar o arquivo, defina as variáveis `DB_URL`, `DB_USER` e `DB_PASSWORD`.

### 3. Compilar e rodar

```bash
mvn clean package
java -jar target/foodlink.jar
```

O `mvn clean package` gera o `target/foodlink.jar` já com o driver do PostgreSQL dentro.

### 🔑 Logins de teste

Todos os usuários de exemplo têm a senha **`1234`**.

| Perfil | E-mail |
| --- | --- |
| Doador | `padaria@foodlink.com` |
| Receptor | `ong@foodlink.com` |
| Voluntário | `ana@foodlink.com` |

As ofertas de exemplo estão em Cianorte-PR (latitude ≈ -23,66 e longitude ≈ -52,61). Para testar a busca por raio, informe essas coordenadas.

---

## 🧭 Menus do sistema

**Principal:** cadastrar usuário · entrar (login) · ver ofertas disponíveis · relatório de impacto

**Doador:** publicar oferta · minhas ofertas · editar oferta · excluir oferta

**Receptor:** buscar ofertas por raio · reservar oferta · minhas reservas / cancelar

**Voluntário:** solicitações de retirada · assumir retirada · registrar coleta · registrar entrega · minhas retiradas

---

## 📂 Estrutura do projeto

```text
AEP-FOODLINK-/
├── pom.xml
├── .gitignore
├── README.md
├── database/
│   └── foodlink.sql              # script único: 6 tabelas + 1 view + dados de exemplo
├── docs/
│   ├── DER.md                    # DER em Mermaid (o GitHub renderiza)
│   ├── DER_FOODLINK.png
│   ├── Diagrama_Classe_FOODLINK.png
│   └── FoodLink-1ºBimestre-AEP.pdf
└── src/
    ├── app/          # FoodLinkApp (main)
    ├── config/       # ConexaoDB
    ├── controller/   # Usuario, Oferta e Reserva controllers
    ├── model/        # entidades (+ enums/)
    ├── repository/   # acesso ao banco (JDBC)
    ├── service/      # regras de negócio
    ├── view/         # MenuConsole
    └── db.properties
```

---

## 🗄️ Banco de dados

| Tabela | Função |
| --- | --- |
| `usuario` | Doador, Receptor e Voluntário em uma só tabela (coluna `tipo`) |
| `endereco` | Endereço com latitude e longitude |
| `oferta_doacao` | Oferta de alimento (entidade principal do CRUD) |
| `reserva` | Reserva de uma oferta por um Receptor |
| `retirada` | Coleta e entrega feitas por um Voluntário |
| `indicador_impacto` | Peso reaproveitado e CO₂ evitado |
| `vw_historico_doacoes` *(view)* | Histórico das doações entregues, usado no relatório |

Veja o diagrama em [`docs/DER.md`](docs/DER.md) e [`docs/DER_FOODLINK.png`](docs/DER_FOODLINK.png).

---

## 📚 Documentação

- **Diagrama de classes:** [`docs/Diagrama_Classe_FOODLINK.png`](docs/Diagrama_Classe_FOODLINK.png)
- **DER:** [`docs/DER.md`](docs/DER.md) e [`docs/DER_FOODLINK.png`](docs/DER_FOODLINK.png)
- **Entrega do 1º bimestre:** [`docs/FoodLink-1ºBimestre-AEP.pdf`](docs/FoodLink-1%C2%BABimestre-AEP.pdf)

---

## 🔄 Git

- Branch principal: `main`
- Branches de desenvolvimento: `feature/nome-da-funcionalidade`
- Commits no padrão *Conventional Commits* (`feat:`, `fix:`, `docs:`)

---

## 👨‍💻 Equipe

| Integrante | Função |
| --- | --- |
| Diego Brito | Banco de dados e interfaces |
| Ruan Gabriel | Modelagem POO |
| Arthur Morales | Backend (CRUD e persistência) |

---

## 📄 Projeto acadêmico

**Projeto Bimestral — AEP 4º Semestre** · Curso de Engenharia de Software · 2026

Este projeto tem finalidade acadêmica. O histórico de commits do GitHub registra a participação de cada integrante.
