package com.mars.apacheavro.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/test")
public class TestController {
    @GetMapping(value = "/public")
    public ResponseEntity<Object> home() {
        return new ResponseEntity<>("PAGE_PUBLIC", HttpStatus.OK);
    }

}
