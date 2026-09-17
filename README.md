# jev-resilience-spring-boot-starter

[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot 3.x](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![JitPack](https://jitpack.io/v/com.github.vicente-md/jev-resilience-spring-boot-starter.svg)](https://jitpack.io/#com.github.vicente-md/jev-resilience-spring-boot-starter)

A Spring Boot starter that brings semantic failure detection to Spring WebFlux services.
Standard circuit breakers only see transport-level failures (5xx, timeouts, connection
errors) and miss *silent failures*: HTTP 200 responses whose body encodes an error, a
stack trace, or a "system under maintenance" notice. This starter evaluates each response
payload with [TypeSafe Jev](https://docs.typesafe.ai/introduction) and converts suspected
silent failures into ordinary exceptions that your existing error handling can process.

## Key Features

- **Reactive AOP.** An AspectJ aspect composes the Jev evaluation into the returned
  `Mono`/`Flux` via `flatMap`. No reactive thread is ever blocked.
- **Fail-open by design.** Any TypeSafe API error, timeout, or malformed response is
  treated as "not a failure" (score `0.0`). A Jev outage cannot trip your circuit breaker
  or interrupt request processing.
- **Zero-overhead execution.** Evaluation is a single non-blocking `WebClient` call with
  a configurable timeout (750 ms default), added to the existing reactive pipeline. No
  extra threads, no blocking.
- **Standard Spring error handling.** Failures surface as `SemanticFailureException`, a
  regular `RuntimeException`: handle it with `onErrorResume`, `@ExceptionHandler`, or
  register it in a Resilience4j `CircuitBreaker`'s `recordExceptions`.

## Installation

The starter is published via [JitPack](https://jitpack.io). Add the JitPack repository
and the dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.vicente-md</groupId>
    <artifactId>jev-resilience-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

The consuming application is expected to provide `spring-boot-starter-webflux`.

## Configuration

```yaml
typesafe:
  jev:
    api-key: ${TYPESAFE_API_KEY}
    base-url: https://api.typesafe.ai   # optional; this is the default
    model: jev-latest                   # optional; this is the default
```

```bash
export TYPESAFE_API_KEY=sk-...   # issued at https://console.typesafe.ai/settings/keys
```

Auto-configuration activates once `typesafe.jev.api-key` is set.

## Usage

Annotate any WebFlux service method that returns `Mono<T>` or `Flux<T>` with
`@SemanticCircuitBreaker`:

```java
@Service
public class PaymentService {

    private final WebClient webClient;

    public PaymentService(WebClient.Builder builder) {
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

When Jev's confidence that a response is a disguised failure exceeds
`confidenceThreshold`, the pipeline emits a `SemanticFailureException` instead of the
original item. Handle it like any other exception:

```java
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
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
```

`SemanticFailureException` also integrates with Resilience4j: add it to a
`CircuitBreaker`'s `recordExceptions` and it counts toward the failure rate like any
transport error.

## Testing

Two bundled tests verify the exact request that `JevEvaluationService` sends.

`JevEvaluationServiceMockWebServerTest` runs offline against a local `MockWebServer` and
asserts the path, `Authorization` header, and JSON body of the `POST /v1/systemone` Noul
request, plus the fail-open behavior on a 5xx response:

```bash
mvn test -Dtest=JevEvaluationServiceMockWebServerTest
```

Expected result: `Tests run: 2, Failures: 0, Errors: 0`.

`JevEvaluationServiceRealApiIT` is an opt-in check against the real TypeSafe API. It runs
only when `TYPESAFE_API_KEY` is set, sends one healthy payload and one disguised
maintenance notice, asserts that the returned scores are low and high respectively, and
prints both `noul` scores:

```bash
export TYPESAFE_API_KEY=sk-...
mvn test -Dtest=JevEvaluationServiceRealApiIT
```

Without the environment variable, this test is skipped and does not run in a normal
build.

The same request shape with curl:

```bash
curl -s -X POST https://api.typesafe.ai/v1/systemone \
  -H "Authorization: Bearer $TYPESAFE_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
        "state": "{\"status\":\"ok\",\"note\":\"system under maintenance, please retry later\"}",
        "model": "jev-latest",
        "questions": {
          "is_silent_failure": {
            "type": "noul",
            "instructions": "Does this payload represent a silent failure, a maintenance window, or an error state disguised as a success?"
          }
        }
      }'
```

A response with `answers.is_silent_failure.noul` close to `1.0` confirms that both the
API key and the request shape are correct.

> **Note:** `JevEvaluationService` sends the configured key as
> `Authorization: Bearer <api-key>`. Configure only the raw key value in
> `typesafe.jev.api-key`; the `Bearer ` prefix is added by the starter.

## How It Works

1. `ReactiveSemanticCircuitBreakerAspect` intercepts `@SemanticCircuitBreaker` methods
   and transforms the returned `Mono<T>`/`Flux<T>` with `flatMap`.
2. Each emitted item is serialized to a string and passed to `JevEvaluationService`,
   which submits a single `Noul` question to `POST /v1/systemone` on the TypeSafe API
   through a non-blocking `WebClient`.
3. If the returned `noul` confidence score exceeds `confidenceThreshold`, the item is
   replaced with `Mono.error(new SemanticFailureException(score, payload))`; otherwise it
   passes through unchanged.
4. If the Jev call fails or times out, the evaluation returns `0.0` (fail-open), so a
   TypeSafe outage never trips the circuit breaker.