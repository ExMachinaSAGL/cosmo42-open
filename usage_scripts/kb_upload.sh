#!/bin/sh

BASE_URL="http://localhost:8080"

if [ -z "$1" ]; then
  echo "Usage: $0 <file_path>"
  exit 1
fi

FILE_PATH=$1

echo "Uploading file: $FILE_PATH"
curl -X POST "$BASE_URL/api/v1/kb/documents" -F "file=@$FILE_PATH"
echo "Upload request sent for file: $FILE_PATH"
