package com.bedolla.bancobienestar.config;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.bedolla.bancobienestar.entity.CuentaEntity;
import com.bedolla.bancobienestar.entity.Rol;
import com.bedolla.bancobienestar.entity.SolicitudCreditoEntity;
import com.bedolla.bancobienestar.entity.UsuarioEntity;
import com.bedolla.bancobienestar.repository.CuentaRepository;
import com.bedolla.bancobienestar.repository.SolicitudCreditoRepository;
import com.bedolla.bancobienestar.repository.UsuarioRepository;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initUsuarios(UsuarioRepository usuarioRepository,
                                           CuentaRepository cuentaRepository,
                                           SolicitudCreditoRepository solicitudCreditoRepository,
                                           PasswordEncoder passwordEncoder) {
        return args -> {
            if (usuarioRepository.count() == 0) {

                // ==== Ejecutivo (ADMIN) ====
                UsuarioEntity moises = new UsuarioEntity();
                moises.setNombre("Moises Bedolla Magallon");
                moises.setUsername("moises");
                moises.setPassword(passwordEncoder.encode("moises123"));
                moises.setRol(Rol.ADMIN);
                moises.setProfesion("Lic. en Administración de Empresas");
                moises.setFechaRegistro(LocalDateTime.now().minusDays(30));
                usuarioRepository.save(moises);

                // ==== Clientes ====
                UsuarioEntity brandon = new UsuarioEntity();
                brandon.setNombre("Brandon Bedolla Magallon");
                brandon.setUsername("brandon");
                brandon.setPassword(passwordEncoder.encode("brandon123"));
                brandon.setRol(Rol.CLIENTE);
                brandon.setProfesion("Ingeniero en Sistemas Computacionales");
                brandon.setFechaRegistro(LocalDateTime.now().minusDays(20));
                usuarioRepository.save(brandon);

                UsuarioEntity greyci = new UsuarioEntity();
                greyci.setNombre("Greyci Lucena Garcia");
                greyci.setUsername("greyci");
                greyci.setPassword(passwordEncoder.encode("greyci123"));
                greyci.setRol(Rol.CLIENTE);
                greyci.setProfesion("Ambientalista");
                greyci.setFechaRegistro(LocalDateTime.now().minusDays(15));
                usuarioRepository.save(greyci);

                UsuarioEntity antony = new UsuarioEntity();
                antony.setNombre("Antony Pacheco Llanes");
                antony.setUsername("antony");
                antony.setPassword(passwordEncoder.encode("antony123"));
                antony.setRol(Rol.CLIENTE);
                antony.setProfesion("Ingeniero en Sistemas Computacionales");
                antony.setFechaRegistro(LocalDateTime.now().minusDays(10));
                usuarioRepository.save(antony);

                // ==== Cuenta propia del ejecutivo (igual que un cliente) ====
                CuentaEntity cuentaMoises = new CuentaEntity();
                cuentaMoises.setClabe("012180001112223334");
                cuentaMoises.setNoTarjeta("4152314411122233");
                cuentaMoises.setFechaExpiracion(LocalDate.of(2029, 11, 30));
                cuentaMoises.setCsv(321);
                cuentaMoises.setSaldo(120000.00);
                cuentaMoises.setEstado("ACTIVA");
                cuentaMoises.setUsuario(moises);
                cuentaRepository.save(cuentaMoises);

                // ==== Cuentas de debito de los clientes ====
                CuentaEntity cuentaBrandon = new CuentaEntity();
                cuentaBrandon.setClabe("012180001234567895");
                cuentaBrandon.setNoTarjeta("4152314412345678");
                cuentaBrandon.setFechaExpiracion(LocalDate.of(2029, 12, 31));
                cuentaBrandon.setCsv(123);
                cuentaBrandon.setSaldo(45863.00);
                cuentaBrandon.setEstado("ACTIVA");
                cuentaBrandon.setUsuario(brandon);
                cuentaRepository.save(cuentaBrandon);

                CuentaEntity cuentaGreyci = new CuentaEntity();
                cuentaGreyci.setClabe("012180009876543214");
                cuentaGreyci.setNoTarjeta("4152314498765432");
                cuentaGreyci.setFechaExpiracion(LocalDate.of(2029, 10, 31));
                cuentaGreyci.setCsv(456);
                cuentaGreyci.setSaldo(18250.00);
                cuentaGreyci.setEstado("ACTIVA");
                cuentaGreyci.setUsuario(greyci);
                cuentaRepository.save(cuentaGreyci);

                CuentaEntity cuentaAntony = new CuentaEntity();
                cuentaAntony.setClabe("012180005647382910");
                cuentaAntony.setNoTarjeta("4152314456473829");
                cuentaAntony.setFechaExpiracion(LocalDate.of(2029, 8, 31));
                cuentaAntony.setCsv(789);
                cuentaAntony.setSaldo(9700.00);
                cuentaAntony.setEstado("ACTIVA");
                cuentaAntony.setUsuario(antony);
                cuentaRepository.save(cuentaAntony);

                // ==== Solicitudes de credito de ejemplo ====
                SolicitudCreditoEntity solBrandon = new SolicitudCreditoEntity();
                solBrandon.setUsuario(brandon);
                solBrandon.setMontoSolicitado(15000.00);
                solBrandon.setEstado("PENDIENTE");
                solBrandon.setFecha(LocalDateTime.now().minusDays(2));
                solicitudCreditoRepository.save(solBrandon);

                SolicitudCreditoEntity solGreyci = new SolicitudCreditoEntity();
                solGreyci.setUsuario(greyci);
                solGreyci.setMontoSolicitado(8000.00);
                solGreyci.setEstado("PENDIENTE");
                solGreyci.setFecha(LocalDateTime.now().minusDays(1));
                solicitudCreditoRepository.save(solGreyci);

                SolicitudCreditoEntity solAntony = new SolicitudCreditoEntity();
                solAntony.setUsuario(antony);
                solAntony.setMontoSolicitado(25000.00);
                solAntony.setEstado("APROBADA");
                solAntony.setFecha(LocalDateTime.now().minusDays(5));
                solicitudCreditoRepository.save(solAntony);

                System.out.println("Usuarios de prueba creados:");
                System.out.println(" -> EJECUTIVO (ADMIN) username: moises  / password: moises123");
                System.out.println(" -> CLIENTE           username: brandon / password: brandon123");
                System.out.println(" -> CLIENTE           username: greyci  / password: greyci123");
                System.out.println(" -> CLIENTE           username: antony  / password: antony123");
            }
        };
    }
}
