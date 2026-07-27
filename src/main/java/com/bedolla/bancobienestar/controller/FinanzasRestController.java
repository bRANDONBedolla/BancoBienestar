package com.bedolla.bancobienestar.controller;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bedolla.bancobienestar.entity.GastosDTO;

@RestController
@RequestMapping("/api/v1/finanzas")
public class FinanzasRestController {

    @GetMapping("/gastos-mes")
    public List<GastosDTO> obtenerGastos() {
        return Arrays.asList(
            new GastosDTO("Alimentos", 350.00, "#FF5733"),
            new GastosDTO("vivienda", 1200.00, "#33FF57"),
            new GastosDTO("Transporte", 200.00, "#3357FF"),
            new GastosDTO("Otros", 2000.00, "#F1C40F")
        );
    }
}
