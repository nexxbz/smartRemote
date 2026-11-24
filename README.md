# Smart Remote (WiFi)

Dos apps Android:
- **phone/**: el control remoto (interfaz oscura estilo captura).
- **tv/**: receptor para la Smart TV Android/Google TV (también sirve en otro celular o tablet).

## Compilar (GitHub)
1. Sube esta carpeta a un repositorio nuevo en GitHub.
2. Pestaña **Actions** → "Build APKs" → espera a que termine.
3. Descarga el artefacto `apks`: `phone-debug.apk` y `tv-debug.apk`.

## Usar
1. Instala `tv-debug.apk` en la TV (USB o `adb install tv-debug.apk`).
2. Abre "Smart Remote TV" → botón 1 → activa el servicio de accesibilidad. Anota el PIN.
   (Opcional: botón 2 para permitir brillo).
3. Instala `phone-debug.apk` en el celular (misma red WiFi), elige la TV o escribe su IP y el PIN.
