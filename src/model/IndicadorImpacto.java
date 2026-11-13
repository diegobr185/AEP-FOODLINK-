package model;

/** Totais das doações que informaram o peso. */
public class IndicadorImpacto {

    private final float pesoTotalKg;
    private final float co2EvitadoKg;

    public IndicadorImpacto(float pesoTotalKg, float co2EvitadoKg) {
        this.pesoTotalKg = pesoTotalKg;
        this.co2EvitadoKg = co2EvitadoKg;
    }

    public float getPesoTotalKg() { return pesoTotalKg; }
    public float getCo2EvitadoKg() { return co2EvitadoKg; }
}
