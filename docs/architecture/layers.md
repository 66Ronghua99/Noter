# Layer Rules

## Canonical Direction

`Types -> Config -> Repo -> Service -> Runtime -> UI`

Providers are the only approved entry point for cross-cutting infrastructure.

## Rules

1. Lower layers must not depend on higher layers.
2. UI must not talk to Repo directly.
3. Service owns business decisions; Repo owns persistence details.
4. External or untyped input must be validated at the boundary.
5. Operationally important third-party SDKs should be wrapped behind providers or adapters.
