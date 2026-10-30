PRAGMA foreign_keys = ON;

-- ============================================================
-- Cliente
--   El id es el número de celular normalizado (solo dígitos, con
--   indicativo, ej. "573001234567"). Se toma de los contactos del
--   teléfono o se digita a mano (alta manual / contacto sin número).
--   El nombre se refresca desde contactos al abrir la app si el
--   cliente tiene contactoUri y el permiso está concedido.
--   "Eliminar" = estado 'inactivo' (se conservan deudas y abonos).
--   Si se vuelve a agregar el mismo celular, se reactiva.
-- ============================================================
CREATE TABLE cliente (
    idCliente         TEXT    PRIMARY KEY,                    -- celular normalizado
    nombre            TEXT    NOT NULL,
    telefonoOriginal  TEXT,                                   -- como venía en el contacto (para mostrar)
    contactoUri       TEXT,                                   -- lookup URI del contacto; NULL si fue alta manual
    origen            TEXT    NOT NULL DEFAULT 'contacto'
                              CHECK (origen IN ('contacto','manual')),
    estado            TEXT    NOT NULL DEFAULT 'activo'
                              CHECK (estado IN ('activo','inactivo')),
    fechaCreacion     TEXT    NOT NULL DEFAULT (datetime('now')),
    fechaModificacion TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================
-- Deuda: nombre + descripcion + valor.
--   saldo y estado son DERIVADOS de los abonos (ver triggers):
--     saldo  = valor - SUM(abono.valor)
--     estado = 'pagada' si saldo = 0, 'pendiente' si saldo > 0,
--              'anulada' se fija manualmente y los triggers la respetan.
--   "Marcar pagada" = insertar un abono por el saldo restante (tipo 'cierre').
--   "Reabrir"       = anular el último abono activo (o subir el valor).
--   "Eliminar"      = estado 'anulada' (se conserva el historial).
-- ============================================================
CREATE TABLE deuda (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    idCliente         TEXT    NOT NULL REFERENCES cliente(idCliente) ON DELETE CASCADE,
    nombre            TEXT    NOT NULL,
    descripcion       TEXT,
    valor             REAL    NOT NULL CHECK (valor > 0),
    saldo             REAL    NOT NULL CHECK (saldo >= 0),
    estado            TEXT    NOT NULL DEFAULT 'pendiente'
                              CHECK (estado IN ('pendiente','pagada','anulada')),
    fechaCreacion     TEXT    NOT NULL DEFAULT (datetime('now')),
    fechaPago         TEXT,                                   -- se llena cuando saldo llega a 0
    fechaAnulacion    TEXT,
    fechaModificacion TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================
-- Pago: abono general del cliente, repartido entre sus deudas.
--   valor = lo que entregó el cliente. Lo aplicado es SUM(abonos activos)
--   y la diferencia es su saldo a favor (ver vw_pago); ninguno de los dos
--   se escribe desde Kotlin. Si se anula un abono suelto, su monto vuelve
--   a favor; el pago no se anula solo aunque se quede sin abonos.
--   Nada se borra: "Eliminar" = estado 'anulado' (anula también sus abonos,
--   trg_pago_anular, y su saldo a favor desaparece).
-- ============================================================
CREATE TABLE pago (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    idCliente      TEXT    NOT NULL REFERENCES cliente(idCliente) ON DELETE CASCADE,
    valor          REAL    NOT NULL CHECK (valor > 0),
    fecha          TEXT    NOT NULL DEFAULT (datetime('now')),
    nota           TEXT,
    estado         TEXT    NOT NULL DEFAULT 'activo'
                           CHECK (estado IN ('activo','anulado')),
    fechaAnulacion TEXT
);

-- ============================================================
-- Abono: pago parcial o total sobre UNA deuda. Valor libre.
--   idPago NULL = abono directo; con valor = parte de un pago general.
--   Nada se borra: "Eliminar"/"Reabrir" = estado 'anulado'. Solo los
--   abonos activos cuentan en saldo, totales y vistas.
-- ============================================================
CREATE TABLE abono (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    idDeuda     INTEGER NOT NULL REFERENCES deuda(id) ON DELETE CASCADE,
    valor       REAL    NOT NULL CHECK (valor > 0),
    tipo        TEXT    NOT NULL DEFAULT 'abono'
                        CHECK (tipo IN ('abono','cierre')),  -- 'cierre' = generado por "Marcar pagada"
    fecha       TEXT    NOT NULL DEFAULT (datetime('now')),
    nota        TEXT,
    idPago      INTEGER REFERENCES pago(id) ON DELETE CASCADE,
    estado      TEXT    NOT NULL DEFAULT 'activo'
                        CHECK (estado IN ('activo','anulado')),
    fechaAnulacion TEXT
);

-- ============================================================
-- Índices
-- ============================================================
CREATE INDEX idx_deuda_cliente_estado ON deuda(idCliente, estado);
CREATE INDEX idx_abono_deuda          ON abono(idDeuda, estado);
CREATE INDEX idx_abono_pago           ON abono(idPago);
CREATE INDEX idx_pago_cliente         ON pago(idCliente);

-- ============================================================
-- Triggers
-- ============================================================

-- fechaModificacion
CREATE TRIGGER trg_cliente_mod AFTER UPDATE ON cliente
BEGIN
    UPDATE cliente SET fechaModificacion = datetime('now') WHERE idCliente = NEW.idCliente;
END;

-- No permitir abonar más que el saldo
CREATE TRIGGER trg_abono_no_excede BEFORE INSERT ON abono
BEGIN
    SELECT CASE
        WHEN (SELECT estado FROM deuda WHERE id = NEW.idDeuda) = 'anulada'
            THEN RAISE(ABORT, 'La deuda está anulada')
        WHEN NEW.valor > (SELECT saldo FROM deuda WHERE id = NEW.idDeuda) + 0.0001
            THEN RAISE(ABORT, 'El abono supera el saldo de la deuda')
    END;
END;

-- Recalcular saldo/estado al insertar o anular abonos (solo cuentan los activos)
CREATE TRIGGER trg_abono_ins AFTER INSERT ON abono
BEGIN
    UPDATE deuda SET
        saldo  = valor - COALESCE((SELECT SUM(valor) FROM abono
                                   WHERE idDeuda = NEW.idDeuda AND estado = 'activo'), 0),
        fechaModificacion = datetime('now')
    WHERE id = NEW.idDeuda;
    UPDATE deuda SET
        estado    = CASE WHEN saldo <= 0.0001 THEN 'pagada' ELSE 'pendiente' END,
        saldo     = CASE WHEN saldo <= 0.0001 THEN 0 ELSE saldo END,
        fechaPago = CASE WHEN saldo <= 0.0001 THEN datetime('now') ELSE NULL END
    WHERE id = NEW.idDeuda AND estado != 'anulada';
END;

-- Los abonos de un pago general no pueden sumar más que lo que entregó el cliente
CREATE TRIGGER trg_abono_no_excede_pago BEFORE INSERT ON abono
WHEN NEW.idPago IS NOT NULL
BEGIN
    SELECT CASE
        WHEN (SELECT estado FROM pago WHERE id = NEW.idPago) = 'anulado'
            THEN RAISE(ABORT, 'El pago está anulado')
        WHEN NEW.valor > (SELECT valor FROM pago WHERE id = NEW.idPago)
                         - COALESCE((SELECT SUM(valor) FROM abono
                                     WHERE idPago = NEW.idPago AND estado = 'activo'), 0)
                         + 0.0001
            THEN RAISE(ABORT, 'El abono supera el valor del pago')
    END;
END;

-- Un abono anulado no se reactiva: podría superar el saldo actual
CREATE TRIGGER trg_abono_no_reactivar BEFORE UPDATE OF estado ON abono
WHEN OLD.estado = 'anulado' AND NEW.estado != 'anulado'
BEGIN
    SELECT RAISE(ABORT, 'El abono está anulado');
END;

CREATE TRIGGER trg_abono_anular AFTER UPDATE OF estado ON abono
WHEN NEW.estado = 'anulado' AND OLD.estado = 'activo'
BEGIN
    UPDATE deuda SET
        saldo  = valor - COALESCE((SELECT SUM(valor) FROM abono
                                   WHERE idDeuda = NEW.idDeuda AND estado = 'activo'), 0),
        fechaModificacion = datetime('now')
    WHERE id = NEW.idDeuda;
    UPDATE deuda SET
        estado    = CASE WHEN saldo <= 0.0001 THEN 'pagada' ELSE 'pendiente' END,
        fechaPago = CASE WHEN saldo <= 0.0001 THEN fechaPago ELSE NULL END
    WHERE id = NEW.idDeuda AND estado != 'anulada';
END;

-- Al editar el valor de la deuda, recalcular (puede reabrir una pagada)
CREATE TRIGGER trg_deuda_valor_upd AFTER UPDATE OF valor ON deuda
BEGIN
    SELECT CASE
        WHEN NEW.valor < COALESCE((SELECT SUM(valor) FROM abono
                                   WHERE idDeuda = NEW.id AND estado = 'activo'), 0) - 0.0001
            THEN RAISE(ABORT, 'El valor no puede ser menor que lo ya abonado')
    END;
    UPDATE deuda SET
        saldo = NEW.valor - COALESCE((SELECT SUM(valor) FROM abono
                                      WHERE idDeuda = NEW.id AND estado = 'activo'), 0),
        fechaModificacion = datetime('now')
    WHERE id = NEW.id;
    UPDATE deuda SET
        estado    = CASE WHEN saldo <= 0.0001 THEN 'pagada' ELSE 'pendiente' END,
        saldo     = CASE WHEN saldo <= 0.0001 THEN 0 ELSE saldo END,
        fechaPago = CASE WHEN saldo <= 0.0001 THEN COALESCE(fechaPago, datetime('now')) ELSE NULL END
    WHERE id = NEW.id AND estado != 'anulada';
END;

-- Anular un pago anula todos sus abonos activos (y trg_abono_anular recalcula)
CREATE TRIGGER trg_pago_anular AFTER UPDATE OF estado ON pago
WHEN NEW.estado = 'anulado' AND OLD.estado = 'activo'
BEGIN
    UPDATE abono SET estado = 'anulado', fechaAnulacion = datetime('now')
    WHERE idPago = NEW.id AND estado = 'activo';
END;

-- Al anular una deuda, el dinero que le llegó de pagos generales vuelve a
-- favor del cliente: se anulan esos abonos (los directos no se tocan).
CREATE TRIGGER trg_deuda_anular_libera_pagos AFTER UPDATE OF estado ON deuda
WHEN NEW.estado = 'anulada' AND OLD.estado != 'anulada'
BEGIN
    UPDATE abono SET estado = 'anulado', fechaAnulacion = datetime('now')
    WHERE idDeuda = NEW.id AND idPago IS NOT NULL AND estado = 'activo';
END;

CREATE TRIGGER trg_deuda_mod AFTER UPDATE OF nombre, descripcion, estado ON deuda
BEGIN
    UPDATE deuda SET fechaModificacion = datetime('now') WHERE id = NEW.id;
END;

-- ============================================================
-- Vista: pago con lo aplicado, su saldo a favor y a cuántas deudas llegó.
--   saldoFavor = valor recibido - abonos activos (0 si el pago está anulado).
-- ============================================================
CREATE VIEW vw_pago AS
SELECT
    p.id,
    p.idCliente,
    p.valor,
    p.fecha,
    p.nota,
    p.estado,
    p.fechaAnulacion,
    COALESCE(SUM(a.valor), 0)  AS aplicado,
    CASE WHEN p.estado = 'activo' THEN p.valor - COALESCE(SUM(a.valor), 0) ELSE 0 END
                               AS saldoFavor,
    COUNT(DISTINCT a.idDeuda)  AS numDeudas
FROM pago p
LEFT JOIN abono a ON a.idPago = p.id AND a.estado = 'activo'
GROUP BY p.id;

-- ============================================================
-- Vista: resumen por cliente (lista principal)
-- ============================================================
CREATE VIEW vw_resumen_cliente AS
SELECT
    c.idCliente,
    c.nombre,
    c.telefonoOriginal,
    c.contactoUri,
    c.origen,
    c.estado,
    COUNT(CASE WHEN d.estado != 'anulada' THEN d.id END)              AS totalDeudas,
    SUM(CASE WHEN d.estado = 'pendiente' THEN 1 ELSE 0 END)           AS deudasPendientes,
    COALESCE(SUM(CASE WHEN d.estado != 'anulada' THEN d.valor END), 0) AS valorTotal,
    COALESCE(SUM(CASE WHEN d.estado = 'pendiente' THEN d.saldo END), 0) AS saldoPendiente,
    COALESCE(SUM(CASE WHEN d.estado != 'anulada' THEN d.valor - d.saldo END), 0) AS totalPagado,
    COALESCE((SELECT SUM(saldoFavor) FROM vw_pago v
              WHERE v.idCliente = c.idCliente), 0)                     AS saldoFavor
FROM cliente c
LEFT JOIN deuda d ON d.idCliente = c.idCliente
GROUP BY c.idCliente;

-- ============================================================
-- Vista: deuda con su total abonado y cantidad de abonos
-- ============================================================
CREATE VIEW vw_deuda_detalle AS
SELECT
    d.*,
    COALESCE(a.totalAbonado, 0) AS totalAbonado,
    COALESCE(a.numAbonos, 0)    AS numAbonos
FROM deuda d
LEFT JOIN (
    SELECT idDeuda, SUM(valor) AS totalAbonado, COUNT(*) AS numAbonos
    FROM abono WHERE estado = 'activo' GROUP BY idDeuda
) a ON a.idDeuda = d.id;
