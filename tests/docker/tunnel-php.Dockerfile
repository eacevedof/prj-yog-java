# imagen del tunel PHP para make test-mysql: todos los motores (pdo_mysql, pdo_pgsql, pdo_sqlite, pdo_sqlsrv, mongodb; Redis no necesita extension)
FROM php:8.4-cli-bookworm
RUN apt-get update && apt-get install -y --no-install-recommends gnupg curl ca-certificates libpq-dev unixodbc-dev libssl-dev \
    && curl -fsSL https://packages.microsoft.com/keys/microsoft.asc | gpg --dearmor -o /usr/share/keyrings/microsoft-prod.gpg \
    && curl -fsSL https://packages.microsoft.com/config/debian/12/prod.list > /etc/apt/sources.list.d/mssql-release.list \
    && apt-get update && ACCEPT_EULA=Y apt-get install -y --no-install-recommends msodbcsql18 \
    && docker-php-ext-install pdo_mysql pdo_pgsql \
    && pecl install sqlsrv-5.12.0 pdo_sqlsrv-5.12.0 mongodb-1.20.1 \
    && docker-php-ext-enable pdo_sqlsrv mongodb \
    && rm -rf /var/lib/apt/lists/*
