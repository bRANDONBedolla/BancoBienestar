package com.bedolla.bancobienestar.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class loginController {

    @GetMapping("/login")
    public String mostrarLogin() {
        return "login";
    }
}
