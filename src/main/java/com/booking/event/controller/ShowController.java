package com.booking.event.controller;

import com.booking.event.dto.CreateShowRequest;
import com.booking.event.dto.ShowResponse;
import com.booking.event.entity.Show;
import com.booking.event.service.ShowService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shows")
public class ShowController {
    private final ShowService showService;

    public ShowController ( ShowService showService){
        this.showService = showService;
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Show createShow(@RequestBody CreateShowRequest request){
        return showService.createShow(request);
    }

    @GetMapping("/{showId}")
    public ShowResponse getShow(@PathVariable Long showId) {
        return showService.getShow(showId);
    }

}
