#!/bin/sh

BASE_URL="http://localhost:8080"

if [ -z "$1" ]; then
  echo "Usage: $0 <document_uuid>"
  exit 1
fi

UUID=$1

echo "Deleting document with UUID: $UUID"
curl -X DELETE -v "$BASE_URL/api/v1/kb/documents/$UUID"
echo "Delete request sent for document with UUID: $UUID"
