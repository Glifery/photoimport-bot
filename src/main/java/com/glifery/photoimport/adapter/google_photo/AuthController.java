package com.glifery.photoimport.adapter.google_photo;

import com.glifery.photoimport.application.port.MediaStorageInterface;
import com.glifery.photoimport.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

@RestController
@RequiredArgsConstructor
public class AuthController {
    private final MediaStorageInterface mediaStorage;

    @GetMapping("/test")
    public String test() {
        return "Hello world";
    }

    @GetMapping("/auth")
    public ModelAndView auth(@RequestParam("code") String code, @RequestParam("state") String state) {
        mediaStorage.authByCode(code, new User(state));

        return new ModelAndView("redirect:" + "https://t.me/photoimport_bot?start=auth_completed");
    }
}
