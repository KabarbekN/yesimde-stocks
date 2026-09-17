package kz.nurgissa.kasestockexchangeparser.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class HomeController {

    private final Resource indexHtml = new ClassPathResource("static/index.html");

    @GetMapping(value = "/", produces = "text/html;charset=UTF-8")
    public Mono<Resource> home() {
        return Mono.just(indexHtml);
    }

    @GetMapping(value = "/index.html", produces = "text/html;charset=UTF-8")
    public Mono<Resource> index() {
        return Mono.just(indexHtml);
    }
}
