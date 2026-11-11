-- =====================================================================
-- FoodLink - Script ÚNICO do banco de dados
-- SGBD: PostgreSQL 12+  (nos testes automatizados roda em H2 modo PostgreSQL)
-- Uso:  psql -U postgres -d foodlink -f database/foodlink.sql
-- Contém: 6 tabelas + 1 view + dados de exemplo (senha de todos: 1234)
-- =====================================================================

-- ---------- Limpeza (permite reexecutar o script) ----------
DROP VIEW  IF EXISTS vw_historico_doacoes;
DROP TABLE IF EXISTS indicador_impacto;
DROP TABLE IF EXISTS retirada;
DROP TABLE IF EXISTS reserva;
DROP TABLE IF EXISTS oferta_doacao;
DROP TABLE IF EXISTS usuario;
DROP TABLE IF EXISTS endereco;

-- ---------- ENDERECO (classe Endereco) ----------
CREATE TABLE endereco (
    id        UUID PRIMARY KEY,
    rua       VARCHAR(150) NOT NULL,
    cidade    VARCHAR(100) NOT NULL,
    cep       VARCHAR(10)  NOT NULL,
    latitude  DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL
);

-- ---------- USUARIO (classe abstrata Usuario + herança Doador/Receptor/Voluntario) ----------
-- Tabela única com discriminador "tipo" (single table inheritance).
CREATE TABLE usuario (
    id                     UUID PRIMARY KEY,
    tipo                   VARCHAR(20)  NOT NULL CHECK (tipo IN ('DOADOR', 'RECEPTOR', 'VOLUNTARIO')),
    nome                   VARCHAR(120) NOT NULL,
    email                  VARCHAR(150) NOT NULL UNIQUE,
    documento              VARCHAR(30)  NOT NULL UNIQUE,
    senha_hash             VARCHAR(128) NOT NULL,
    data_cadastro          TIMESTAMP    NOT NULL,
    limite_reservas_ativas INTEGER
);

-- ---------- OFERTA_DOACAO (entidade principal do CRUD) ----------
CREATE TABLE oferta_doacao (
    id            UUID PRIMARY KEY,
    doador_id     UUID NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    endereco_id   UUID NOT NULL REFERENCES endereco (id),
    alimento      VARCHAR(120) NOT NULL,
    quantidade    NUMERIC(10, 2) NOT NULL CHECK (quantidade > 0),
    peso          NUMERIC(10, 2) NOT NULL CHECK (peso > 0),
    data_validade TIMESTAMP NOT NULL,
    observacoes   VARCHAR(255),
    status        VARCHAR(20) NOT NULL
                  CHECK (status IN ('DISPONIVEL', 'RESERVADA', 'CONCLUIDA', 'EXPIRADA'))
);

CREATE INDEX idx_oferta_status   ON oferta_doacao (status);
CREATE INDEX idx_oferta_doador   ON oferta_doacao (doador_id);
CREATE INDEX idx_oferta_validade ON oferta_doacao (data_validade);

-- ---------- RESERVA (RF04 / RN02 / RN03) ----------
CREATE TABLE reserva (
    id           UUID PRIMARY KEY,
    oferta_id    UUID NOT NULL REFERENCES oferta_doacao (id) ON DELETE CASCADE,
    receptor_id  UUID NOT NULL REFERENCES usuario (id),
    data_reserva TIMESTAMP NOT NULL,
    status       VARCHAR(20) NOT NULL CHECK (status IN ('ATIVA', 'CANCELADA', 'CONCLUIDA'))
);

CREATE INDEX idx_reserva_oferta          ON reserva (oferta_id);
CREATE INDEX idx_reserva_receptor_status ON reserva (receptor_id, status);

-- ---------- RETIRADA (RF05) ----------
CREATE TABLE retirada (
    id            UUID PRIMARY KEY,
    reserva_id    UUID NOT NULL UNIQUE REFERENCES reserva (id) ON DELETE CASCADE,
    voluntario_id UUID NOT NULL REFERENCES usuario (id),
    data_coleta   TIMESTAMP,
    data_entrega  TIMESTAMP,
    status        VARCHAR(20) NOT NULL CHECK (status IN ('PENDENTE', 'EM_ANDAMENTO', 'ENTREGUE'))
);

CREATE INDEX idx_retirada_voluntario ON retirada (voluntario_id);

-- ---------- INDICADOR_IMPACTO (RF06 / RN05) ----------
CREATE TABLE indicador_impacto (
    id             UUID PRIMARY KEY,
    oferta_id      UUID NOT NULL UNIQUE REFERENCES oferta_doacao (id) ON DELETE CASCADE,
    peso_total_kg  NUMERIC(10, 2) NOT NULL,
    co2_evitado_kg NUMERIC(10, 2) NOT NULL,
    data_calculo   TIMESTAMP NOT NULL
);

-- ---------- VIEW de apoio ao relatório (RF06) ----------
CREATE VIEW vw_historico_doacoes AS
SELECT o.id AS oferta_id, o.alimento, o.peso,
       d.nome AS doador, r.nome AS receptor, v.nome AS voluntario, t.data_entrega
FROM retirada t
JOIN reserva rs      ON rs.id = t.reserva_id
JOIN oferta_doacao o ON o.id = rs.oferta_id
JOIN usuario d       ON d.id = o.doador_id
JOIN usuario r       ON r.id = rs.receptor_id
JOIN usuario v       ON v.id = t.voluntario_id
WHERE t.status = 'ENTREGUE';

-- =====================================================================
-- DADOS DE EXEMPLO (senha de todos: 1234)
-- =====================================================================
INSERT INTO usuario (id, tipo, nome, email, documento, senha_hash, data_cadastro, limite_reservas_ativas)
VALUES
 ('11111111-1111-1111-1111-111111111111', 'DOADOR', 'Padaria Central', 'padaria@foodlink.com',
  '12.345.678/0001-99', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4', CURRENT_TIMESTAMP, NULL),
 ('22222222-2222-2222-2222-222222222222', 'RECEPTOR', 'ONG Mesa Cheia', 'ong@foodlink.com',
  '98.765.432/0001-11', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4', CURRENT_TIMESTAMP, 3),
 ('33333333-3333-3333-3333-333333333333', 'VOLUNTARIO', 'Ana Souza', 'ana@foodlink.com',
  '111.222.333-44', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4', CURRENT_TIMESTAMP, NULL);

INSERT INTO endereco (id, rua, cidade, cep, latitude, longitude)
VALUES
 ('44444444-4444-4444-4444-444444444444', 'Rua das Flores, 100', 'Cianorte', '87200-000', -23.6636, -52.6053),
 ('55555555-5555-5555-5555-555555555555', 'Av. Brasil, 1500', 'Cianorte', '87200-100', -23.6712, -52.6120);

INSERT INTO oferta_doacao (id, doador_id, endereco_id, alimento, quantidade, peso, data_validade, observacoes, status)
VALUES
 ('66666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111',
  '44444444-4444-4444-4444-444444444444', 'Pães e massas', 20, 8.5,
  CURRENT_TIMESTAMP + INTERVAL '2' DAY, 'Retirar até as 18h', 'DISPONIVEL'),
 ('77777777-7777-7777-7777-777777777777', '11111111-1111-1111-1111-111111111111',
  '55555555-5555-5555-5555-555555555555', 'Frutas variadas', 15, 12.0,
  CURRENT_TIMESTAMP + INTERVAL '3' DAY, 'Caixas fechadas', 'DISPONIVEL');
