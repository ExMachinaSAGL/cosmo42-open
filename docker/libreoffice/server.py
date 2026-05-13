#!/usr/bin/env python3

import http.server
import subprocess
import tempfile
import os
import threading

# LibreOffice is not thread-safe
lo_lock = threading.Lock()

class ConvertHandler(http.server.BaseHTTPRequestHandler):

    def do_POST(self):
        if self.path != "/convert":
            self.send_error(404)
            return

        content_length = int(self.headers.get("Content-Length", 0))
        filename = self.headers.get("X-Filename", "input.docx")
        file_bytes = self.rfile.read(content_length)

        try:
            pdf_bytes = self.convert(file_bytes, filename)
            self.send_response(200)
            self.send_header("Content-Type", "application/pdf")
            self.send_header("Content-Length", str(len(pdf_bytes)))
            self.end_headers()
            self.wfile.write(pdf_bytes)
        except Exception as e:
            self.send_error(500, str(e))

    def convert(self, file_bytes, filename):
        with lo_lock:
            with tempfile.TemporaryDirectory() as tmpdir:
                input_path = os.path.join(tmpdir, filename)
                base_name = os.path.splitext(filename)[0]
                output_path = os.path.join(tmpdir, base_name + ".pdf")

                with open(input_path, "wb") as f:
                    f.write(file_bytes)

                result = subprocess.run(
                    ["libreoffice", "--headless", "--convert-to", "pdf",
                     "--outdir", tmpdir, input_path],
                    capture_output=True, text=True, timeout=60
                )

                if result.returncode != 0:
                    raise RuntimeError(f"LibreOffice error: {result.stderr}")
                if not os.path.exists(output_path):
                    raise RuntimeError("PDF not generated")

                with open(output_path, "rb") as f:
                    return f.read()

    def log_message(self, format, *args):
        pass

if __name__ == "__main__":
    server = http.server.HTTPServer(("0.0.0.0", 3000), ConvertHandler)
    print("LibreOffice converter listening on :3000")
    server.serve_forever()