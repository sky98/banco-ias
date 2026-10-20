SHELL := /bin/bash
COMPOSE := docker compose
GRADLE_IMAGE := gradle:9.8.0-jdk21
GRADLE_CACHE_VOLUME := bancoias-gradle-cache

.PHONY: help up down reload reset restart logs ps test test-back build build-back smoke db

help:
	@echo "BancoIAS - comandos disponibles"
	@echo ""
	@echo "  make up          Construye y levanta db y back"
	@echo "  make down        Detiene contenedores (conserva el volumen de datos)"
	@echo "  make reload      down + up --build (conserva datos)"
	@echo "  make reset       down -v + up --build (borra datos y vuelve a sembrar)"
	@echo "  make restart     Reinicia servicios sin reconstruir"
	@echo "  make logs        Sigue los logs"
	@echo "  make ps          Estado de los contenedores"
	@echo "  make test        Ejecuta pruebas del backend"
	@echo "  make test-back   Ejecuta pruebas del backend"
	@echo "  make build       Compila el backend"
	@echo "  make build-back  Compila el backend"
	@echo "  make smoke       Ejecuta el smoke test contra la API"
	@echo "  make db          Levanta solo PostgreSQL"

up:
	$(COMPOSE) up --build -d

down:
	$(COMPOSE) down

reload:
	$(COMPOSE) down
	$(COMPOSE) up --build -d

reset:
	$(COMPOSE) down -v
	$(COMPOSE) up --build -d

restart:
	$(COMPOSE) restart

logs:
	$(COMPOSE) logs -f

ps:
	$(COMPOSE) ps

test: test-back

test-back:
	docker run --rm --network host \
		-e GRADLE_USER_HOME=/home/gradle/.gradle \
		-e DOCKER_HOST=unix:///var/run/docker.sock \
		-e TESTCONTAINERS_RYUK_DISABLED=true \
		-v "$(CURDIR)/back:/home/gradle/project" \
		-v "$(GRADLE_CACHE_VOLUME):/home/gradle/.gradle" \
		-v /var/run/docker.sock:/var/run/docker.sock \
		-w /home/gradle/project \
		$(GRADLE_IMAGE) ./gradlew test --no-daemon

build: build-back

build-back:
	docker run --rm \
		-e GRADLE_USER_HOME=/home/gradle/.gradle \
		-v "$(CURDIR)/back:/home/gradle/project" \
		-v "$(GRADLE_CACHE_VOLUME):/home/gradle/.gradle" \
		-w /home/gradle/project \
		$(GRADLE_IMAGE) ./gradlew :app-service:bootJar --no-daemon

smoke:
	./scripts/smoke-test.sh

db:
	$(COMPOSE) up -d db
