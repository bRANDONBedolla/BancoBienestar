package com.bedolla.bancobienestar.exception;

/**
 * Se lanza cuando una cuenta no tiene saldo suficiente para completar
 * una operación (transferencia, pago de servicio, retiro, etc.).
 *
 * Al ser una RuntimeException, no obliga a los controllers a declarar
 * "throws", pero @Transactional(rollbackFor = Exception.class) en
 * BancaService asegura que cualquier cargo ya aplicado se revierta.
 */
public class FondosInsuficientesException extends RuntimeException {

    public FondosInsuficientesException(String mensaje) {
        super(mensaje);
    }

    public FondosInsuficientesException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
