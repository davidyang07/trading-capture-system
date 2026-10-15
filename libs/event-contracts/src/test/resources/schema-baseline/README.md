# Published schema baseline

Copies of the event schemas as they are published. `PublishedSchemaBaselineTest` requires every
current schema in `src/main/avro` to stay compatible with its copy here, in both directions
(BACKWARD and FORWARD), and to keep every logical type (decimal precision and scale, dates,
timestamps) unchanged. Avro's own compatibility check ignores logical types, so a decimal scale
change from 4 to 8 would otherwise pass while corrupting every value.

When a compatible change ships, copy the new `.avsc` here in the same change. A schema that adds a
new event must add its baseline too.
