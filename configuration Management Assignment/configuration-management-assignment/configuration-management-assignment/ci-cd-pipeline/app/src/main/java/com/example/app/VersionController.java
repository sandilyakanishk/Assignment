package com.example.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The whole point of this controller is to make it OBVIOUS which color is
 * currently serving traffic. During a blue-green deployment you hit / and
 * watch the `color` field change at the exact moment nginx flips the switch.
 */
@RestController
@RequestMapping("/")
public class VersionController {

    @Value("${app.color:unknown}")
    private String color;

    @Value("${app.version:unknown}")
    private String version;

    @GetMapping
    public Map<String, Object> root() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service",  "bluegreen-app");
        body.put("color",    color);
        body.put("version",  version);
        body.put("hostname", hostname());
        return body;
    }

    @GetMapping("/api/version")
    public Map<String, Object> version() {
        return root();
    }

    private String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
