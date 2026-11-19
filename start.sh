#!/bin/bash
# Arranca la pantalla virtual, el servidor VNC, la aplicación Swing y noVNC.
PUERTO="${PORT:-10000}"

Xvfb "$DISPLAY" -screen 0 "$RESOLUCION" -nolisten tcp &
sleep 2
openbox &

# Si se define VNC_PASSWORD en Render, el visor pedirá esa contraseña
if [ -n "$VNC_PASSWORD" ]; then
    x11vnc -storepasswd "$VNC_PASSWORD" /tmp/vncpass >/dev/null 2>&1
    AUTH="-rfbauth /tmp/vncpass"
else
    AUTH="-nopw"
fi
x11vnc -display "$DISPLAY" -forever -shared -localhost -rfbport 5900 $AUTH -quiet &

# La aplicación se vuelve a abrir si alguien la cierra
(
    while true; do
        java SistemaInventarioMercadoEconomia
        sleep 1
    done
) &

echo "Sistema disponible en el puerto $PUERTO"
exec websockify --web /app/web "0.0.0.0:$PUERTO" localhost:5900
