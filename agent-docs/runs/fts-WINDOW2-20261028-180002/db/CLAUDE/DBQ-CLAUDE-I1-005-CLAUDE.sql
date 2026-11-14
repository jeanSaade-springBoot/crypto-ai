SELECT id, symbol, entry_price, exit_price, stop_loss, take_profit,
       entry_price * 1.0005 AS hard_floor,
       exit_price < entry_price AS exit_below_entry,
       exit_price <= stop_loss AS exit_at_or_below_sl,
       (entry_price - stop_loss) / entry_price * 100 AS sl_dist_pct,
       (take_profit - entry_price) / entry_price * 100 AS tp_dist_pct
FROM paper_position
WHERE id IN (182, 172, 162, 158, 143, 137);