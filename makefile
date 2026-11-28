#!/bin/bash
# JavaYog: se lanza desde la raiz del repo, en Git Bash o WSL. Requiere JDK 25 y Maven en el PATH
# (en el ZBook: C:\programs\jdk\jdk-25 y C:\programs\maven).
empty :=
.RECIPEPREFIX := $(empty) $(empty)
CURRENT_BRANCH := $(shell git rev-parse --abbrev-ref HEAD)

.PHONY: help build run test test-mysql mysql-down clean gitpush

# feature 13: el tunel HTTP de SQLyog (el PHP de prj-yog) para make test-mysql; si no esta, sus tests se saltan
YOG_TUNNEL_DIR ?= $(if $(wildcard ../prj-webyog-tunnel/backend_web),../prj-webyog-tunnel/backend_web,../prj-yog/backend_web)
YOG_TUNNEL_ABS = $(shell cd $(YOG_TUNNEL_DIR) 2>/dev/null && (pwd -W 2>/dev/null || pwd))
# la carpeta que el tunel ve como /data: su lista de BDs SQLite y el fichero de la clave it/app.sqlite (build/ esta en
# .gitignore y mvn clean no la borra con el contenedor en marcha)
SQLITE_TUNNEL_ABS = $(shell mkdir -p build/sqlite-tunnel && cd build/sqlite-tunnel && (pwd -W 2>/dev/null || pwd))

help: ## Show this help message · make help
    @echo "usage: make [target]"
    @echo
    @echo "targets:"
    @awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z_-]+:.*## / {printf "  %-12s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

build: ## compila y copia las dependencias a target/lib (sin tests) · make build
    mvn -q -B package -DskipTests

run: build ## compila y abre la app · make run
    bin/desktop

test: ## tests contra su propia BD (target/javayog-test.sqlite) · make test  |  make test f="ConnectionsServicesTest"
    mvn -B test $(if $(f),-Dtest="$(f)",)

test-mysql: ## tests + integracion contra MySQL 8.4 (127.0.0.1:33306), PostgreSQL 16 (127.0.0.1:35432), Redis 7 (127.0.0.1:36379), MongoDB 7 (127.0.0.1:37017), SQL Server 2022 (127.0.0.1:31433), un sshd (127.0.0.1:32222) y el tunel PHP (127.0.0.1:38080) desechables en Docker · make test-mysql  |  YOG_TUNNEL_DIR=<ruta a backend_web>
    @docker network create javayog-test-net >/dev/null 2>&1 || true
    @docker start javayog-test-mysql >/dev/null 2>&1 || docker run -d --name javayog-test-mysql --network javayog-test-net -e MYSQL_ROOT_PASSWORD=javayog-test -p 127.0.0.1:33306:3306 mysql:8.4
    @docker network connect javayog-test-net javayog-test-mysql >/dev/null 2>&1 || true
    @docker start javayog-test-pg >/dev/null 2>&1 || docker run -d --name javayog-test-pg --network javayog-test-net -e POSTGRES_PASSWORD=javayog-test -p 127.0.0.1:35432:5432 postgres:16-alpine
    @until docker exec javayog-test-pg pg_isready -U postgres 2>/dev/null | grep -q accepting; do sleep 2; done
    @docker start javayog-test-redis >/dev/null 2>&1 || docker run -d --name javayog-test-redis --network javayog-test-net -p 127.0.0.1:36379:6379 redis:7-alpine redis-server --requirepass javayog-redis
    @until docker exec javayog-test-redis redis-cli -a javayog-redis --no-auth-warning ping 2>/dev/null | grep -q PONG; do sleep 1; done
    @docker start javayog-test-mongo >/dev/null 2>&1 || docker run -d --name javayog-test-mongo --network javayog-test-net -p 127.0.0.1:37017:27017 -e MONGO_INITDB_ROOT_USERNAME=root -e MONGO_INITDB_ROOT_PASSWORD=javayog-mongo mongo:7
    @until docker exec javayog-test-mongo mongosh -u root -p javayog-mongo --quiet --eval "db.runCommand({ping: 1}).ok" 2>/dev/null | grep -q 1; do sleep 2; done
    @docker start javayog-test-ssh >/dev/null 2>&1 || docker run -d --name javayog-test-ssh --network javayog-test-net -p 127.0.0.1:32222:22 alpine:3.20 sh -c "apk add --no-cache openssh >/dev/null && ssh-keygen -A && echo root:javayog-ssh | chpasswd && sed -i -E 's/^#?PermitRootLogin.*/PermitRootLogin yes/; s/^#?AllowTcpForwarding.*/AllowTcpForwarding yes/; s/^#?PasswordAuthentication.*/PasswordAuthentication yes/' /etc/ssh/sshd_config && exec /usr/sbin/sshd -D -e"
    @until docker exec javayog-test-mysql mysqladmin -uroot -pjavayog-test ping 2>/dev/null | grep -q alive; do sleep 2; done
    @until docker logs javayog-test-ssh 2>&1 | grep -q "Server listening"; do sleep 2; done
    @docker start javayog-test-mssql >/dev/null 2>&1 || docker run -d --name javayog-test-mssql --network javayog-test-net -e ACCEPT_EULA=Y -e MSSQL_SA_PASSWORD=Javayog-test1 -e MSSQL_PID=Developer -p 127.0.0.1:31433:1433 mcr.microsoft.com/mssql/server:2022-latest
    @until docker logs javayog-test-mssql 2>&1 | grep -q "ready for client connections"; do sleep 2; done
    @if [ -n "$(YOG_TUNNEL_ABS)" ]; then \
        docker build -q -t javayog-test-php-tunnel-all -f tests/docker/tunnel-php.Dockerfile tests/docker >/dev/null; \
        printf '{"it/app.sqlite": "/data/app.sqlite"}\n' > build/sqlite-tunnel/sqlite-databases.json; \
        docker start javayog-test-tunnel-all >/dev/null 2>&1 || MSYS_NO_PATHCONV=1 docker run -d --name javayog-test-tunnel-all --network javayog-test-net -p 127.0.0.1:38080:8080 -e PHP_CLI_SERVER_WORKERS=4 -e YOG_URL_TOKEN=tok-test-123 -e YOG_ALLOWED_HOSTS=javayog-test-mysql:3306,javayog-test-pg:5432,javayog-test-mssql:1433,javayog-test-redis:6379,javayog-test-mongo:27017 -e YOG_SQLITE_PASSWORD=lite-test -e YOG_SQLITE_DATABASES_FILE=/data/sqlite-databases.json -v "$(YOG_TUNNEL_ABS):/app:ro" -v "$(SQLITE_TUNNEL_ABS):/data" -w /app javayog-test-php-tunnel-all php -S 0.0.0.0:8080 index.php >/dev/null; \
        until curl -s "http://127.0.0.1:38080/tok-test-123?app" | grep -q TunnelVersion; do sleep 1; done; \
    else echo "sin tunel PHP en $(YOG_TUNNEL_DIR): los tests del tunel HTTP se saltan"; fi
    JAVAYOG_TEST_MYSQL=127.0.0.1:33306:root:javayog-test JAVAYOG_TEST_PGSQL=127.0.0.1:35432:postgres:javayog-test JAVAYOG_TEST_REDIS=127.0.0.1:36379:javayog-redis JAVAYOG_TEST_REDIS_FROM_SSH=javayog-test-redis:6379 JAVAYOG_TEST_MONGO=127.0.0.1:37017:root:javayog-mongo JAVAYOG_TEST_MONGO_FROM_SSH=javayog-test-mongo:27017 JAVAYOG_TEST_MSSQL=127.0.0.1:31433:sa:Javayog-test1 JAVAYOG_TEST_SSH=127.0.0.1:32222:root:javayog-ssh:javayog-test-mysql:3306 $(if $(YOG_TUNNEL_ABS),JAVAYOG_TEST_HTTP="http://127.0.0.1:38080/tok-test-123|javayog-test-mysql|3306" JAVAYOG_TEST_PGSQL_FROM_TUNNEL=javayog-test-pg:5432 JAVAYOG_TEST_MSSQL_FROM_TUNNEL=javayog-test-mssql:1433 JAVAYOG_TEST_REDIS_FROM_TUNNEL=javayog-test-redis:6379 JAVAYOG_TEST_MONGO_FROM_TUNNEL=javayog-test-mongo:27017 MSYS2_ENV_CONV_EXCL=JAVAYOG_TEST_SQLITE_TUNNEL JAVAYOG_TEST_SQLITE_TUNNEL="it/app.sqlite|$(SQLITE_TUNNEL_ABS)|lite-test",) mvn -B test

mysql-down: ## borra el MySQL, el PostgreSQL, el Redis, el MongoDB, el SQL Server, el sshd, el tunel PHP y la red desechables de test-mysql · make mysql-down
    docker rm -f javayog-test-mysql javayog-test-pg javayog-test-redis javayog-test-mongo javayog-test-mssql javayog-test-ssh javayog-test-tunnel javayog-test-tunnel-all
    docker network rm javayog-test-net

clean: ## borra target/ · make clean
    mvn -q clean

gitpush: ## git push de la rama actual · make gitpush m="mensaje"
    clear;
    git pull origin $(CURRENT_BRANCH);
    git add .; git commit -m "jy: $(m)"; git push origin $(CURRENT_BRANCH);
