#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
keystore_path="${1:-$project_root/release-keystore.jks}"
properties_path="${2:-$project_root/keystore.properties}"
key_alias="${ANDROID_KEY_ALIAS:-pixelcardemulator}"

if [[ -e "$keystore_path" || -e "$properties_path" ]]; then
    echo "Refusing to overwrite an existing signing file." >&2
    echo "keystore: $keystore_path" >&2
    echo "properties: $properties_path" >&2
    exit 1
fi

read -r -s -p "Keystore password: " store_password
printf '\n'
read -r -s -p "Confirm keystore password: " confirmed_store_password
printf '\n'
if [[ "$store_password" != "$confirmed_store_password" || -z "$store_password" ]]; then
    echo "Keystore passwords do not match or are empty." >&2
    exit 1
fi

read -r -s -p "Key password (press Enter to reuse keystore password): " key_password
printf '\n'
key_password="${key_password:-$store_password}"

umask 077
keytool -genkeypair \
    -alias "$key_alias" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -storetype JKS \
    -keystore "$keystore_path" \
    -storepass "$store_password" \
    -keypass "$key_password" \
    -dname "CN=PixelCardEmulator, OU=Android, O=PixelCardEmulator, L=Unknown, ST=Unknown, C=XX" \
    -noprompt

cat > "$properties_path" <<EOF
storeFile=$keystore_path
storePassword=$store_password
keyAlias=$key_alias
keyPassword=$key_password
EOF

chmod 600 "$keystore_path" "$properties_path"
echo "Created local signing files:"
echo "  $keystore_path"
echo "  $properties_path"
