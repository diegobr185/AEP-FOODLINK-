-- =====================================================================
-- FoodLink - Script ÚNICO do banco de dados
-- SGBD: PostgreSQL 12+
-- Contém as tabelas e dados de exemplo (senha de todos: 1234)
-- Reexecutável sem apagar dados. Use os exemplos apenas em ambiente de teste.
-- =====================================================================

-- O arquivo está em UTF-8, inclusive os dados de exemplo com acentos.
SET client_encoding = 'UTF8';

-- Todas as alterações são confirmadas juntas; em caso de erro, são desfeitas.
BEGIN;

-- ---------- ENDERECO (classe Endereco) ----------
CREATE TABLE IF NOT EXISTS endereco (
    id        UUID PRIMARY KEY,
    rua       VARCHAR(150) NOT NULL,
    cidade    VARCHAR(100) NOT NULL,
    cep       VARCHAR(10)  NOT NULL,
    latitude  DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL
);

-- ---------- USUARIO (classe abstrata Usuario + herança Doador/Receptor/Voluntario) ----------
-- Tabela única com discriminador "tipo" (single table inheritance).
CREATE TABLE IF NOT EXISTS usuario (
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
CREATE TABLE IF NOT EXISTS oferta_doacao (
    id            UUID PRIMARY KEY,
    doador_id     UUID NOT NULL REFERENCES usuario (id),
    endereco_id   UUID NOT NULL REFERENCES endereco (id),
    alimento      VARCHAR(120) NOT NULL,
    quantidade    NUMERIC(10, 2) CHECK (quantidade > 0),
    peso          NUMERIC(10, 2) CHECK (peso > 0),
    data_validade TIMESTAMP NOT NULL,
    observacoes   VARCHAR(255),
    status        VARCHAR(20) NOT NULL
                  CHECK (status IN ('DISPONIVEL', 'RESERVADA', 'CONCLUIDA', 'EXPIRADA')),
    CONSTRAINT oferta_quantidade_ou_peso CHECK (quantidade IS NOT NULL OR peso IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_oferta_status   ON oferta_doacao (status);
CREATE INDEX IF NOT EXISTS idx_oferta_doador   ON oferta_doacao (doador_id);
CREATE INDEX IF NOT EXISTS idx_oferta_validade ON oferta_doacao (data_validade);

-- ---------- RESERVA (RF04 / RN02 / RN03) ----------
CREATE TABLE IF NOT EXISTS reserva (
    id           UUID PRIMARY KEY,
    oferta_id    UUID NOT NULL REFERENCES oferta_doacao (id),
    receptor_id  UUID NOT NULL REFERENCES usuario (id),
    data_reserva TIMESTAMP NOT NULL,
    status       VARCHAR(20) NOT NULL CHECK (status IN ('ATIVA', 'CANCELADA', 'CONCLUIDA'))
);

CREATE INDEX IF NOT EXISTS idx_reserva_oferta          ON reserva (oferta_id);
CREATE INDEX IF NOT EXISTS idx_reserva_receptor_status ON reserva (receptor_id, status);

-- ---------- RETIRADA (RF05) ----------
CREATE TABLE IF NOT EXISTS retirada (
    id            UUID PRIMARY KEY,
    reserva_id    UUID NOT NULL UNIQUE REFERENCES reserva (id),
    voluntario_id UUID NOT NULL REFERENCES usuario (id),
    data_coleta   TIMESTAMP,
    data_entrega  TIMESTAMP,
    status        VARCHAR(20) NOT NULL CHECK (status IN ('PENDENTE', 'EM_ANDAMENTO', 'ENTREGUE'))
);

CREATE INDEX IF NOT EXISTS idx_retirada_voluntario ON retirada (voluntario_id);

-- ---------- INDICADOR_IMPACTO (RF06 / RN05) ----------
CREATE TABLE IF NOT EXISTS indicador_impacto (
    id             UUID PRIMARY KEY,
    oferta_id      UUID NOT NULL UNIQUE REFERENCES oferta_doacao (id),
    peso_total_kg  NUMERIC(10, 2),
    co2_evitado_kg NUMERIC(10, 2),
    data_calculo   TIMESTAMP NOT NULL
);

-- Permite informar quantidade ou peso também em bancos da versão anterior.
ALTER TABLE oferta_doacao ALTER COLUMN quantidade DROP NOT NULL;
ALTER TABLE oferta_doacao ALTER COLUMN peso DROP NOT NULL;
ALTER TABLE oferta_doacao DROP CONSTRAINT IF EXISTS oferta_quantidade_ou_peso;
ALTER TABLE oferta_doacao ADD CONSTRAINT oferta_quantidade_ou_peso
    CHECK (quantidade IS NOT NULL OR peso IS NOT NULL);
ALTER TABLE indicador_impacto ALTER COLUMN peso_total_kg DROP NOT NULL;
ALTER TABLE indicador_impacto ALTER COLUMN co2_evitado_kg DROP NOT NULL;

-- Protege histórico também se o banco já foi criado pela versão anterior.
-- A troca das chaves não apaga registros e acontece na transação acima.
ALTER TABLE oferta_doacao DROP CONSTRAINT IF EXISTS oferta_doacao_doador_id_fkey;
ALTER TABLE oferta_doacao ADD CONSTRAINT oferta_doacao_doador_id_fkey
    FOREIGN KEY (doador_id) REFERENCES usuario (id);
ALTER TABLE reserva DROP CONSTRAINT IF EXISTS reserva_oferta_id_fkey;
ALTER TABLE reserva ADD CONSTRAINT reserva_oferta_id_fkey
    FOREIGN KEY (oferta_id) REFERENCES oferta_doacao (id);
ALTER TABLE retirada DROP CONSTRAINT IF EXISTS retirada_reserva_id_fkey;
ALTER TABLE retirada ADD CONSTRAINT retirada_reserva_id_fkey
    FOREIGN KEY (reserva_id) REFERENCES reserva (id);
ALTER TABLE indicador_impacto DROP CONSTRAINT IF EXISTS indicador_impacto_oferta_id_fkey;
ALTER TABLE indicador_impacto ADD CONSTRAINT indicador_impacto_oferta_id_fkey
    FOREIGN KEY (oferta_id) REFERENCES oferta_doacao (id);

-- RN03: uma oferta não pode ter duas reservas ativas ao mesmo tempo.
-- Se houver duplicatas antigas, corrija-as antes de executar o script.
CREATE UNIQUE INDEX IF NOT EXISTS idx_reserva_ativa_por_oferta
    ON reserva (oferta_id) WHERE status = 'ATIVA';

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
  '111.222.333-44', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4', CURRENT_TIMESTAMP, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO endereco (id, rua, cidade, cep, latitude, longitude)
VALUES
 ('44444444-4444-4444-4444-444444444444', 'Rua das Flores, 100', 'Cianorte', '87200-000', -23.6636, -52.6053),
 ('55555555-5555-5555-5555-555555555555', 'Av. Brasil, 1500', 'Cianorte', '87200-100', -23.6712, -52.6120)
ON CONFLICT DO NOTHING;

-- Só insere ofertas de exemplo se o doador e endereço correspondentes existirem.
-- Assim, um e-mail de exemplo já usado por outra conta não impede a migração.
INSERT INTO oferta_doacao (id, doador_id, endereco_id, alimento, quantidade, peso, data_validade, observacoes, status)
SELECT exemplo.* FROM (VALUES
 (UUID '66666666-6666-6666-6666-666666666666', UUID '11111111-1111-1111-1111-111111111111',
  UUID '44444444-4444-4444-4444-444444444444', 'Pães e massas', 20, 8.5,
  CURRENT_TIMESTAMP + INTERVAL '2' DAY, 'Retirar até as 18h', 'DISPONIVEL'),
 (UUID '77777777-7777-7777-7777-777777777777', UUID '11111111-1111-1111-1111-111111111111',
  UUID '55555555-5555-5555-5555-555555555555', 'Frutas variadas', 15, 12.0,
  CURRENT_TIMESTAMP + INTERVAL '3' DAY, 'Caixas fechadas', 'DISPONIVEL')
) AS exemplo(id, doador_id, endereco_id, alimento, quantidade, peso, data_validade, observacoes, status)
WHERE EXISTS (SELECT 1 FROM usuario u WHERE u.id = exemplo.doador_id AND u.tipo = 'DOADOR')
  AND EXISTS (SELECT 1 FROM endereco e WHERE e.id = exemplo.endereco_id)
ON CONFLICT DO NOTHING;

COMMIT;
