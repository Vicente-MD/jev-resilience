# jev-resilience-spring-boot-starter

Non-blocking Spring Boot Starter that implements a **Semantic Circuit Breaker** for
Spring WebFlux services. Standard circuit breakers only see transport-level failures
(5xx, timeouts). This starter also catches *silent failures* — HTTP 200 responses whose
JSON body actually encodes an error, a stack trace, or a "System Under Maintenance"
notice — by having [TypeSafe Jev](https://docs.typesafe.ai/introduction) evaluate the
payload with its `Noul` primitive, off the hot path, without blocking any reactive thread.

## Build & publish the starter

**Option A — JitPack (recommended, no server to run)**

JitPack builds the jar directly from a tagged GitHub release — no manual `mvn deploy`,
no artifact hosting to manage.

1. Push this repo to a public GitHub repository (e.g. `vicentedamasceno/jev-resilience`).
2. On GitHub, go to **Releases → Draft a new release**, create tag `0.1.0`, and publish.
3. Go to [jitpack.io](https://jitpack.io), paste the repo URL, click **Look up**, then
   **Get it** next to the `0.1.0` release. Wait for the build log to turn green.

> **Note:** JitPack derives your `groupId` from your GitHub username/org, so this
> starter's `pom.xml` uses `com.github.vicentedamasceno` — replace it with
> `com.github.<your-github-username>` if you fork/publish it under a different account.

**Option B — Local install / private repo**

```bash
cd jev-resilience-spring-boot-starter
mvn clean install          # runs tests, installs the jar into ~/.m2
# or: mvn deploy            # to a shared Nexus/Artifactory/GitHub Packages repo
```

## Install (in the consuming project)

**If published via JitPack:**

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.vicentedamasceno</groupId>
    <artifactId>jev-resilience-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

**If installed locally / from a private repo:**

```xml
<dependency>
    <groupId>com.github.vicentedamasceno</groupId>
    <artifactId>jev-resilience-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

The consuming project also needs `spring-boot-starter-webflux` (usually already present).

## Configure

```yaml
typesafe:
  jev:
    api-key: ${TYPESAFE_API_KEY}
    base-url: https://api.typesafe.ai
    model: jev-latest
```

```bash
export TYPESAFE_API_KEY=sk-...   # from https://console.typesafe.ai/settings/keys
```

Auto-configuration activates automatically once `typesafe.jev.api-key` is set — nothing
else to wire up.

## Use

Annotate any WebFlux controller/service method that returns `Mono<T>` or `Flux<T>`:

```java
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final WebClient paymentProviderClient;
    private final PaymentService paymentService;

    public PaymentController(WebClient.Builder builder, PaymentService paymentService) {
        this.paymentProviderClient = builder.baseUrl("https://provider.example.com").build();
        this.paymentService = paymentService;
    }

    @GetMapping("/{id}/status")
    public Mono<ResponseEntity<?>> getPaymentStatus(@PathVariable String id) {
        return paymentService.fetchStatus(id)
                .map(ResponseEntity::ok)
                .onErrorResume(SemanticFailureException.class, ex ->
                        Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body("Provider returned a disguised failure (confidence=" + ex.getConfidenceScore() + ")")));
    }
}

@Service
class PaymentService {

    private final WebClient webClient;

    PaymentService(WebClient.Builder builder) {
        this.webClient = builder.baseUrl("https://provider.example.com").build();
    }

    @SemanticCircuitBreaker(confidenceThreshold = 0.85)
    public Mono<PaymentStatusResponse> fetchStatus(String paymentId) {
        return webClient.get()
                .uri("/payments/{id}/status", paymentId)
                .retrieve()
                .bodyToMono(PaymentStatusResponse.class);
    }
}
```

If Jev's `noul` confidence that the response is a disguised failure exceeds the
threshold, the `Mono` completes with a `SemanticFailureException` instead of the
original item. Handle it with `onErrorResume` (as above), or add it to an existing
Resilience4j `CircuitBreaker`'s `recordExceptions` so it counts toward the breaker's
failure rate like any other exception.

## How it works

1. `ReactiveSemanticCircuitBreakerAspect` wraps the `Mono<T>`/`Flux<T>` returned by an
   `@SemanticCircuitBreaker`-annotated method with `flatMap`.
2. Each emitted item is stringified and sent to `JevEvaluationService`, which posts a
   single `Noul` question to `POST https://api.typesafe.ai/v1/systemone` via a
   non-blocking `WebClient`.
3. If the returned `noul` score exceeds `confidenceThreshold`, the pipeline emits
   `Mono.error(new SemanticFailureException(...))`; otherwise the original item passes
   through unchanged.
4. On any Jev transport error/timeout, the client **fails open** (returns `0.0`) so a
   TypeSafe outage never trips your circuit breaker.
