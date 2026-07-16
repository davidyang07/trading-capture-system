# event-contracts

Owns every Avro event schema (`src/main/avro/*.avsc`) and their generated `SpecificRecord` classes. Populated in **Phase 6** per `docs/contracts/event-catalog.md` and ADR-0004: shared `EventMetadata` record, seven domain events, avro-maven-plugin codegen, serialization + BACKWARD-compatibility contract tests. Generated code is a build artifact — never hand-edited, never committed. Deliberately empty until Phase 6 (no placeholder schemas).
