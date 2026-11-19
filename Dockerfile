# Sistema de Gestión y Control de Inventario - Mercado La Economía
# Ejecuta la aplicación Java Swing en una pantalla virtual (Xvfb) y la
# muestra en el navegador con noVNC, para tener una URL pública en Render.
FROM eclipse-temurin:21-jdk

ENV DEBIAN_FRONTEND=noninteractive \
    DISPLAY=:1 \
    RESOLUCION=1280x800x24 \
    INVENTARIO_DIR=/app/datos \
    JAVA_TOOL_OPTIONS="-Xmx256m -Dfile.encoding=UTF-8"

RUN apt-get update && apt-get install -y --no-install-recommends \
        xvfb x11vnc novnc websockify openbox \
        libxext6 libxrender1 libxtst6 libxi6 fontconfig \
        fonts-dejavu-core fonts-symbola \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY *.java ./
RUN javac -encoding UTF-8 *.java

# Página de inicio: abre directamente el sistema en el navegador
RUN cp -r /usr/share/novnc /app/web && printf '%s\n' \
    '<!DOCTYPE html><html lang="es"><head><meta charset="utf-8">' \
    '<title>Mercado La Economía - Control de Inventario</title>' \
    '<meta http-equiv="refresh" content="0; url=vnc.html?autoconnect=true&resize=scale&reconnect=true&reconnect_delay=2000&show_dot=true">' \
    '</head><body style="font-family:sans-serif;text-align:center;padding-top:40px">' \
    '<p>Cargando el Sistema de Gestión y Control de Inventario...</p>' \
    '<p><a href="vnc.html?autoconnect=true&resize=scale&reconnect=true">Entrar al sistema</a></p>' \
    '</body></html>' > /app/web/index.html

COPY start.sh /app/start.sh
RUN sed -i 's/\r$//' /app/start.sh && chmod +x /app/start.sh && mkdir -p /app/datos

CMD ["/app/start.sh"]
