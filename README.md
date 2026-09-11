# 🍎 FoodLink

### Plataforma Inteligente de Redução de Desperdício Alimentar e Logística Solidária



---

## 📌 Sobre o projeto

O **FoodLink** é uma plataforma desenvolvida com o objetivo de **reduzir o desperdício de alimentos próprios para consumo** e facilitar o encaminhamento desses excedentes para organizações sociais.

A proposta é conectar, de maneira organizada, **doadores, receptores e voluntários**, permitindo que alimentos disponíveis sejam divulgados, localizados, reservados e encaminhados para quem precisa.

O sistema busca solucionar problemas como a dificuldade de encontrar um destino adequado para excedentes alimentares, a dispersão das informações de doação, a dificuldade de organizar a logística de retirada e a ausência de indicadores sobre o impacto social e ambiental das ações.

O projeto está alinhado aos **Objetivos de Desenvolvimento Sustentável (ODS) da ONU**, especialmente os ODS 2, 12, 13 e 17.

---

## 🎯 Objetivo

O principal objetivo do FoodLink é criar um fluxo digital que facilite o processo:

**Doador → Oferta → Receptor → Reserva → Voluntário → Retirada/Entrega → Registro do impacto**

Dessa forma, o sistema busca diminuir o tempo entre a identificação de um excedente e seu encaminhamento, contribuindo para o reaproveitamento de alimentos e para a geração de impacto social e ambiental positivo.

---

## 🌎 ODS relacionados

### ODS 2 — Fome Zero

Facilitar o encaminhamento de excedentes próprios para consumo para organizações que atendem pessoas em situação de vulnerabilidade.

### ODS 12 — Consumo e Produção Responsáveis

Criar um canal para o reaproveitamento de excedentes alimentares antes que sejam descartados.

### ODS 13 — Ação Contra a Mudança Global do Clima

Registrar a quantidade de alimentos reaproveitados e possibilitar a estimativa do impacto ambiental evitado.

### ODS 17 — Parcerias

Conectar empresas, organizações sociais e voluntários por meio de um fluxo digital organizado.

---

# 👥 Usuários do sistema

O FoodLink possui três principais tipos de usuários:

### 🏢 Doador

Pessoa ou organização que possui alimentos excedentes próprios para consumo.

Pode:

* Criar uma conta;
* Cadastrar ofertas de doação;
* Informar alimentos e quantidade;
* Informar peso e validade;
* Informar o endereço para retirada;
* Acompanhar o encaminhamento da doação.

### 🤝 Receptor

Pessoa ou organização responsável por receber os alimentos.

Pode:

* Consultar ofertas disponíveis;
* Pesquisar ofertas por localização;
* Visualizar informações da doação;
* Verificar a validade;
* Reservar uma oferta;
* Acompanhar o status da reserva.

### 🚚 Voluntário

Responsável por auxiliar na logística de retirada e entrega.

Pode:

* Visualizar solicitações de retirada;
* Assumir uma retirada;
* Registrar a coleta;
* Registrar a entrega;
* Acompanhar o processo de transporte.

---

# 📋 Requisitos Funcionais

| Código   | Requisito                                                                                                                                                                |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **RF01** | O sistema deve permitir o cadastro e a autenticação de usuários dos tipos Doador, Receptor e Voluntário, com nome, e-mail, documento de identificação e dados de acesso. |
| **RF02** | O sistema deve permitir que o Doador cadastre uma oferta de doação informando os alimentos, quantidade ou peso, data de validade, endereço de retirada e observações.    |
| **RF03** | O sistema deve permitir que o Receptor consulte ofertas disponíveis por localização e raio de distância, visualizando os dados da doação e sua validade.                 |
| **RF04** | O sistema deve permitir que o Receptor reserve uma oferta disponível e acompanhe o status da reserva até a retirada.                                                     |
| **RF05** | O sistema deve permitir que um Voluntário consulte e assuma solicitações de retirada, registrando a coleta e a entrega da doação.                                        |
| **RF06** | O sistema deve registrar o histórico das doações e calcular indicadores de quantidade de alimentos reaproveitados e estimativa de CO₂ evitado.                           |

Os requisitos acima são os mesmos definidos na primeira entrega do projeto.

---

# 📐 Regras de Negócio

### RN01 — Validade mínima

Uma oferta de doação deve possuir pelo menos **2 horas de validade restantes** no momento da publicação.

### RN02 — Limite de reservas

Um Receptor pode manter no máximo **3 reservas ativas simultaneamente**.

### RN03 — Exclusividade

Uma oferta que já foi reservada não pode ser reservada por outro Receptor.

### RN04 — Histórico

Doações concluídas devem permanecer registradas para histórico e auditoria.

### RN05 — Indicador ambiental

A estimativa de impacto ambiental deve ser calculada a partir do **peso dos alimentos registrados** no sistema.

---

# 🏗️ Arquitetura

O FoodLink será desenvolvido utilizando uma arquitetura de **Monolito Modular em Camadas**.

A aplicação será organizada nas seguintes camadas:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
Database
```

Também serão utilizadas entidades e DTOs para organização do domínio e transferência de dados.

A arquitetura foi escolhida para facilitar:

* Manutenção;
* Testes;
* Organização do código;
* Separação de responsabilidades;
* Evolução futura do sistema;
* Implementação dos requisitos funcionais.

---

# 💻 Tecnologias

| Tecnologia          | Utilização                                       |
| ------------------- | ------------------------------------------------ |
| **Java**            | Desenvolvimento do Backend                       |
| **PostgreSQL**      | Banco de dados                                   |
| **React**           | Desenvolvimento do Frontend                      |

As tecnologias foram escolhidas para atender aos requisitos de autenticação, ofertas, consultas por localização, reservas, logística e indicadores.

---

# 📂 Estrutura do projeto

```text
FOODLINK_AEP/
├── docs/
│   ├── Diagrama_Classe_FOODLINK.png
│   ├── DER_FOODLINK.png
│   └── Entrega_Bimestre_1.pdf
├── database/
│   ├── script_banco.sql
│   └── modelagem_der.sql
├── src/
│   ├── model/
│   ├── controller/
│   └── view/
└── README.md
```

A estrutura segue a organização definida para o repositório na primeira entrega.

---

# 🗄️ Banco de Dados

O FoodLink utilizará o **PostgreSQL com PostGIS**.

O PostgreSQL será responsável pela persistência dos dados da aplicação, enquanto o PostGIS será utilizado para as funcionalidades relacionadas à localização e busca de ofertas por proximidade.

Os scripts do banco de dados deverão permanecer na pasta:

```text
/database
```

Exemplo:

```text
database/
├── migrations/
└── scripts/
```

O projeto deve utilizar um banco de dados real para persistência das informações, conforme exigido pela AEP.

---

# 🔄 Git e Versionamento

O projeto utiliza **Git e GitHub** para controle de versão.

A branch principal é:

```text
main
```

Branches de desenvolvimento devem seguir o padrão:

```text
feature/nome-da-funcionalidade
```

Exemplos:

```text
feature/cadastro-usuario
feature/cadastro-doacao
feature/reserva-doacao
feature/logistica-retirada
```

---

# 📝 Padrão de Commits

A equipe utilizará preferencialmente o padrão **Conventional Commits**.

Exemplos:

```bash
git commit -m "feat: adiciona cadastro de doadores"
```

```bash
git commit -m "feat: implementa reserva de doacao"
```

```bash
git commit -m "fix: corrige busca por localizacao"
```

```bash
git commit -m "docs: atualiza README"
```

---

# 📅 Cronograma / Backlog

| Data                    | Atividade                                           | Responsável |
| ----------------------- | --------------------------------------------------- | ----------- |
| 06/09/2026 – 07/09/2026 | Levantamento do problema, stakeholders e requisitos | Diego       |
| 07/09/2026 – 08/09/2026 | Definição do escopo, regras de negócio e ODS        | Diego       |
| 09/09/2026 – 11/09/2026 | Modelagem UML e DER                                 | Ruan       |
| 10/09/2026 – 11/10/2026 | Configuração do repositório e estrutura inicial     | Ruan e Diego      |
| A Definir | Implementação do backend e banco de dados           | Arthur      |
| A Definir | Implementação do frontend e integração              | Arthur e Diego      |
| A Definir | Testes, correções e documentação                    | Equipe      |

O guia da AEP define o cronograma/backlog como parte do planejamento e solicita datas, atividades/stories e responsáveis.

---

# 🧩 Orientação a Objetos

O desenvolvimento deverá aplicar os principais conceitos de **Programação Orientada a Objetos (POO)**.

Serão utilizados:

* Classes;
* Objetos;
* Encapsulamento;
* Herança;
* Polimorfismo;
* Classes abstratas e/ou interfaces;
* Sobrescrita de métodos (`@Override`).

A implementação deverá demonstrar claramente herança e polimorfismo, conforme os critérios da AEP.

Um exemplo planejado de especialização é:

```text
Pessoa
 ├── Doador
 ├── Receptor
 └── Voluntario
```

Essa estrutura permite representar os diferentes perfis de usuário e aplicar os conceitos de herança e polimorfismo exigidos no projeto.

---

# 🧪 Testes

Durante o desenvolvimento serão realizados testes para verificar:

* Cadastro de usuários;
* Autenticação;
* Cadastro de ofertas;
* Consulta de ofertas;
* Busca por localização;
* Reservas;
* Controle de limite de reservas;
* Processo de retirada;
* Registro de entrega;
* Persistência no banco de dados;
* Cálculo dos indicadores.

---

# 📊 Impacto esperado

O FoodLink busca gerar impacto positivo por meio de:

### Social

Facilitar o encaminhamento de alimentos para organizações e pessoas que possam recebê-los.

### Ambiental

Reduzir o descarte de alimentos próprios para consumo e permitir o acompanhamento de indicadores ambientais.

### Logístico

Organizar o processo de retirada e entrega das doações.

### Tecnológico

Centralizar informações que poderiam estar dispersas em diferentes canais e tornar o processo mais rastreável.

---

# 📚 Documentação

Os documentos e diagramas do projeto serão armazenados em:

```text
/docs
```

Incluindo:

```text
docs/
├── diagrama-classes/
└── der/
```

O **Diagrama de Classes** representa a estrutura orientada a objetos do sistema, enquanto o **DER** representa a estrutura do banco de dados.

---

# 🚧 Status do projeto

**Em desenvolvimento — AEP 4º Semestre / 2026.2**

### Entrega 1

* [x] Definição do problema
* [x] Identificação dos stakeholders
* [x] Requisitos funcionais
* [x] Regras de negócio
* [x] Definição dos ODS
* [x] Definição da arquitetura
* [x] Definição das tecnologias
* [x] Cronograma
* [x] Estrutura inicial do GitHub
* [ ] Implementação do sistema

### Entrega 2

* [ ] Implementação do Backend
* [ ] Implementação do Frontend
* [ ] Implementação do Banco de Dados
* [ ] CRUD da entidade principal
* [ ] Herança
* [ ] Polimorfismo
* [ ] Integração Backend + Banco
* [ ] Testes
* [ ] Documentação final

A segunda entrega exige código-fonte completo, script do banco, aplicação dos pilares de POO, CRUD funcional e persistência em banco real.

---

# 👨‍💻 Equipe

| Integrante | Função          |
| ---------- | --------------- |
| Diego Brito    | Banco de dados e interfaces |
| Ruan Gabriel  | Modelagem POO |
| Arthur Morales  |  Backend (CRUD e persistência) |



---

# 📄 Projeto Acadêmico

**Projeto Bimestral — AEP 4º Semestre**

**Curso:** Engenharia de Software

**Projeto:** FoodLink — Plataforma Inteligente de Redução de Desperdício Alimentar e Logística Solidária

**Ano:** 2026

---

## 📌 Observação

Este projeto possui finalidade acadêmica e está sendo desenvolvido como parte da AEP. O repositório deverá registrar a evolução do projeto por meio dos commits dos integrantes da equipe.

O histórico do GitHub é utilizado para demonstrar a participação e evolução do trabalho colaborativo.
