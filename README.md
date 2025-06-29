# ktor_jwt_example

Tutorial in Russian ([habr](https://habr.com/ru/articles/921076/)).
       
## Run tests

```bash
./gradlew :test
```

Here is the `curl` to query the running app.

```bash
curl -v localhost:8080/status \
-H "CUSTOM_AUTH_HEADER: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdG9yX2p3dF9leGFtcGxlIiwiYXVkIjoiSGFiciIsImlhdCI6MTc1MDYxNzA0NiwiZXhwIjo2MTg1NDI2NzYwMCwic3ViIjoidGVzdF90b2tlbiIsIndlYl9pZCI6MSwib3MiOiJJT1MiLCJzY29wZXMiOlsiYWRtaW4iLCJhbmFseXRpY3MiXX0.AAnP9w7IFO9G86HJCBsLyounrRZXWsGYxgEObC2_2-pZjZ9TNn8gr_dIbP3GXTVKUH9QvYAhO2ZfXCcIPt32n26lHeXfTj46Gapri_p4Ewm9vofps3Y3s8CmbkDorh_LFSjIHecWX39ijafAKMQ_opiLZ-f8Ctz0U9GO_0fuW0OWwUnnO55AMTKplZRQc3Iqa3XrkVWN_ZIOU1NrmDuwkqvWgejlE_pPWlsvedPjcbssyVJEykoAs5EA5aA1PLt9ZSICMkFnDKhdGy5YvWpAp4WLfVxU59HJfDJb6XEXxawLTwzT1p75BncOg8N6_YzNwwmLZ9SIN6YL-GZj_hCm3g"
```

## JWT code blocks behavior

```Kotlin
install(Authentication) {
    jwt("ClientA") {
        authHeader { call -> /*...*/ }
        verifier { httpAuthHeader -> /*...*/ }
        validate { credential: JWTCredential -> /*...*/ }
        challenge { defaultScheme, realm -> /*...*/ }
    }
}

install(StatusPages) {
    exception { call, e -> /*...*/ }
}

// Somewhere inside Ktor jwt logic
// internal val JWTLogger: Logger = LoggerFactory.getLogger("io.ktor.auth.jwt")
```

The table below demonstrates what block of code (or log) happens when another block of code throws or returns null.

| Non successful block ↓     | challenge | jwt log¹ | exception |
|----------------------------|-----------|----------|-----------|
| `authHeader` returns null  | +         | –        | –         |
| `authHeader` throws        | –         | –        | +         |
| `verifier` not initialized | +         | –        | –         |
| `verifier` throws          | –         | +        | –         |
| `JWTVerifier` throws       | +         | +        | –         |
| `validate` returns null    | +         | +        | –         |
| `validate` throws          | –         | +        | –         |
| `challenge` throws         | –         | –        | +         |

1. Jwt log could be overridden with logback. Check [the detailed example](https://github.com/D00mch/ktor_jwt_example/blob/main/src/main/resources/logback.xml) in the project.

The project contains several examples how errors could be handles, check [the tests](https://github.com/D00mch/ktor_jwt_example/blob/main/test/kotlin/auth/AuthTest.kt).

## Custom JWT AuthenticationProvider

Check the implementation: [JWTAuth.kt](https://github.com/D00mch/ktor_jwt_example/blob/main/src/main/kotlin/auth/jwt/JWTAuth.kt)

The branching now works as expected:

| Non successful block ↓     | challenge | jwt log¹ | exception |
|----------------------------|-----------|----------|-----------|
| `authHeader` returns null  | +         | +        | –         |
| `authHeader` throws        | +         | +        | +         |
| `verifier` not initialized | +         | +        | –         |
| `verifier` throws          | +         | +        | –         |
| `JWTVerifier` throws       | +         | +        | –         |
| `validate` returns null    | +         | +        | –         |
| `validate` throws          | +         | +        | –         |
| `challenge` throws         | –         | –        | +         |
