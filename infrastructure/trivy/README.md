# Trivy image scanning

Container-image vulnerability + secret scanning for the five TCS images. Config:
[`trivy.yaml`](./trivy.yaml); documented CVE acceptances (currently none): [`.trivyignore`](./.trivyignore).

## Policy

- Fail on **fixable `HIGH` / `CRITICAL`** vulnerabilities (`--ignore-unfixed`): the build must be
  remediated by bumping the base image or dependency, not by suppressing findings.
- `.trivyignore` is for reviewed, time-boxed exceptions only — empty by policy.
- Scanners: `vuln` + `secret`.

## Run locally (executed)

```bash
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$PWD/infrastructure/trivy:/cfg:ro" \
  aquasec/trivy:0.72.0 image --config /cfg/trivy.yaml \
  --severity HIGH,CRITICAL --exit-code 1 --ignore-unfixed \
  tcs/trading-service:validation
```

Verified 2026-07-21 against freshly built validation images for trading-service, account-service,
enrichment-service, allocation-engine, and admin-web: **0 fixable HIGH/CRITICAL findings** and no
image-secret findings, exit code 0 for every image. Findings discovered during validation were
remediated rather than suppressed: Apache MINA is pinned to the compatible 2.2.9 security release,
admin-web runs Next.js 15.5.20, and the frontend runtime image excludes package-manager tooling.
