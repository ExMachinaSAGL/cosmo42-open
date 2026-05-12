#!/bin/sh

BASE_URL="http://localhost:8080"

curl -X GET  -H "Accept: application/json" "$BASE_URL/api/v1/kb/documents"
