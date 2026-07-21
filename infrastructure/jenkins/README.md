# Jenkins delivery pipeline

Declarative pipeline at [`Jenkinsfile`](./Jenkinsfile). Configure a Jenkins Pipeline job with
**Pipeline script from SCM**, Script Path `infrastructure/jenkins/Jenkinsfile`, on a
**Docker-capable agent** (the backend integration tests use Testcontainers and the pipeline builds
+ scans images).

## Stages

1. **Checkout** — validates the image prefix/tag, resolves an empty tag to the short git SHA, and
   rejects deploy requests that do not also push the scanned images.
2. **Secret scan** — `gitleaks detect` over the full tree (mandatory; SARIF archived).
3. **Lint & static** — `mvnw validate` + `pnpm --filter admin-web lint typecheck`.
4. **Backend tests + coverage gate** — `mvnw clean verify`: unit + Testcontainers integration +
   the JaCoCo 80% line / 70% branch gate. JUnit + JaCoCo reports published.
5. **Frontend tests + build** — `pnpm --filter admin-web test` + production `build`.
6. **Package** — archives the service jars (fingerprinted).
7. **Build images** — `docker build` for all five images.
8. **Image scan (trivy)** — fails on fixable `HIGH,CRITICAL` in any image
   (`infrastructure/trivy/trivy.yaml`).
9. **Push images** *(opt-in, `PUSH_IMAGES`)* — pushes with credentials from the store.
10. **Deploy (manual gate)** *(opt-in, `DEPLOY`)* — `input` approval, then `kubectl apply` the
    manifests and wait for each rollout.

## Non-negotiables (enforced in the file)

- **Tests are never skipped.** No `-DskipTests`; the backend `verify` and frontend `test` stages
  have no `when` guard that can bypass them.
- **No embedded credentials.** Secrets come only from the Jenkins credentials store
  (`registry-credentials`, `kubeconfig`) via `withCredentials`. Registry/host are parameters.
- **Deploy runs the scanned artifact.** The `Deploy` stage asserts a real `tcs-secrets` already
  exists (never applies the `examples/` placeholder), requires `PUSH_IMAGES`, applies the manifests
  non-recursively, then `kubectl set image` to the exact `RESOLVED_TAG` it built + scanned + pushed.
- **Parameters are validated before shell use.** Image prefixes and tags reject whitespace and
  shell metacharacters; registry login targets the registry host rather than the repository path.
- **Pinned CI tool images.** gitleaks / trivy are pinned to fixed versions (`GITLEAKS_IMAGE`,
  `TRIVY_IMAGE`) for reproducible scans; trivy still refreshes its vuln DB at runtime.
- **Secret scan and tests run on every build**; only Push and Deploy are gated (parameters +
  manual approval).

## Validation

The **declarative linter requires a running Jenkins** (it lives in the pipeline-model-definition
plugin), so it is an activation step, not something validatable offline:

```bash
# CLI (needs Jenkins URL + an API token):
java -jar jenkins-cli.jar -s "$JENKINS_URL" -auth "$USER:$TOKEN" \
  declarative-linter < infrastructure/jenkins/Jenkinsfile

# or HTTP:
curl -s -X POST -H "$CRUMB" -F "jenkinsfile=<infrastructure/jenkins/Jenkinsfile" \
  "$JENKINS_URL/pipeline-model-converter/validate"
```

Expected: `Jenkinsfile successfully validated.` The repo does not ship a Jenkins controller, so this
command is **documented, not executed** in CI-less verification (validated-vs-executed is called out
in the release notes).

Required plugins: Pipeline/Declarative Pipeline, Credentials Binding, JUnit, and Workspace Cleanup
(`cleanWs`). The agent also needs Java 21, Docker, Corepack/Node 22, and `kubectl`; Docker access is
required by Testcontainers, image builds, gitleaks, and Trivy.

## Credentials to create in Jenkins

| ID | Type | Used by |
|---|---|---|
| `registry-credentials` | Username/password | `Push images` (`docker login`) |
| `kubeconfig` | Secret file | `Deploy` (`kubectl`) |
