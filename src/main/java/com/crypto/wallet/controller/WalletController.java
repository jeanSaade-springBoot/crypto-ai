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
    @org.springframework.beans.factory.annotation.Autowired
    private com.crypto.wallet.service.WalletTransactionCoordination coordination;
    @GetMapping public Map<String,Object> overview(){ return walletService.overview(); }
    @GetMapping("/dashboard") public Map<String,Object> dashboardOverview(){ return walletService.dashboardOverview(); }
    @PostMapping("/assets") public ResponseEntity<Void> setAsset(@RequestBody WalletAssetRequest request){
        // FIX-125: first asset administration provisions outside the mutation transaction.
        if(request.symbol()!=null && !request.symbol().isBlank()) {
            String asset=request.symbol().trim().toUpperCase(java.util.Locale.ROOT);
            if(!"USDT".equals(asset))coordination.provision(asset.endsWith("USDT")?asset:asset+"USDT");
        }
        walletService.setAsset(request); return ResponseEntity.noContent().build(); }
    @PostMapping("/cash-flows") public ResponseEntity<Void> cashFlow(@RequestBody WalletCashFlowRequest request){ walletService.addCashFlow(request); return ResponseEntity.noContent().build(); }
    @PutMapping("/settings") public ResponseEntity<Void> settings(@RequestBody WalletSettingsRequest request){ walletService.updateSettings(request); return ResponseEntity.noContent().build(); }
}
