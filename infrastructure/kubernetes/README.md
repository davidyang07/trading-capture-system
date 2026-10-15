# Kubernetes application manifests

Plain, dependency-free manifests for the five application workloads. Backing stores (MySQL,
Redis, Kafka, Schema Registry, Keycloak) are assumed provided in the `tcs` namespace by an
operator, StatefulSet, or `ExternalName` Service to a managed instance — deploying stateful
infrastructure is out of the app-tier scope; wire their Service DNS names in
`10-configmap-common.yaml`.

## Files (apply in name order)

| File | Contents |
|---|---|
| `00-namespace.yaml` | `tcs` Namespace, `tcs-app` ServiceAccount (token **not** auto-mounted), and NetworkPolicies (default-deny ingress + intra-namespace allow + admin-web-from-ingress). |
| `10-configmap-common.yaml` | Shared non-secret config: OIDC URIs, Redis/Kafka/Schema-Registry endpoints, in-namespace service URLs. |
| `30..33-*.yaml` | Per-service ConfigMap + Deployment + Service for trading / account / enrichment / allocation. |
| `40-admin-web.yaml` | admin-web (Next.js BFF) ConfigMap + Deployment + Service. |
| `50-ingress.yaml` | Ingress exposing **only** admin-web (all API traffic flows through its BFF). |
| `examples/secret.example.yaml` | **Example only** Secret (`tcs-secrets`) with `_dev` placeholders — deliberately kept **out** of the top-level apply set so `kubectl apply -f infrastructure/kubernetes/` (non-recursive) never provisions weak credentials. Copy it out and replace every value before deploying (sealed-secrets / external-secrets / cloud secret manager). |

## Hardening posture

- **Probes:** every workload has `startup` + `readiness` + `liveness` probes. Java services use the
  actuator `health/readiness` and `health/liveness` groups; admin-web uses `/api/auth/session`.
- **Resources:** requests + limits on every container (Java 250m/512Mi → 1cpu/1Gi; admin-web
  100m/256Mi → 500m/512Mi).
- **securityContext:** `runAsNonRoot` with an explicit numeric UID (matching the image),
  `readOnlyRootFilesystem: true` (writable `emptyDir` mounts only for `/tmp` and the Next cache),
  `allowPrivilegeEscalation: false`, `capabilities: drop [ALL]`, `seccompProfile: RuntimeDefault`.
- **Network:** default-deny ingress. Each backend serves actuator (probes, the unauthenticated
  `/actuator/prometheus`) on a separate `management` port (`MANAGEMENT_SERVER_PORT`, API port +
  1000) that its Service does not expose, so it is reachable only from inside the namespace. Only admin-web is exposed
  externally, and only from the ingress-controller namespace.
- **allocation-engine** is pinned to `replicas: 1` with the `Recreate` strategy — its
  transactional-outbox poller must never run concurrently.

## Validation (executed)

Schema-validated with **kubeconform** (strict) via its container image — no cluster required:

```bash
docker run --rm -v "$PWD/infrastructure/kubernetes:/manifests:ro" \
  ghcr.io/yannh/kubeconform:v0.8.0-alpine -strict -summary /manifests
# => 23 resources found in 9 files - Valid: 23, Invalid: 0, Errors: 0, Skipped: 0
```

## Deploy (activation — requires a cluster)

```bash
# 1. Namespace + network policies.
kubectl apply -f 00-namespace.yaml
# 2. Provide REAL secrets named `tcs-secrets` (do NOT apply examples/secret.example.yaml as-is).
#    Copy examples/secret.example.yaml, replace every value, and apply it (or use sealed-secrets /
#    external-secrets). Nothing under examples/ is ever applied by the directory apply in step 4.
kubectl -n tcs apply -f <your-real-secret>.yaml
# 3. Ensure MySQL/Redis/Kafka/Schema-Registry/Keycloak Services exist in the namespace.
# 4. Apply config + workloads (non-recursive → examples/ is skipped).
kubectl -n tcs apply -f infrastructure/kubernetes/
kubectl -n tcs rollout status deploy/admin-web --timeout=180s
```

A local `kind` smoke test is optional (see the root README). The CI pipeline
(`infrastructure/jenkins/Jenkinsfile`) runs the same flow in its gated, manual-approval `Deploy`
stage: it asserts `tcs-secrets` already exists, then applies the manifests (skipping `examples/`)
with every workload image rewritten to the exact tag it built and scanned. The committed `image:`
pins are only defaults for a manual apply; CI never deploys them.

## Rollback and recovery

- **Rolling update failed / bad image:** `kubectl -n tcs rollout undo deploy/<name>` reverts to the
  previous ReplicaSet; verify with `kubectl -n tcs rollout status deploy/<name>` and
  `kubectl -n tcs get pods`. The `maxUnavailable: 0` strategy means a failed rollout never drops
  below the desired healthy count (old pods stay until the new ones pass readiness).
- **allocation-engine (Recreate):** an undo recreates the single pod; no concurrent poller ever
  runs. Outbox rows are DB-authoritative and idempotent, so a restarted poller resumes safely.
- **Config/secret change:** update the ConfigMap/Secret then `kubectl -n tcs rollout restart
  deploy/<name>` to pick it up.
- **Post-rollback verification:** all pods `Ready`, `kubectl -n tcs get events` clean, and the
  admin-web `/api/auth/session` + each service `/actuator/health` return healthy.

## Known limitations

- Backing stateful services (MySQL/Redis/Kafka/Schema-Registry/Keycloak) are referenced, not
  deployed here (documented scope boundary). Confirm each Service's DNS/port at deploy time — the
  common ConfigMap assumes `kafka:9092`, `redis:6379`, `mysql:3306`, `keycloak:8180`.
- **Browser login requires an Ingress-exposed Keycloak.** The in-cluster defaults
  (`OIDC_ISSUER`/`AUTH_KEYCLOAK_ISSUER` = `keycloak:8180`) are correct for service-to-service and
  M2M, but a browser cannot reach `keycloak:8180`. Interactive login needs Keycloak exposed via
  Ingress with `KC_HOSTNAME` set to the frontchannel host, and `OIDC_ISSUER`/`AUTH_KEYCLOAK_ISSUER`
  overridden to it (the frontchannel/backchannel split described in `10-configmap-common.yaml`).
- **admin-web needs HTTPS in production.** The image runs `NODE_ENV=production`, so Auth.js expects
  secure session cookies; before any non-localhost use, set `AUTH_URL`/`NEXT_PUBLIC_APP_URL` to
  `https://…` and add a `tls:` block (+ cert-manager) to `50-ingress.yaml`.
- **`allow-intra-namespace` permits all-pod/all-port traffic within `tcs`** (kept simple for the
  portfolio demo). A compromised admin-web could reach the backends' unauthenticated
  `/actuator/prometheus`. To tighten, scope the scrape allowance to a monitoring podSelector and
  restrict backend ingress to the specific service ports. This is an explicit portfolio-demo
  trade-off, not a production-ready tenant boundary.
- The `prometheus.io/scrape` pod annotations take effect only with an annotation-aware Prometheus
  configured for pod service-discovery in the namespace (not shipped here — scope boundary).
