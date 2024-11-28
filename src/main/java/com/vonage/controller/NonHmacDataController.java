package com.vonage.controller;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.vonage.nonhmac.service.NonHmacDataService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/nonhmacdata")
@RequiredArgsConstructor
public class NonHmacDataController {

    @Data
    private static class Duration {
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss", timezone="Europe/Zagreb")
        private LocalDateTime start;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss", timezone="Europe/Zagreb")
        private LocalDateTime end;
    }

    private final NonHmacDataService nonHmacDataService;

    @PostMapping
    public ResponseEntity<String> startCollection(@RequestBody Duration duration) throws ExecutionException, InterruptedException {
        LocalDateTime nextStartTime = nonHmacDataService.startCollection(new MutablePair<>(duration.getStart(), duration.getEnd())).get();
        return ResponseEntity.ok("Collection started...\nNext start time : " + nextStartTime.toString());
    }

    @GetMapping("/combine")
    public ResponseEntity<String> combineResults() throws FileNotFoundException, InterruptedException {
        nonHmacDataService.combineResult();
        return ResponseEntity.ok("Result combined successfully");
    }
}
