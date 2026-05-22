# Security Policy

## Supported versions

Cosmo42 is currently in active development. Security fixes are applied to the `main` branch. Tagged releases are not yet
receiving long-term support.

| Version  | Supported |
|----------|-----------|
| `main`   | ✅         |
| `< main` | ❌         |

## Reporting a vulnerability

Please **do not** report security vulnerabilities through public GitHub issues, pull requests or discussions.

Instead, report them privately via GitHub Security Advisories:

<https://github.com/ExMachinaSAGL/cosmo42/security/advisories/new>

When reporting, please include:

- A description of the issue and its impact.
- Steps to reproduce, or a proof-of-concept.
- Affected versions, commits, or deployment configuration.
- Any suggested mitigation, if known.

You should receive an acknowledgement within a few working days. We will work with you to confirm the issue, prepare a
fix, and coordinate disclosure.

## Scope

In scope:

- The Cosmo42 backend (`backend/`) and frontend (`frontend/`).
- The Docker images and Compose files published from this repository.
- The LibreOffice sidecar in `docker/libreoffice/`.

Out of scope:

- Third-party LLM or embedding servers you connect Cosmo42 to.
- Self-hosted MariaDB instances managed by the operator.
- Generic findings against dependencies without a working exploit against Cosmo42.
