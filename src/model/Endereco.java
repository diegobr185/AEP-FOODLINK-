package model;

import java.util.UUID;

/** Endereco — coordenadas usadas no cálculo de distância (RF03). */
public class Endereco {

    private UUID id;
    private String rua;
    private String cidade;
    private String cep;
    private float latitude;
    private float longitude;

    public Endereco(String rua, String cidade, String cep, float latitude, float longitude) {
        this(UUID.randomUUID(), rua, cidade, cep, latitude, longitude);
    }

    public Endereco(UUID id, String rua, String cidade, String cep, float latitude, float longitude) {
        this.id = id;
        this.rua = rua;
        this.cidade = cidade;
        this.cep = cep;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public UUID getId() { return id; }
    public String getRua() { return rua; }
    public String getCidade() { return cidade; }
    public String getCep() { return cep; }
    public float getLatitude() { return latitude; }
    public float getLongitude() { return longitude; }

    @Override
    public String toString() {
        return cidade + " - " + rua;
    }
}
