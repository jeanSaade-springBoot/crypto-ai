package com.crypto.wallet.controller;

import com.crypto.wallet.dto.*;
import com.crypto.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {
    private final WalletService walletService;
    @GetMapping public Map<String,Object> overview(){ return walletService.overview(); }
    @PostMapping("/assets") public ResponseEntity<Void> setAsset(@RequestBody WalletAssetRequest request){ walletService.setAsset(request); return ResponseEntity.noContent().build(); }
    @PostMapping("/cash-flows") public ResponseEntity<Void> cashFlow(@RequestBody WalletCashFlowRequest request){ walletService.addCashFlow(request); return ResponseEntity.noContent().build(); }
    @PutMapping("/settings") public ResponseEntity<Void> settings(@RequestBody WalletSettingsRequest request){ walletService.updateSettings(request); return ResponseEntity.noContent().build(); }
}
