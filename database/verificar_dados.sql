-- Diagnóstico SOMENTE DE LEITURA para bancos criados pela versão anterior.
-- Execute após fazer backup e antes de aplicar database/foodlink.sql.
-- Cada consulta deve retornar zero linhas. Se aparecer algo, revise o caso
-- com os envolvidos antes de alterar dados; o script não faz correções automáticas.

-- 1. Uma oferta não pode ter duas reservas ativas.
SELECT r.oferta_id, COUNT(*) AS reservas_ativas
FROM reserva r
WHERE r.status = 'ATIVA'
GROUP BY r.oferta_id
HAVING COUNT(*) > 1;

-- 2. Reserva, oferta e retirada precisam ter estados compatíveis.
SELECT r.id AS reserva_id, r.status AS status_reserva,
       o.id AS oferta_id, o.status AS status_oferta,
       t.id AS retirada_id, t.status AS status_retirada
FROM reserva r
JOIN oferta_doacao o ON o.id = r.oferta_id
LEFT JOIN retirada t ON t.reserva_id = r.id
WHERE (r.status = 'ATIVA' AND o.status <> 'RESERVADA')
   OR (r.status = 'CONCLUIDA' AND (o.status <> 'CONCLUIDA' OR t.status IS DISTINCT FROM 'ENTREGUE'))
   OR (r.status = 'CANCELADA' AND t.id IS NOT NULL)
   OR (t.status = 'ENTREGUE' AND r.status <> 'CONCLUIDA')
   OR (t.status IN ('PENDENTE', 'EM_ANDAMENTO') AND r.status <> 'ATIVA');

-- 3. Oferta reservada sem uma reserva ativa correspondente.
SELECT o.id, o.alimento, o.status
FROM oferta_doacao o
WHERE o.status = 'RESERVADA'
  AND NOT EXISTS (SELECT 1 FROM reserva r WHERE r.oferta_id = o.id AND r.status = 'ATIVA');

-- 4. Uma conclusão deve ter entrega e indicador de impacto.
SELECT o.id, o.alimento, o.status
FROM oferta_doacao o
WHERE o.status = 'CONCLUIDA'
  AND (
      NOT EXISTS (SELECT 1 FROM reserva r JOIN retirada t ON t.reserva_id = r.id
                  WHERE r.oferta_id = o.id AND r.status = 'CONCLUIDA' AND t.status = 'ENTREGUE')
      OR NOT EXISTS (SELECT 1 FROM indicador_impacto i WHERE i.oferta_id = o.id)
  );

-- 5. Indicador sem oferta concluída.
SELECT i.id AS indicador_id, o.id AS oferta_id, o.status AS status_oferta
FROM indicador_impacto i
JOIN oferta_doacao o ON o.id = i.oferta_id
WHERE o.status <> 'CONCLUIDA';
