# Changelog

## 0.1.0 (unreleased)

First version.

* Verify a base shader pack (zip or folder) against the fingerprint stored in a patch, apply the patch, write the patched pack atomically.
* Patch file with embedded metadata (names, versions, base and result fingerprints).
* Dev-side generator with self-check.
* Every failure is a typed checked exception; the library never logs or prints.
* Single jar without runtime dependencies (dependencies shaded and relocated), Java 8+.
