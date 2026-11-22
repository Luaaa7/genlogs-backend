package com.genlogs.app.service;

/**
 * BPMN 10-F (v3.4) · Factiliza rechazó la clave (plan vencido o token inválido). Ya se avisó
 * al Jefe Administrativo con el aviso de Factiliza: quien la recibe no debe avisar otra vez.
 */
public class FactilizaSinPlanException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final int codigoHttp;

    public FactilizaSinPlanException(int codigoHttp) {
        super("Factiliza rechazó la clave (HTTP " + codigoHttp + ")");
        this.codigoHttp = codigoHttp;
    }

    public int getCodigoHttp() {
        return codigoHttp;
    }
}
