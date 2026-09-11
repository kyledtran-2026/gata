docker images --format "{{json .}}" |
    ConvertFrom-Json |
    Sort-Object { Get-Date $_.CreatedAt } -Descending |
    Format-Table Repository, Tag, ID, CreatedAt, Size -AutoSize