package com.softility.omivertex.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the Thymeleaf shell page that hosts the React SPA. */
@Controller
public class HomeController {

    @GetMapping("/")
    public String index() {
        return "index";
    }
}
