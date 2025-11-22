#!/usr/bin/env bash
set -euo pipefail

OUTPUT_DIR=${1:-certs}
REMOTE_DIR="${OUTPUT_DIR}/remote"
LOCAL_DIR="${OUTPUT_DIR}/local"
CA_DIR="${OUTPUT_DIR}/ca"

mkdir -p "${REMOTE_DIR}" "${LOCAL_DIR}" "${CA_DIR}"

echo "Generating root CA..."
openssl genrsa -out "${CA_DIR}/ca-key.pem" 4096
openssl req -x509 -new -nodes -key "${CA_DIR}/ca-key.pem" -sha256 -days 825 \
  -out "${CA_DIR}/ca-cert.pem" -subj "/CN=Forwarding-Root-CA"

echo "Generating remote server certificate..."
openssl genrsa -out "${REMOTE_DIR}/server-key.pem" 2048
openssl req -new -key "${REMOTE_DIR}/server-key.pem" \
  -out "${REMOTE_DIR}/server.csr" -subj "/CN=remote-forward-proxy"
openssl x509 -req -in "${REMOTE_DIR}/server.csr" -CA "${CA_DIR}/ca-cert.pem" \
  -CAkey "${CA_DIR}/ca-key.pem" -CAcreateserial -out "${REMOTE_DIR}/server-cert.pem" \
  -days 825 -sha256 -extfile <(printf "subjectAltName=DNS:remote-forward-proxy,IP:127.0.0.1")
rm "${REMOTE_DIR}/server.csr"

echo "Generating local client certificate..."
openssl genrsa -out "${LOCAL_DIR}/client-key.pem" 2048
openssl req -new -key "${LOCAL_DIR}/client-key.pem" \
  -out "${LOCAL_DIR}/client.csr" -subj "/CN=local-forward-client"
openssl x509 -req -in "${LOCAL_DIR}/client.csr" -CA "${CA_DIR}/ca-cert.pem" \
  -CAkey "${CA_DIR}/ca-key.pem" -CAcreateserial -out "${LOCAL_DIR}/client-cert.pem" \
  -days 825 -sha256
rm "${LOCAL_DIR}/client.csr"

echo ""
echo "Generated certificates:"
echo "  CA certificate:      ${CA_DIR}/ca-cert.pem"
echo "  Remote server cert:  ${REMOTE_DIR}/server-cert.pem"
echo "  Remote server key:   ${REMOTE_DIR}/server-key.pem"
echo "  Local client cert:   ${LOCAL_DIR}/client-cert.pem"
echo "  Local client key:    ${LOCAL_DIR}/client-key.pem"
echo ""
echo "Copy the CA certificate to both sides as trust store."
echo "Optionally distribute client certificates if mutual TLS is enabled."

