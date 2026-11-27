package io.github.jcorta.balancegateway.coremock.chaos;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChaosController {

    private final ChaosService chaos;

    public ChaosController(ChaosService chaos) {
        this.chaos = chaos;
    }

    @GetMapping("/admin/chaos")
    public ChaosSettings current() {
        return chaos.current();
    }

    @PostMapping("/admin/chaos")
    public ChaosSettings update(@Valid @RequestBody ChaosSettings settings) {
        return chaos.update(settings);
    }

    @DeleteMapping("/admin/chaos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() {
        chaos.reset();
    }
}
