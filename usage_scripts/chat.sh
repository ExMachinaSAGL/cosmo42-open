#!/bin/bash

# Base URL for the API
BASE_URL="http://localhost:8080"

# Initialize UUID as empty
UUID=""

echo "Starting interactive chat session."
echo "Type 'exit' or 'quit' to end the session."
echo "----------------------------------------------------"

while true; do
  printf "You: "
  read -r MESSAGE

  if [ "$MESSAGE" = "exit" ] || [ "$MESSAGE" = "quit" ]; then
    echo "----------------------------------------------------"
    echo "Ending chat session."
    break
  fi

  # Determine the UUID to send
  if [ -n "$UUID" ]; then
    CURRENT_UUID_IN_PAYLOAD="\"$UUID\""
  else
    CURRENT_UUID_IN_PAYLOAD="null"
  fi

  # Escape the message for JSON
  ESCAPED_MESSAGE=$(echo "$MESSAGE" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g')

  printf "Bot: "

  # Use process substitution to avoid subshell
  while IFS= read -r line; do
    case "$line" in
      data:*)
        json="${line#data:}"
        json="${json# }"

        [ -z "$json" ] && continue

        type=$(jq -r '.type // empty' <<< "$json" 2>/dev/null)
        data=$(jq -r '.data // empty' <<< "$json" 2>/dev/null)

        case "$type" in
          UUID)
            [ -n "$data" ] && UUID="$data"
            printf "[UUID: %s] " "$data"
            ;;
          STATUS)
            [ -n "$data" ] && printf "[%s] " "$data"
            ;;
          CHUNK)
            [ -n "$data" ] && printf '%s' "$data"
            ;;
          COMPLETED)
            printf '\n'
            ;;
          ERROR)
            printf '\nError: %s\n' "$data"
            ;;
        esac
        ;;
    esac
  done < <(curl -s -N -X POST \
    -H "Content-Type: application/json" \
    -H "Accept: text/event-stream" \
    -d "{\"uuid\":$CURRENT_UUID_IN_PAYLOAD,\"message\":\"$ESCAPED_MESSAGE\"}" \
    "$BASE_URL/api/v1/chat/stream" 2>/dev/null)

done