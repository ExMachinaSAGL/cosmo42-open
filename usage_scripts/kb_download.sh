#!/bin/sh

BASE_URL="http://localhost:8080"

if [ -z "$1" ]; then
  echo "Usage: $0 <document_uuid> [output_filename]"
  exit 1
fi

UUID=$1
OUTPUT_FILENAME=${2:-"downloaded_document_$UUID"} # Default filename if not provided

echo "Downloading document with UUID: $UUID"
curl -X GET -o "$OUTPUT_FILENAME" "$BASE_URL/api/v1/kb/documents/$UUID/download"
echo "Document downloaded to $OUTPUT_FILENAME"
