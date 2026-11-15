# Servicio PHP intermediario (Broker) sobre Apache.
# No usa Composer ni extensiones adicionales: la imagen oficial ya trae cURL.
FROM php:8.3-apache

# Apache sirve solo la carpeta public/; config/ y src/ quedan fuera del alcance web.
ENV APACHE_DOCUMENT_ROOT=/var/www/html/public

RUN a2enmod rewrite \
    # Configuración de producción: los avisos de PHP van al log y no ensucian el JSON.
    && mv "$PHP_INI_DIR/php.ini-production" "$PHP_INI_DIR/php.ini" \
    # Evita la advertencia de Apache por no tener nombre de servidor.
    && echo "ServerName localhost" > /etc/apache2/conf-available/nombre-servidor.conf \
    && a2enconf nombre-servidor \
    # Cambia la raíz de documentos a public/.
    && sed -ri -e 's!/var/www/html!${APACHE_DOCUMENT_ROOT}!g' /etc/apache2/sites-available/*.conf \
    && sed -ri -e 's!/var/www/!${APACHE_DOCUMENT_ROOT}!g' /etc/apache2/apache2.conf /etc/apache2/conf-available/*.conf \
    # Permite que un código con "/" codificado (%2F) llegue al front controller en vez de un 404 de Apache.
    && sed -ri -e 's!</VirtualHost>!\tAllowEncodedSlashes NoDecode\n</VirtualHost>!' /etc/apache2/sites-available/000-default.conf

COPY config/ /var/www/html/config/
COPY src/ /var/www/html/src/
COPY public/ /var/www/html/public/

EXPOSE 80
