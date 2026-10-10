# Smartphone (mod de teléfono para Minecraft 1.21.1 · Fabric)

Un teléfono estilo FiveM que aparece abajo a la derecha.

## Qué incluye
- **Mensajes**: burbujas de chat, historial completo guardado en el servidor, estados Enviado / Entregado / Leído. Si el otro está desconectado, el mensaje le espera y lo ve al entrar.
- **Número único** por jugador (formato `555-1234`). Para agregar a alguien necesitas su número.
- **Contactos (agenda)**: guardas nombre + número.
- **Llamadas** con voz usando **Plasmo Voice**, con timbre, contestar, rechazar, colgar, y un historial de llamadas recientes.
- **Cámara**: saca fotos (sin HUD) y se guardan en la **Galería** del teléfono (carpeta `phone_gallery`).
- **Ajustes**: volumen del tono, No molestar, fondo de pantalla.
- Notificaciones y llamadas entrantes aparecen abajo a la derecha aunque el teléfono esté cerrado.

## Teclas (se pueden cambiar en Controles → Teléfono)
| Tecla | Acción |
|-------|--------|
| `M` | Abrir / cerrar el teléfono (y salir del modo cámara) |
| `Y` | Contestar llamada |
| `N` | Rechazar / colgar |
| `Enter` | Tomar foto (solo en modo cámara) |

Comando: `/phone number` te dice tu número.

## Requisitos
- Minecraft 1.21.1 con Fabric Loader
- Fabric API
- Plasmo Voice (versión Fabric 1.21.1), en el servidor **y** en los clientes
- El mod tiene que estar instalado en el servidor y en los clientes

## Cómo hablar en una llamada
Plasmo Voice añade una activación llamada **Teléfono**. Entra en los ajustes de Plasmo Voice
(tecla `V` por defecto) → *Activaciones* → *Teléfono* y elige cómo activarla (pulsar para hablar con una tecla,
o por voz). Lo que hables con esa activación solo lo oye la persona con quien estás en llamada.

## Cómo compilarlo (sin instalar nada) con GitHub
1. Crea una cuenta en https://github.com y un repositorio nuevo (vacío).
2. Sube **todo el contenido de esta carpeta** (botón *Add file → Upload files*; asegúrate de subir también la carpeta oculta `.github`).
3. Entra en la pestaña **Actions**. Se ejecuta solo "Compilar mod" (si no, pulsa *Run workflow*).
4. Cuando termine (unos 3-5 minutos), abre la ejecución y descarga **smartphone-mod** en *Artifacts*.
   Dentro está `smartphone-1.0.0.jar`. (No uses el `-sources.jar`.)
5. Mete ese `.jar` en la carpeta `mods` junto con Fabric API y Plasmo Voice.

Si algo falla al compilar, copia el mensaje de error del paso "Compilar" y pásamelo para corregirlo.

## Cómo compilarlo en tu PC
Con Java 21 instalado: `./gradlew build` (Windows: `gradlew.bat build`). El jar queda en `build/libs/`.
