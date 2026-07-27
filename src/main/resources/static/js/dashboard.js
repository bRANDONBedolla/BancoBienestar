/**
 * dashboard.js
 * Carga la gráfica de "Gastos por categoría" desde la API de finanzas
 * y la renderiza con Chart.js dentro de #graficaGastos.
 */
(function () {
    "use strict";

    const ENDPOINT = "/api/v1/finanzas/gastos-mes";

    function formatoMoneda(valor) {
        return "$" + Number(valor).toLocaleString("es-MX", { minimumFractionDigits: 2 });
    }

    function construirGrafica(datos) {
        const canvas = document.getElementById("graficaGastos");
        if (!canvas) return;

        const etiquetas = datos.map((item) => item.categoria);
        const valores = datos.map((item) => item.monto);
        const colores = datos.map((item) => item.colorHex);

        new Chart(canvas.getContext("2d"), {
            type: "bar",
            data: {
                labels: etiquetas,
                datasets: [{
                    label: "Gasto ($)",
                    data: valores,
                    backgroundColor: colores,
                    borderRadius: 8,
                    maxBarThickness: 48
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: (contexto) => formatoMoneda(contexto.parsed.y)
                        }
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        grid: { color: "#eef1f6" },
                        ticks: {
                            callback: (valor) => "$" + Number(valor).toLocaleString("es-MX")
                        }
                    },
                    x: {
                        grid: { display: false }
                    }
                }
            }
        });
    }

    function cargarGastosDelMes() {
        fetch(ENDPOINT)
            .then((response) => {
                if (!response.ok) {
                    throw new Error("Error en la red al obtener los datos de gastos (" + response.status + ")");
                }
                return response.json();
            })
            .then(construirGrafica)
            .catch((error) => console.error("[dashboard] No se pudo cargar la gráfica de gastos:", error));
    }

    document.addEventListener("DOMContentLoaded", cargarGastosDelMes);
})();
