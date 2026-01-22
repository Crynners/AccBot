# SoloMinator - Azure Deployment Guide

## Požadavky
- Azure App Service (Linux, .NET 8.0)
- `WEBSITE_RUN_FROM_PACKAGE=0` (vypnout Run-From-Zip režim)

## Důležité nastavení Azure

```bash
# Nastavit app settings (jednorázově)
az webapp config appsettings set --resource-group SoloMinator --name solominator-web --settings WEBSITE_RUN_FROM_PACKAGE=0
```

## Postup nasazení

### 1. Publish aplikace
```bash
cd C:\Users\vitne\source\repos\AccBot\SoloMinator
dotnet publish -c Release -o ./clean_publish
```

### 2. Odstranit nepotřebné složky (pokud existují)
```bash
rm -rf ./clean_publish/publish ./clean_publish/publish-linux
```

### 3. Vytvořit ZIP s Unix cestami (DŮLEŽITÉ!)
Windows Compress-Archive vytváří backslash cesty, které nefungují na Linuxu.
Použít Python skript:

```python
# create_zip.py
import zipfile
import os

source_dir = r'C:\Users\vitne\source\repos\AccBot\SoloMinator\clean_publish'
zip_path = r'C:\Users\vitne\source\repos\AccBot\SoloMinator\clean_deploy.zip'

with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
    for root, dirs, files in os.walk(source_dir):
        for file in files:
            file_path = os.path.join(root, file)
            arcname = os.path.relpath(file_path, source_dir).replace('\\', '/')
            zipf.write(file_path, arcname)

print('Zip created successfully')
```

Spustit:
```bash
python create_zip.py
```

### 4. Deploy na Azure
```bash
az webapp deploy --resource-group SoloMinator --name solominator-web --src-path "./clean_deploy.zip" --type zip --clean true
```

## Důležité poznámky

### Proč WEBSITE_RUN_FROM_PACKAGE=0?
- S `WEBSITE_RUN_FROM_PACKAGE=1` (Run-From-Zip) satelitní assemblies pro lokalizaci (`en/SoloMinator.resources.dll`) nejsou správně načítány
- S `WEBSITE_RUN_FROM_PACKAGE=0` se ZIP extrahuje do `/home/site/wwwroot/` včetně všech podadresářů

### Proč Python pro ZIP?
- PowerShell `Compress-Archive` vytváří cesty s backslash (`\`)
- Linux rsync tyto cesty nerozpozná a deploy selže s chybou "Invalid argument (22)"
- Python `zipfile` s `.replace('\\', '/')` vytvoří správné Unix cesty

## Struktura nasazeného wwwroot
```
/home/site/wwwroot/
├── en/
│   └── SoloMinator.resources.dll  # Anglická lokalizace
├── runtimes/                       # Native libraries
├── wwwroot/                        # Static files
│   ├── css/
│   ├── js/
│   ├── lib/
│   ├── solominator.svg            # Logo
│   └── ...
├── SoloMinator.dll
├── SoloMinator.exe
└── ...
```

## URL
- Produkce: https://solominator.cz
- Kudu: https://solominator-web.scm.azurewebsites.net
