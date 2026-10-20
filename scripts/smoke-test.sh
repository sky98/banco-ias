#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
PREFIX="SMOKE-$(date +%s)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

fail() {
  echo "FALLO: $1" >&2
  exit 1
}

expect_status() {
  local expected="$1"
  local actual="$2"
  local label="$3"
  if [[ "$actual" != "$expected" ]]; then
    fail "${label}: esperaba HTTP ${expected} y recibió ${actual}"
  fi
  echo "OK: ${label} (HTTP ${actual})"
}

post() {
  local reference="$1"
  local preApprovedId="$2"
  local customerId="$3"
  local amount="$4"
  local bodyFile="$5"
  local status
  status="$(curl -sS -o "${bodyFile}" -w '%{http_code}' \
    -X POST "${BASE_URL}/api/v1/preapproved-requests" \
    -H 'Content-Type: application/json' \
    -d "{\"requestReference\":\"${reference}\",\"preApprovedId\":\"${preApprovedId}\",\"customerId\":\"${customerId}\",\"amount\":${amount}}")"
  printf '%s' "${status}"
}

echo "Smoke test BancoIAS contra ${BASE_URL}"
echo "Prefijo de referencias: ${PREFIX}"
echo

STATUS="$(post "${PREFIX}-1" "PRA-1001" "USR-10" "600000" "${TMP_DIR}/r1.json")"
expect_status 201 "${STATUS}" "solicitud aprobada"
grep -q '"status":"APPROVED"' "${TMP_DIR}/r1.json" || fail "la solicitud no quedó aprobada"

STATUS="$(post "${PREFIX}-1" "PRA-1001" "USR-10" "600000" "${TMP_DIR}/r1-replay.json")"
expect_status 200 "${STATUS}" "replay idempotente"
grep -q '"replayed":true' "${TMP_DIR}/r1-replay.json" || fail "el replay no fue marcado como replayed"

STATUS="$(post "${PREFIX}-2" "PRA-1001" "USR-10" "500000" "${TMP_DIR}/r2.json")"
expect_status 201 "${STATUS}" "solicitud rechazada por cupo"
grep -q '"reason":"INSUFFICIENT_QUOTA"' "${TMP_DIR}/r2.json" || fail "motivo de rechazo inesperado"

STATUS="$(post "${PREFIX}-3" "PRA-1001" "USR-10" "0" "${TMP_DIR}/r3.json")"
expect_status 201 "${STATUS}" "solicitud rechazada por monto"
grep -q '"reason":"INVALID_AMOUNT"' "${TMP_DIR}/r3.json" || fail "motivo de monto inválido inesperado"

STATUS="$(post "${PREFIX}-1" "PRA-1001" "USR-10" "700000" "${TMP_DIR}/r4.json")"
expect_status 409 "${STATUS}" "conflicto de referencia con payload distinto"
grep -q '"code":"REFERENCE_CONFLICT"' "${TMP_DIR}/r4.json" || fail "no se reportó conflicto de referencia"

echo
echo "Solicitudes concurrentes sobre PRA-2001..."
for index in 0 1 2 3 4 5; do
  post "${PREFIX}-C${index}" "PRA-2001" "USR-20" "400000" "${TMP_DIR}/c${index}.json" \
    > "${TMP_DIR}/c${index}.status" &
done
wait

APPROVED=0
REJECTED=0
for index in 0 1 2 3 4 5; do
  expect_status 201 "$(cat "${TMP_DIR}/c${index}.status")" "concurrente ${index}"
  if grep -q '"status":"APPROVED"' "${TMP_DIR}/c${index}.json"; then
    APPROVED=$((APPROVED + 1))
  elif grep -q '"reason":"INSUFFICIENT_QUOTA"' "${TMP_DIR}/c${index}.json"; then
    REJECTED=$((REJECTED + 1))
  else
    fail "respuesta concurrente inesperada"
  fi
done

[[ "${APPROVED}" -eq 5 ]] || fail "se esperaban 5 aprobadas y se obtuvieron ${APPROVED}"
[[ "${REJECTED}" -eq 1 ]] || fail "se esperaba 1 rechazada y se obtuvieron ${REJECTED}"
echo "OK: concurrencia (5 aprobadas, 1 rechazada)"

STATUS="$(curl -sS -o "${TMP_DIR}/balance.json" -w '%{http_code}' "${BASE_URL}/api/v1/preapproved/PRA-2001")"
expect_status 200 "${STATUS}" "consulta de cupo"
grep -q '"availableAmount":0.00' "${TMP_DIR}/balance.json" || fail "el cupo final de PRA-2001 no quedó en cero"

echo
echo "Smoke test completado correctamente."
