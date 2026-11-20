#!/bin/sh
# Agar /certs da sertifikat bo'lmasa — 365 kunlik self-signed sertifikat yaratadi.
set -eu
if [ -s /certs/server.crt ] && [ -s /certs/server.key ]; then
  echo "TLS certificate already present, skipping"
  exit 0
fi
CN="${SERVER_NAME:-localhost}"
openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
  -keyout /certs/server.key -out /certs/server.crt \
  -subj "/CN=${CN}" -addext "subjectAltName=DNS:${CN},DNS:localhost,IP:127.0.0.1"
chmod 644 /certs/server.crt
chmod 644 /certs/server.key   # nginx master root sifatida o'qiydi; volume faqat :ro ulanadi
echo "Self-signed certificate generated for ${CN}"
