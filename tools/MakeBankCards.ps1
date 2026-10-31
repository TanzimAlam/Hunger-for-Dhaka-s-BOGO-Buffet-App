# Builds app/src/main/assets/bank_cards.json from data/card-bins/banks/*.json.
#
# The app only needs bank + product names for its two pickers, so BINs and the
# "bestFor" hint are dropped. Bank and card order are stable (alphabetical), which
# keeps the asset diff-free when the BIN export is refreshed without real changes.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools/MakeBankCards.ps1

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$banksDir = Join-Path $root "data/card-bins/banks"
$target = Join-Path $root "app/src/main/assets/bank_cards.json"

if (-not (Test-Path $banksDir)) {
    throw "Missing $banksDir - run this from the repository root."
}

$banks = Get-ChildItem -Path $banksDir -Filter "*.json" | ForEach-Object {
    $raw = Get-Content -Path $_.FullName -Raw -Encoding UTF8
    $bank = $raw | ConvertFrom-Json
    if (-not $bank.bank) { throw "No bank name in $($_.Name)" }

    $cards = @($bank.cards |
        ForEach-Object {
            [pscustomobject]@{
                name  = [string]$_.name
                brand = [string]$_.brand
                type  = [string]$_.type
                tier  = [string]$_.tier
            }
        } |
        Sort-Object -Property name)

    [pscustomobject]@{
        bank  = [string]$bank.bank
        cards = $cards
    }
} | Sort-Object -Property bank

$totalCards = ($banks | ForEach-Object { $_.cards.Count } | Measure-Object -Sum).Sum
$json = $banks | ConvertTo-Json -Depth 6

# Write without a BOM: Android's JSONArray parser rejects one at the start.
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($target, $json, $utf8)

Write-Host "Wrote $target"
Write-Host "Banks: $($banks.Count)   Cards: $totalCards"
