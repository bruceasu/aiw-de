package me.asu.aidev.webconsole.protocol.http;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(basePackages = "me.asu.aidev.webconsole")
public class WebConsoleAccessDeniedAdvice {
    @ExceptionHandler(SecurityException.class)
    public ModelAndView forbidden(SecurityException error) {
        ModelAndView view = new ModelAndView("ui/forbidden");
        view.setStatus(HttpStatus.FORBIDDEN);
        view.addObject("message", error.getMessage());
        view.addObject("page", "dashboard");
        return view;
    }
}
