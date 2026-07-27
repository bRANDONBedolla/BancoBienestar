package com.bedolla.bancobienestar.entity;

public class GastosDTO {

    private String categoria;
    private Double monto;
    private String colorHex;

    public GastosDTO() {
    }

    public GastosDTO(String categoria, Double monto, String colorHex) {
        this.categoria = categoria;
        this.monto = monto;
        this.colorHex = colorHex;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public Double getMonto() {
        return monto;
    }

    public void setMonto(Double monto) {
        this.monto = monto;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }
}
