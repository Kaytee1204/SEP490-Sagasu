package com.example.base.controller.report;
import com.example.base.dto.ApiResponse;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.report.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
@RestController @RequestMapping("/api/reports") @RequiredArgsConstructor
public class ReportController {
    private final ReportService service;
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(@RequestPart("data") ReportService.Submit data,@RequestPart(value="images",required=false) List<MultipartFile> images,@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.create(data,images,user)));
    }
    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable long id,@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.mineDetail(id,user)));
    }
}
